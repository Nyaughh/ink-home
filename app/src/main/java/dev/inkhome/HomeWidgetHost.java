package dev.inkhome;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Owns Android widget allocations. Home stores the ID only after binding and configuration. */
final class HomeWidgetHost {
    private static final int HOST = 0x4948;
    private static final int BIND = 0x4901;
    private static final int CONFIGURE = 0x4902;
    private static final String PENDING = "pendingHomeWidget";
    private static final String REDRAW = "homeWidgetRedrawMs";
    private static final int[] INTERVALS = {0, 1000, 10000, 60000};

    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final AppWidgetManager manager;
    private final AppWidgetHost host;
    private final List<WidgetView> attached = new ArrayList<>();
    private IntConsumer accepted;
    private boolean listening;
    private int pending;
    private int interval;

    HomeWidgetHost(MainActivity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
        manager = AppWidgetManager.getInstance(activity);
        interval = prefs.getInt(REDRAW, 60000);
        host = new AppWidgetHost(activity, HOST) {
            @Override public AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
                return new WidgetView(context);
            }
        };
        pending = prefs.getInt(PENDING, 0);
        if (pending != 0) discard();
    }

    void start() {
        if (listening) return;
        try {
            host.startListening();
            listening = true;
        } catch (RuntimeException e) {
            Log.w("HomeWidgetHost", "Widget service unavailable", e);
        }
    }

    void stop() {
        if (listening) {
            host.stopListening();
            listening = false;
        }
    }

    void pick(IntConsumer onAccepted) {
        if (pending != 0) return;
        List<AppWidgetProviderInfo> providers = new ArrayList<>();
        for (AppWidgetProviderInfo info : manager.getInstalledProviders()) {
            if ((info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0)
                providers.add(info);
        }
        if (providers.isEmpty()) {
            activity.toast(activity.getString(R.string.home_widget_empty));
            return;
        }
        Collator collator = Collator.getInstance();
        providers.sort((a, b) -> collator.compare(label(a), label(b)));
        String[] labels = new String[providers.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = label(providers.get(i));
        activity.choose(activity.getString(R.string.home_widget_add), labels, -1, i -> bind(providers.get(i), onAccepted));
    }

    private String label(AppWidgetProviderInfo info) {
        String label = info.loadLabel(activity.getPackageManager());
        return label != null ? label : info.provider.getShortClassName();
    }

    private void bind(AppWidgetProviderInfo info, IntConsumer callback) {
        if (pending != 0) return;
        accepted = callback;
        try {
            pending = host.allocateAppWidgetId();
            prefs.edit().putInt(PENDING, pending).apply();
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            if (manager.bindAppWidgetIdIfAllowed(pending, info.getProfile(), info.provider, options)) {
                configure();
            } else {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.getProfile())
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                activity.startActivityForResult(bind, BIND);
            }
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    boolean result(int request, int result, Intent data) {
        if (request != BIND && request != CONFIGURE) return false;
        if (pending == 0) return true;
        if (result != Activity.RESULT_OK ||
                (data != null && data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending) != pending)) {
            discard();
            return true;
        }
        try {
            if (request == BIND) configure();
            else finish();
        } catch (RuntimeException e) {
            failed(e);
        }
        return true;
    }

    private void configure() {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(pending);
        if (info == null) throw new IllegalStateException("Widget provider unavailable");
        if (info.configure == null) finish();
        else host.startAppWidgetConfigureActivityForResult(activity, pending, 0, CONFIGURE, null);
    }

    private void finish() {
        accepted.accept(pending);
        clearPending();
    }

    private void discard() {
        if (pending != 0) remove(pending);
        clearPending();
    }

    private void clearPending() {
        accepted = null;
        pending = 0;
        prefs.edit().remove(PENDING).apply();
    }

    private void failed(RuntimeException e) {
        Log.w("HomeWidgetHost", "Could not add widget", e);
        discard();
        activity.toast(activity.getString(R.string.home_widget_add_failed));
    }

    View view(int id) {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info != null) return host.createView(activity, id, info);
        TextView missing = activity.ink.body(activity.getString(R.string.home_widget_unavailable));
        missing.setGravity(Gravity.CENTER);
        return missing;
    }

    void remove(int id) {
        host.deleteAppWidgetId(id);
    }

    void settings() {
        String[] labels = activity.getResources().getStringArray(R.array.home_widget_intervals);
        int checked = -1;
        for (int i = 0; i < INTERVALS.length; i++) if (interval == INTERVALS[i]) checked = i;
        activity.choose(activity.getString(R.string.home_widget_redraw_limit), labels, checked, i -> {
            interval = INTERVALS[i];
            prefs.edit().putInt(REDRAW, interval).apply();
            for (WidgetView widget : attached) widget.reschedule();
        });
    }

    private final class WidgetView extends AppWidgetHostView {
        private long lastRefresh;
        private RemoteViews latest;
        private boolean providerChanged;
        private boolean layoutChanged;
        private boolean redrawNeeded;
        private boolean active;
        private boolean scheduled;
        private boolean applying;
        private final Runnable refresh = this::flush;

        WidgetView(Context context) { super(context); }

        @Override public void updateAppWidget(RemoteViews update) {
            // A null update is meaningful: it asks Android to show the default provider layout.
            latest = update;
            providerChanged = true;
            schedule();
        }

        private void schedule() {
            if (!active || scheduled || applying) return;
            scheduled = true;
            long delay = lastRefresh == 0 ? 0 : lastRefresh + interval - SystemClock.uptimeMillis();
            postDelayed(refresh, Math.max(0, delay));
        }

        private void reschedule() {
            if (!scheduled) return;
            removeCallbacks(refresh);
            scheduled = false;
            schedule();
        }

        private void flush() {
            scheduled = false;
            if (!active) return;
            lastRefresh = SystemClock.uptimeMillis();
            applying = true;
            try {
                if (providerChanged) {
                    RemoteViews update = latest;
                    latest = null;
                    providerChanged = false;
                    super.updateAppWidget(update);
                }
                if (layoutChanged) super.requestLayout();
                invalidate();
            } finally {
                applying = false;
                layoutChanged = false;
                redrawNeeded = false;
            }
            if (providerChanged) schedule();
        }

        // Live clocks can change their text and request a layout between provider updates.
        @Override public void requestLayout() {
            if (!active || applying || interval == 0 || !isLaidOut()) {
                super.requestLayout();
                return;
            }
            layoutChanged = true;
            schedule();
        }

        private boolean deferChildDraw() {
            if (!active) return true;
            if (applying || interval == 0 || !isLaidOut()) return false;
            redrawNeeded = true;
            schedule();
            return true;
        }

        @Override public void onDescendantInvalidated(View child, View target) {
            if (!deferChildDraw()) super.onDescendantInvalidated(child, target);
        }

        @Override public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
            return deferChildDraw() ? null : super.invalidateChildInParent(location, dirty);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            active = true;
            attached.add(this);
            if (providerChanged || redrawNeeded || layoutChanged) schedule();
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            if (w <= 0 || h <= 0) return;
            float density = getResources().getDisplayMetrics().density;
            int width = Math.round(w / density);
            int height = Math.round(h / density);
            updateAppWidgetSize(null, width, height, width, height);
        }

        @Override protected void onDetachedFromWindow() {
            active = false;
            attached.remove(this);
            removeCallbacks(refresh);
            scheduled = false;
            lastRefresh = 0;
            super.onDetachedFromWindow();
        }
    }
}

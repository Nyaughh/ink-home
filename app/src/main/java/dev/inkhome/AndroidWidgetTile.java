package dev.inkhome;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class AndroidWidgetTile extends HomeComponent {
    static int widgetId(HomeLayout.Item item) {
        if (!"android".equals(item.type)) return -1;
        try { return Integer.parseInt(item.settings.get("widgetId")); }
        catch (NumberFormatException e) { return -1; }
    }

    @Override void editorStarted(MainActivity host) {
        allocations(host).recover();
    }
    @Override void added(MainActivity host, HomeLayout.Item item) {
        allocations(host).added(item);
    }
    @Override void beforeSave(MainActivity host, HomeLayout original) {
        allocations(host).beforeSave(original);
    }
    @Override void reset(MainActivity host, HomeLayout original, HomeLayout preset) {
        preset.hiddenWidgetIds.addAll(original.hiddenWidgetIds);
    }
    @Override void prune(MainActivity host, HomeLayout original, HomeLayout draft) {
        allocations(host).prune(original, draft);
    }
    @Override void finish(MainActivity host, HomeLayout keep) {
        allocations(host).finish(keep);
    }

    private AndroidWidgetAllocations allocations(MainActivity host) {
        return host.homeWidgets.allocations;
    }

    @Override public String id() { return "android"; }
    @Override public String name(MainActivity host) { return host.getString(R.string.home_widget_android); }
    @Override public String[] variants() { return new String[]{"default"}; }
    @Override public boolean supports(int w, int h) {
        return w >= 1 && h >= 1;
    }
    @Override public Map<String, String[]> options() {
        Map<String,String[]> choices = new LinkedHashMap<>();
        choices.put("borders", new String[]{"default", "false", "true"});
        choices.put("redraw", new String[]{"default", "as requested", "1 second", "10 seconds", "1 minute"});
        return choices;
    }
    @Override public String optionName(MainActivity host, String key) {
        if ("redraw".equals(key)) return host.getString(R.string.home_widget_redraw_limit);
        return "borders".equals(key) ? host.getString(R.string.home_settings_borders) : key;
    }
    @Override public List<HomeLayout.Item> restorable(MainActivity host, HomeLayout layout) {
        List<HomeLayout.Item> choices = new ArrayList<>();
        for (int id : layout.hiddenWidgetIds) {
            HomeLayout.Item item = new HomeLayout.Item(id(), variants()[0], 0, 0, 2, 4);
            item.settings.put("widgetId", Integer.toString(id));
            choices.add(item);
        }
        return choices;
    }
    @Override public String restoredName(MainActivity host, HomeLayout.Item item) {
        return host.homeWidgets.labelForId(widgetId(item));
    }
    @Override public void restored(HomeLayout layout, HomeLayout.Item item) {
        layout.hiddenWidgetIds.remove(Integer.valueOf(widgetId(item)));
    }
    @Override public void configureNew(MainActivity host, HomeLayout.Item item, Runnable ready) {
        host.homeWidgets.pick(id -> {
            item.settings.put("widgetId", Integer.toString(id));
            ready.run();
        });
    }
    @Override public void release(MainActivity host, HomeLayout.Item item) {
        int id = widgetId(item);
        if (id > 0) host.homeWidgets.remove(id);
    }
    private View preview(MainActivity host, int id) {
        AppWidgetManager manager = AppWidgetManager.getInstance(host);
        AppWidgetProviderInfo bound = id > 0 ? manager.getAppWidgetInfo(id) : null;
        List<AppWidgetProviderInfo> choices = bound == null ? manager.getInstalledProviders()
                : java.util.Collections.singletonList(bound);
        for (AppWidgetProviderInfo info : choices) {
            if ((info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue;
            Drawable drawable = info.loadPreviewImage(host, host.getResources().getDisplayMetrics().densityDpi);
            if (drawable == null) continue;
            ImageView example = new ImageView(host);
            example.setScaleType(ImageView.ScaleType.FIT_CENTER);
            example.setImageDrawable(drawable);
            return example;
        }
        TextView caption = host.ink.body(id > 0 ? host.homeWidgets.labelForId(id) : name(host));
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(2);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return caption;
    }

    @Override boolean rebuildOnResize() { return false; }

    @Override public View renderContent(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        int id = widgetId(item);
        return preview || id < 1 ? preview(host, id) : host.homeWidgets.view(id, item.settings.get("redraw"));
    }
}

package dev.inkhome.folio.epd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import dev.inkhome.folio.DisplayProbe;
import dev.inkhome.folio.core.RefreshBudget;
import java.util.function.IntSupplier;

/** Coalesces full cleans until the current burst of updates has finished. */
public final class RefreshController {
    private final Handler queue = new Handler(Looper.getMainLooper());
    private final ViewGroup decor;
    private final Epd epd;
    private final IntSupplier threshold;
    private final RefreshBudget budget = new RefreshBudget();
    private boolean pending;
    private View fastView;

    private boolean resumed;
    private boolean woke;
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            woke = true;
            if (resumed) resume();
        }
    };

    public RefreshController(ViewGroup decor, IntSupplier threshold) {
        this.decor = decor;
        this.threshold = threshold;
        epd = Epd.forView(decor);
        if (epd instanceof OnyxEpd) {
            decor.getContext().registerReceiver(wake, new IntentFilter(Intent.ACTION_SCREEN_ON));
        }
    }

    public void changed(View view, RefreshBudget.Change change) {
        if (!(epd instanceof OnyxEpd)) return;
        Epd.Intent intent = change == RefreshBudget.Change.IMAGE ? Epd.Intent.IMAGE : Epd.Intent.PAGE;
        epd.update(view, intent);
        if (budget.charge(change, DisplayProbe.night(view.getContext()), threshold.getAsInt()) || pending) {
            cleanWhenIdle();
        }
    }

    public void cleanWhenIdle() {
        pending = true;
        queue.removeCallbacks(idleClean);
        if (resumed && fastView == null) queue.postDelayed(idleClean, 60);
    }

    private final Runnable idleClean = () -> clean(false);

    public void clean(boolean manual) {
        queue.removeCallbacks(idleClean);
        pending = false;
        budget.reset();
        if (decor.isShown()) epd.clean(decor, manual);
    }

    public void fast(View view) {
        if (!(epd instanceof OnyxEpd)) return;
        queue.removeCallbacks(settle);
        if (fastView != null && fastView != view) settle.run();
        fastView = view;
        epd.update(view, Epd.Intent.FAST);
        queue.removeCallbacks(idleClean);
        queue.postDelayed(settle, 250);
    }

    private final Runnable settle = () -> {
        View view = fastView;
        fastView = null;
        if (view != null && view.isAttachedToWindow()) {
            changed(view, RefreshBudget.Change.PAGE);
            view.invalidate();
        } else if (pending) {
            cleanWhenIdle();
        }
    };

    public void resume() {
        resumed = true;
        if (woke || pending) cleanWhenIdle();
        woke = false;
    }

    public void close() {
        pause();
        if (epd instanceof OnyxEpd) decor.getContext().unregisterReceiver(wake);
    }

    public void pause() {
        resumed = false;
        queue.removeCallbacks(settle);
        if (fastView != null) epd.update(fastView, Epd.Intent.PAGE);
        fastView = null;
        queue.removeCallbacks(idleClean);
        // If a clean was pending, the next visible update will schedule it again.
    }
}

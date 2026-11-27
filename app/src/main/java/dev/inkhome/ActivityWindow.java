package dev.inkhome;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

/** Applies Ink Home's orientation preference and keeps controls within the usable window. */
final class ActivityWindow {
    private ActivityWindow() {}

    static void applyOrientation(Activity activity, Store store) {
        int orientation = store.portraitLocked()
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_USER;
        if (activity.getRequestedOrientation() != orientation) activity.setRequestedOrientation(orientation);
    }

    static void fitContent(Activity activity, View root) {
        activity.getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                    | WindowInsets.Type.systemGestures() | WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsets.CONSUMED;
        });
    }
}

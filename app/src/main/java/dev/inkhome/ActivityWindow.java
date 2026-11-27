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
                    | WindowInsets.Type.ime());
            Insets gestures = insets.getInsets(WindowInsets.Type.systemGestures());
            // The transient notification-shade swipe reserves the top edge even when hidden.
            // Let the status strip occupy that space while keeping side and bottom controls clear.
            view.setPadding(Math.max(safe.left, gestures.left), safe.top,
                    Math.max(safe.right, gestures.right), Math.max(safe.bottom, gestures.bottom));
            return WindowInsets.CONSUMED;
        });
    }
}

package dev.inkhome.lab;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import dev.inkhome.R;

public final class ShellProbeService extends AccessibilityService {
    private static final String TAG = "InkShellProbe";
    private TextView strip;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (strip != null) return;

        TextView label = new TextView(this);
        label.setText(R.string.shell_probe_label);
        label.setTextColor(Color.WHITE);
        label.setBackgroundColor(Color.BLACK);
        label.setTextSize(14);
        int pad = Math.round(4 * getResources().getDisplayMetrics().density);
        label.setPadding(2 * pad, pad, 2 * pad, pad);
        label.setOnClickListener(v -> {
            try {
                View.class.getMethod("refreshScreen", int.class).invoke(v, 98);
                Log.i(TAG, "refreshScreen(98) returned");
            } catch (ReflectiveOperationException | SecurityException e) {
                Log.w(TAG, "refreshScreen(98) failed", e);
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.setFitInsetsTypes(0);
        try {
            getSystemService(WindowManager.class).addView(label, params);
            strip = label;
        } catch (WindowManager.BadTokenException | SecurityException e) {
            Log.w(TAG, "Overlay attachment failed", e);
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN) {
            Log.i(TAG, KeyEvent.keyCodeToString(code) + " " + event.getAction());
        }
        return false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    private void removeStrip() {
        if (strip != null) {
            getSystemService(WindowManager.class).removeViewImmediate(strip);
            strip = null;
        }
    }

    @Override public boolean onUnbind(Intent intent) {
        removeStrip();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        removeStrip();
        super.onDestroy();
    }
}
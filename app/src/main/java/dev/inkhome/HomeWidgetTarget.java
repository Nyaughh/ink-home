package dev.inkhome;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

/** A stationary hold offers layout editing while preserving child taps, scrolling and item menus. */
final class HomeWidgetTarget extends FrameLayout {
    private final MainActivity host;
    private final HomeLayout.Item item;
    private final HomeComponent component;
    private final int slop;
    private float downX, downY, rawX, rawY;
    private long downTime;
    private boolean held;
    private final Runnable hold = this::held;

    HomeWidgetTarget(MainActivity host, HomeLayout.Item item, HomeComponent component) {
        super(host);
        this.host = host;
        this.item = item;
        this.component = component;
        slop = ViewConfiguration.get(host).getScaledTouchSlop();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                held = false;
                downX = event.getX();
                downY = event.getY();
                rawX = event.getRawX();
                rawY = event.getRawY();
                downTime = event.getDownTime();
                postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) {
                    removeCallbacks(hold);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                break;
            default:
                break;
        }
        if (!held) super.dispatchTouchEvent(event);
        return true;
    }

    private void held() {
        if (!isAttachedToWindow() || !hasWindowFocus()) return;
        held = true;
        MotionEvent cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, downX, downY, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        View target = findLongClick(getChildAt(0), (int) rawX, (int) rawY, new Rect());
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        String edit = host.getString(R.string.home_edit_layout);
        String[] choices = target == null ? new String[]{edit}
                : new String[]{edit, host.getString(R.string.home_item_actions)};
        host.choose(component.name(host), choices, -1, choice -> {
            if (choice == 0) host.editHome(item.key);
            else target.performLongClick();
        });
    }

    private static View findLongClick(View view, int x, int y, Rect visible) {
        if (!view.getGlobalVisibleRect(visible) || !visible.contains(x, y)) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View nested = findLongClick(group.getChildAt(i), x, y, visible);
                if (nested != null) return nested;
            }
        }
        return view.isLongClickable() && view.isEnabled() ? view : null;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_LONG_CLICK, host.getString(R.string.home_edit_layout)));
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
            host.editHome(item.key);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(hold);
        super.onDetachedFromWindow();
    }
}

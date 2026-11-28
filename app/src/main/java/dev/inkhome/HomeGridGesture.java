package dev.inkhome;

import android.view.MotionEvent;
import android.view.View;

/** Resolve a snapped proposal against the untouched draft, applying it only on release. */
final class HomeGridGesture implements View.OnTouchListener {
    private final HomeGrid grid;
    private final HomeLayout layout;
    private final HomeLayout.Item item;
    private final boolean resizing;
    private final int slop;
    private float downX, downY;
    private boolean tracking, dragging;
    private int lastColumn, lastRow;
    private HomeLayout.Placement placement;

    HomeGridGesture(HomeGrid grid, HomeLayout layout, HomeLayout.Item item, boolean resizing, int slop) {
        this.grid = grid;
        this.layout = layout;
        this.item = item;
        this.resizing = resizing;
        this.slop = slop;
    }

    @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                tracking = true;
                lastColumn = -1;
                lastRow = -1;
                view.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                finish(view);
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                if (!tracking) return true;
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (!dragging && dx * dx + dy * dy >= slop * slop) {
                    dragging = true;
                    grid.dragging(true);
                }
                if (dragging) update(dx, dy);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    boolean tapped = !dragging;
                    HomeLayout.Placement accepted = placement;
                    finish(view);
                    if (tapped) view.performClick();
                    else if (accepted != null) {
                        accepted.apply();
                        if (resizing) item.settings.remove("heightMode");
                        grid.dropped(item);
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private void finish(View view) {
        if (!tracking) return;
        if (dragging) grid.dragging(false);
        placement = null;
        dragging = false;
        tracking = false;
        view.getParent().requestDisallowInterceptTouchEvent(false);
    }

    private void update(float dx, float dy) {
        int origin = resizing ? item.y + item.h : item.y;
        float edge = grid.rowSpan(0, origin) + dy;
        int minRow = resizing ? item.y + 1 : 0;
        int maxRow = resizing ? layout.rows : layout.rows - item.h;
        int row = minRow;
        for (int r = minRow + 1; r <= maxRow; r++) {
            if (Math.abs(grid.rowSpan(0, r) - edge) < Math.abs(grid.rowSpan(0, row) - edge)) row = r;
        }
        int offset = Math.round(dx * layout.cols / Math.max(1, grid.getWidth()));
        int column = resizing ? Math.max(1, Math.min(layout.cols - item.x, item.w + offset))
                : Math.max(0, Math.min(layout.cols - item.w, item.x + offset));
        if (row == lastRow && column == lastColumn) return;
        lastRow = row;
        lastColumn = column;
        int x = column, y = row, w = item.w, h = item.h;
        if (resizing) {
            x = item.x;
            y = item.y;
            int best = Integer.MAX_VALUE;
            HomeComponent component = HomeComponents.get(item.type);
            for (int cw = 1; cw <= layout.cols - x; cw++) {
                for (int ch = 1; ch <= layout.rows - y; ch++) {
                    int dw = cw - column, dh = ch - (row - y);
                    int distance = dw * dw + dh * dh;
                    if (distance < best && component.supports(cw, ch)) {
                        w = cw;
                        h = ch;
                        best = distance;
                    }
                }
            }
        }
        boolean unchanged = x == item.x && y == item.y && w == item.w && h == item.h;
        placement = unchanged ? null : resizing ? layout.resize(item, w, h) : layout.moveTo(item, x, y);
        if (unchanged || placement != null) grid.showTarget(x, y, w, h);
        else grid.clearTarget();
    }
}

package dev.inkhome;

import android.view.MotionEvent;
import android.view.View;

/** Resolve a snapped proposal against the untouched draft, applying it only on release. */
final class HomeGridGesture implements View.OnTouchListener {
    private final HomeGrid grid;
    private final HomeLayout layout;
    private final HomeLayout.Item item;
    enum Handle {
        MOVE(false, false), TOP_LEFT(true, true), TOP_RIGHT(false, true),
        BOTTOM_LEFT(true, false), BOTTOM_RIGHT(false, false);

        final boolean left, top;

        Handle(boolean left, boolean top) {
            this.left = left;
            this.top = top;
        }
    }

    private final Handle handle;
    private final int slop;
    private float downX, downY;
    private boolean tracking, dragging;
    private int lastColumn, lastRow;
    private HomeLayout.Placement placement;

    HomeGridGesture(HomeGrid grid, HomeLayout layout, HomeLayout.Item item, Handle handle, int slop) {
        this.grid = grid;
        this.layout = layout;
        this.item = item;
        this.handle = handle;
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
                    else {
                        if (accepted != null) {
                            accepted.apply();
                            if (handle != Handle.MOVE) item.settings.remove("heightMode");
                        }
                        grid.dropped();
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
        boolean resizing = handle != Handle.MOVE;
        int right = item.x + item.w, bottom = item.y + item.h;
        int origin = resizing && !handle.top ? bottom : item.y;
        float edge = grid.rowSpan(0, origin) + dy;
        int minRow = resizing && !handle.top ? item.y + 1 : 0;
        int maxRow = !resizing ? layout.rows - item.h : handle.top ? bottom - 1 : layout.rows;
        int row = minRow;
        for (int r = minRow + 1; r <= maxRow; r++) {
            if (Math.abs(grid.rowSpan(0, r) - edge) < Math.abs(grid.rowSpan(0, row) - edge)) row = r;
        }
        int offset = Math.round(dx * layout.cols / Math.max(1, grid.getWidth()));
        int column = resizing ? Math.max(1, Math.min(handle.left ? right : layout.cols - item.x,
                        item.w + (handle.left ? -offset : offset)))
                : Math.max(0, Math.min(layout.cols - item.w, item.x + offset));
        if (row == lastRow && column == lastColumn) return;
        lastRow = row;
        lastColumn = column;
        int x = column, y = row, w = item.w, h = item.h;
        if (resizing) {
            int requestedHeight = handle.top ? bottom - row : row - item.y;
            int maxWidth = handle.left ? right : layout.cols - item.x;
            int maxHeight = handle.top ? bottom : layout.rows - item.y;
            int best = Integer.MAX_VALUE;
            HomeComponent component = HomeComponents.get(item.type);
            for (int cw = 1; cw <= maxWidth; cw++) {
                for (int ch = 1; ch <= maxHeight; ch++) {
                    int dw = cw - column, dh = ch - requestedHeight;
                    int distance = dw * dw + dh * dh;
                    if (distance < best && component.supports(cw, ch)) {
                        w = cw;
                        h = ch;
                        best = distance;
                    }
                }
            }
            x = handle.left ? right - w : item.x;
            y = handle.top ? bottom - h : item.y;
        }
        boolean unchanged = x == item.x && y == item.y && w == item.w && h == item.h;
        placement = unchanged ? null : resizing ? layout.resizeAt(item, x, y, w, h) : layout.moveTo(item, x, y);
        if (unchanged || placement != null) grid.showTarget(x, y, w, h);
        else grid.showTarget(item.x, item.y, item.w, item.h);
    }
}

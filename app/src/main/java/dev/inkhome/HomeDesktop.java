package dev.inkhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

final class HomeDesktop {
    private static final int COLS = HomeLayout.COLS, ROWS = HomeLayout.ROWS;
    private static final String[] KINDS = {"current", "next", "apps", "clock", "calendar",
            "goal", "month", "battery", "moon", "widget"};
    private final MainActivity a;
    private HomeLayout layout;
    private int page;
    private boolean editing;
    private HomeLayout.Tile moving;

    HomeDesktop(MainActivity a) { this.a = a; }

    boolean active() { return editing || HomeLayout.exists(a); }

    void edit() {
        editing = true;
        moving = null;
        a.render();
    }

    boolean back() {
        if (!editing) return false;
        if (moving != null) moving = null;
        else editing = false;
        a.render();
        return true;
    }

    boolean page(int delta) {
        if (layout == null) return false;
        long next = (long) page + delta;
        if (next < 0 || next >= layout.pages || next == page) return false;
        page = (int) next;
        a.render();
        return true;
    }

    View build() {
        if (layout == null) layout = HomeLayout.load(a);
        page = Math.min(page, layout.pages - 1);
        LinearLayout root = a.ink.col();
        int pad = a.ink.dp(8);
        root.setPadding(pad, 0, pad, 0);
        LinearLayout bar = a.ink.row();
        if (layout.pages > 1) icon(bar, "back", a.getString(R.string.desktop_previous), () -> page(-1), page > 0);
        String title = moving != null ? a.getString(R.string.desktop_place_tile) : editing ? a.getString(R.string.desktop_edit) : a.getString(R.string.launcher_home);
        TextView heading = a.ink.oneLine(a.ink.medium(layout.pages > 1
                ? a.getString(R.string.desktop_page_title, title, page + 1, layout.pages) : title, 17));
        bar.addView(heading, a.ink.weight(1));
        if (layout.pages > 1) icon(bar, "forward", a.getString(R.string.desktop_next), () -> page(1), page + 1 < layout.pages);
        if (editing) {
            if (moving == null) {
                icon(bar, "plus", a.getString(R.string.desktop_add), () -> addMenu(-1, -1), true);
                icon(bar, "more", a.getString(R.string.desktop_page_options), this::options, true);
                icon(bar, "check", a.getString(R.string.desktop_done), () -> { editing = false; save(); }, true);
            } else icon(bar, "close", a.getString(R.string.desktop_cancel_move), this::back, true);
        } else icon(bar, "tune", a.getString(R.string.desktop_edit), this::edit, true);
        root.addView(bar);
        root.addView(a.ink.hair());
        Board board = new Board();
        root.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
        board.setClickable(true);
        if (moving != null) {
            for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
                if (layout.fits(moving, page, x, y, moving.w, moving.h)) {
                    final int tx = x, ty = y;
                    board.cell(x, y, 1, 1, target(false, x, y, () -> {
                        moving.x = tx;
                        moving.y = ty;
                        moving.page = page;
                        moving = null;
                        save();
                    }));
                }
            }
        } else if (editing) {
            for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
                if (layout.fits(null, page, x, y, 1, 1)) {
                    final int tx = x, ty = y;
                    board.cell(x, y, 1, 1, target(true, x, y, () -> addMenu(tx, ty)));
                }
            }
        }
        for (HomeLayout.Tile t : layout.tiles) {
            if (t.page != page || (moving != null && t == moving)) continue;
            // During placement only show the other occupied cells.
            View tile;
            if (editing) {
                TextView label = a.ink.body(a.getString(R.string.desktop_tile_summary, name(t.kind), t.w, t.h));
                label.setGravity(Gravity.CENTER);
                label.setBackground(a.ink.card(0, true));
                if (moving == null) label.setOnClickListener(v -> tileOptions(t));
                else {
                    label.setText(a.getString(R.string.desktop_swap, name(t.kind), t.w, t.h));
                    label.setOnClickListener(v -> {
                        HomeMosaic.swap(moving, t);
                        moving = null;
                        save();
                    });
                }
                tile = label;
            } else {
                tile = "widget".equals(t.kind) ? a.homeWidgets.view(t.widgetId)
                        : builtIn(t);
                if (tile == null) tile = a.ink.body(a.getString(R.string.desktop_missing_widget));
                if (tile.getParent() instanceof ViewGroup) ((ViewGroup) tile.getParent()).removeView(tile);
            }
            board.cell(t.x, t.y, t.w, t.h, tile);
            ((Cell) tile.getLayoutParams()).tile = t;
        }
        a.pageNext = () -> page(1);
        a.pagePrev = () -> page(-1);
        return root;
    }

    private void icon(LinearLayout bar, String glyph, String label, Runnable action, boolean enabled) {
        View button = a.ink.iconButton(glyph, action);
        button.setContentDescription(label);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
        bar.addView(button);
    }

    private View target(boolean adding, int x, int y, Runnable action) {
        String label = adding ? a.getString(R.string.desktop_add_symbol) : a.getString(R.string.desktop_place_here);
        TextView button = a.ink.muted(label, 14);
        button.setGravity(Gravity.CENTER);
        button.setBackground(a.ink.pressable());
        button.setContentDescription(a.getString(R.string.desktop_cell, adding ? a.getString(R.string.desktop_add) : label, x + 1, y + 1));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private String name(String kind) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return a.getResources().getStringArray(R.array.desktop_tile_names)[i];
        return a.getString(R.string.desktop_missing_tile);
    }

    private View builtIn(HomeLayout.Tile t) {
        for (String kind : KINDS) {
            if (kind.equals(t.kind)) return HomeTiles.build(a, kind, t.w, t.h);
        }
        return a.ink.body(a.getString(R.string.desktop_missing_tile));
    }

    private void save() {
        layout.save(a);
        a.render();
    }

    private void addMenu(int x, int y) {
        final int dest = page;
        a.choose(a.getString(R.string.desktop_add), a.getResources().getStringArray(R.array.desktop_tile_names), -1, i -> {
            String kind = KINDS[i];
            int[] spot = firstSpace(dest, x, y);
            if (spot == null) {
                chooseSplit(kind, dest);
                return;
            }
            withWidgetId(kind, id -> {
                if (layout == null || !layout.fits(null, dest, spot[0], spot[1], spot[2], spot[3])) {
                    rejectInsert(id);
                    return;
                }
                layout.tiles.add(new HomeLayout.Tile(kind, dest, spot[0], spot[1], spot[2], spot[3], id));
                save();
            });
        });
    }

    private void withWidgetId(String kind, IntConsumer insert) {
        if ("widget".equals(kind)) a.homeWidgets.pick(id -> { if (id >= 0) insert.accept(id); });
        else insert.accept(-1);
    }

    private void rejectInsert(int id) {
        if (id >= 0) a.homeWidgets.remove(id);
        a.toast(a.getString(R.string.desktop_occupied));
    }

    private void chooseSplit(String kind, int dest) {
        List<HomeLayout.Tile> choices = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (HomeLayout.Tile t : layout.tiles) {
            if (t.page != dest || (t.w == 1 && t.h == 1)) continue;
            choices.add(t);
            labels.add(a.getString(R.string.desktop_split_choice, name(t.kind), t.w, t.h, t.x + 1, t.y + 1));
        }
        if (choices.isEmpty()) {
            a.choose(a.getString(R.string.desktop_full),
                    new String[]{a.getString(R.string.desktop_add_page)}, -1, i -> withWidgetId(kind, id -> {
                if (layout == null) { rejectInsert(id); return; }
                page = layout.pages++;
                layout.tiles.add(new HomeLayout.Tile(kind, page, 0, 0, 2, 2, id));
                save();
            }));
            return;
        }
        a.choose(a.getString(R.string.desktop_split_title, name(kind)), labels.toArray(new String[0]), -1,
                i -> withWidgetId(kind, id -> {
                    HomeLayout.Tile t = choices.get(i);
                    if (layout == null || !layout.tiles.contains(t) || t.page != dest || (t.w == 1 && t.h == 1)) {
                        rejectInsert(id);
                        return;
                    }
                    layout.tiles.add(HomeMosaic.split(t, kind, id));
                    save();
                }));
    }

    private int[] firstSpace(int dest, int x, int y) {
        for (int[] size : new int[][]{{2, 2}, {2, 1}, {1, 2}, {1, 1}}) {
            for (int row = 0; row < ROWS; row++) for (int col = 0; col < COLS; col++) {
                if ((x < 0 || (col == x && row == y))
                        && layout.fits(null, dest, col, row, size[0], size[1])) {
                    return new int[]{col, row, size[0], size[1]};
                }
            }
        }
        return null;
    }

    private void tileOptions(HomeLayout.Tile t) {
        a.choose(name(t.kind), a.getResources().getStringArray(R.array.desktop_tile_actions), -1, i -> {
            if (i == 0) { moving = t; a.render(); }
            if (i == 1) resize(t);
            if (i == 2) confirm(a.getString(R.string.desktop_remove_tile, name(t.kind)), () -> {
                layout.tiles.remove(t);
                if ("widget".equals(t.kind)) a.homeWidgets.remove(t.widgetId);
                save();
            });
        });
    }

    private void resize(HomeLayout.Tile t) {
        List<int[]> sizes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int selected = -1;
        for (int h = 1; h <= ROWS; h++) for (int w = 1; w <= COLS; w++) {
            if (!layout.fits(t, page, t.x, t.y, w, h)) continue;
            if (w == t.w && h == t.h) selected = sizes.size();
            sizes.add(new int[]{w, h});
            labels.add(a.getString(R.string.desktop_size, w, h));
        }
        a.choose(a.getString(R.string.desktop_tile_size), labels.toArray(new String[0]), selected, i -> {
            t.w = sizes.get(i)[0];
            t.h = sizes.get(i)[1];
            save();
        });
    }

    private void addPage() {
        a.choose(a.getString(R.string.desktop_add_page),
                a.getResources().getStringArray(R.array.desktop_page_templates), -1, index -> {
            String[] keys = {"reading", "planner", "night"};
            page = layout.pages++;
            if (index < keys.length) {
                for (HomeLayout.Tile t : HomeLayout.preset(keys[index]).tiles) {
                    t.page = page;
                    layout.tiles.add(t);
                }
            }
            save();
        });
    }

    private void options() {
        a.choose(a.getString(R.string.desktop_options), a.getResources().getStringArray(R.array.desktop_home_options), -1, i -> {
            switch (i) {
                case 0: addPage(); break;
                case 1:
                    if (layout.pages == 1) { a.toast(a.getString(R.string.desktop_keep_page)); return; }
                    for (HomeLayout.Tile t : layout.tiles) if (t.page == page) {
                        a.toast(a.getString(R.string.desktop_move_first)); return;
                    }
                    for (HomeLayout.Tile t : layout.tiles) if (t.page > page) t.page--;
                    layout.pages--;
                    page = Math.min(page, layout.pages - 1);
                    save();
                    break;
                case 2: a.homeWidgets.settings(); break;
                case 3: confirm(a.getString(R.string.desktop_reset), this::reset); break;
                case 4: HomeMosaic.tidy(layout, page); save(); break;
            }
        });
    }

    void reset() {
        if (layout == null) layout = HomeLayout.load(a);
        for (HomeLayout.Tile t : layout.tiles) if ("widget".equals(t.kind)) a.homeWidgets.remove(t.widgetId);
        HomeLayout.reset(a);
        layout = null;
        editing = false;
        moving = null;
        page = 0;
        a.render();
    }

    private void confirm(String title, Runnable action) {
        a.choose(title, a.getResources().getStringArray(R.array.desktop_remove_actions), -1, i -> { if (i == 1) action.run(); });
    }

    private final class Board extends ViewGroup {
        private final Paint rule = new Paint();
        private float downX, downY;
        private boolean swipe;
        Board() { super(a); setWillNotDraw(!editing); rule.setColor(a.ink.LINE); }

        void cell(int x, int y, int w, int h, View v) { addView(v, new Cell(x, y, w, h)); }

        @Override protected void onMeasure(int width, int height) {
            int ww = MeasureSpec.getSize(width), hh = MeasureSpec.getSize(height);
            setMeasuredDimension(ww, hh);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                Cell c = (Cell) child.getLayoutParams();
                int w = ww * (c.x + c.w) / COLS - ww * c.x / COLS;
                int h = hh * (c.y + c.h) / ROWS - hh * c.y / ROWS;
                int gap = a.ink.dp(2) * 2;
                child.measure(MeasureSpec.makeMeasureSpec(Math.max(0, w - gap), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(0, h - gap), MeasureSpec.EXACTLY));
            }
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                Cell c = (Cell) child.getLayoutParams();
                int x = getWidth() * c.x / COLS + a.ink.dp(2);
                int y = getHeight() * c.y / ROWS + a.ink.dp(2);
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            for (int x = 1; x < COLS; x++) canvas.drawLine(getWidth() * x / COLS, 0,
                    getWidth() * x / COLS, getHeight(), rule);
            for (int y = 1; y < ROWS; y++) canvas.drawLine(0, getHeight() * y / ROWS,
                    getWidth(), getHeight() * y / ROWS, rule);
        }

        private final int touchSlop = ViewConfiguration.get(a).getScaledTouchSlop();
        private MotionEvent press;
        private final Runnable longPress = this::selectPressedTile;

        // Observe the whole gesture before dispatch, including events owned by widget descendants.
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    clearPress();
                    if (moving == null) {
                        press = MotionEvent.obtain(event);
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (press != null && Math.hypot(event.getX() - press.getX(),
                            event.getY() - press.getY()) > touchSlop) clearPress();
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    clearPress();
                    break;
            }
            return super.dispatchTouchEvent(event);
        }

        private void selectPressedTile() {
            if (press == null) return;
            HomeLayout.Tile selected = null;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (press.getX() >= child.getLeft() && press.getX() < child.getRight()
                        && press.getY() >= child.getTop() && press.getY() < child.getBottom()) {
                    selected = ((Cell) child.getLayoutParams()).tile;
                    break;
                }
            }
            // Cancel the child's pending click before replacing its view or opening a sheet.
            press.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(press);
            clearPress();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (!editing) edit();
            if (selected != null) tileOptions(selected);
        }

        private void clearPress() {
            removeCallbacks(longPress);
            if (press == null) return;
            press.recycle();
            press = null;
        }

        @Override protected void onDetachedFromWindow() {
            clearPress();
            super.onDetachedFromWindow();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getX(); downY = e.getY(); swipe = false;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE && isPageGesture(e)) swipe = true;
            return swipe;
        }

        private boolean isPageGesture(MotionEvent e) {
            float dx = e.getX() - downX;
            return a.store.swipePages() && (dx < 0 ? page + 1 < layout.pages : page > 0)
                    && Math.abs(dx) > a.ink.dp(48) && Math.abs(dx) > 2 * Math.abs(e.getY() - downY);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE && isPageGesture(e)) {
                swipe = true;
                cancelLongPress();
            }
            if (!swipe) return super.onTouchEvent(e);
            if (e.getActionMasked() == MotionEvent.ACTION_UP && isPageGesture(e))
                page(e.getX() < downX ? 1 : -1);
            return true;
        }
    }

    private static final class Cell extends ViewGroup.LayoutParams {
        HomeLayout.Tile tile;
        final int x, y, w, h;
        Cell(int x, int y, int w, int h) {
            super(MATCH_PARENT, MATCH_PARENT);
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
}

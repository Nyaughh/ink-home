package dev.inkhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.function.Consumer;

/** Live and edited instances have identical pixel bounds, including on odd-width displays. */
final class HomeGrid extends ViewGroup {
    private final Paint outline = new Paint();
    private final int stroke;
    private FrameLayout selected;

    HomeGrid(MainActivity host, HomeLayout layout, Consumer<HomeLayout.Item> select,
             Runnable longPress, String selectedKey) {
        super(host);
        boolean editing = select != null;
        stroke = Math.max(1, host.ink.dp(2));
        outline.setColor(host.ink.TEXT);
        if (editing) setOnClickListener(v -> select.accept(null));
        if (longPress != null) setOnLongClickListener(v -> { longPress.run(); return true; });
        for (HomeLayout.Item item : layout.items) {
            HomeComponent component = HomeComponents.get(item.type);
            if (component == null) continue;
            FrameLayout tile = new FrameLayout(host);
            tile.setTag(item);
            tile.addView(component.render(host, item, false), new FrameLayout.LayoutParams(-1, -1));
            if (editing) {
                View handle = new View(host);
                handle.setContentDescription(component.name(host));
                handle.setOnClickListener(v -> select.accept(item));
                handle.setFocusable(true);
                tile.getChildAt(0).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                tile.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
                tile.addView(handle, new FrameLayout.LayoutParams(-1, -1));
                if (item.key.equals(selectedKey)) selected = tile;
            }
            addView(tile);
        }
    }

    void setResizeAction(Consumer<HomeLayout.Item> resize) {
        if (selected == null) return;
        HomeLayout.Item item = (HomeLayout.Item) selected.getTag();
        View grip = new View(getContext());
        grip.setContentDescription(getResources().getString(R.string.editor_size));
        grip.setFocusable(true);
        grip.setOnClickListener(v -> resize.accept(item));
        int target = ((MainActivity) getContext()).ink.dp(44);
        selected.addView(grip, new FrameLayout.LayoutParams(target, target, Gravity.BOTTOM | Gravity.RIGHT));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) child.getTag();
            int w = (item.x + item.w) * width / HomeLayout.COLS - item.x * width / HomeLayout.COLS;
            int h = (item.y + item.h) * height / HomeLayout.ROWS - item.y * height / HomeLayout.ROWS;
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) child.getTag();
            int x = item.x * (r - l) / HomeLayout.COLS, y = item.y * (b - t) / HomeLayout.ROWS;
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (selected == null) return;
        float half = stroke / 2f;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(stroke);
        canvas.drawRect(selected.getLeft() + half, selected.getTop() + half,
                selected.getRight() - half, selected.getBottom() - half, outline);
        outline.setStyle(Paint.Style.FILL);
        int size = stroke * 4;
        canvas.drawRect(selected.getRight() - size, selected.getBottom() - size,
                selected.getRight(), selected.getBottom(), outline);
    }
}

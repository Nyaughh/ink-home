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
    private final int[] rowEdges = new int[HomeLayout.ROWS + 1];
    private final int[] intrinsicHeights = new int[HomeLayout.ROWS];

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

    int rowSpan(int y, int rows) {
        return rowEdges[y + rows] - rowEdges[y];
    }

    private void measureRows(int width, int height) {
        final int rows = HomeLayout.ROWS;
        java.util.Arrays.fill(intrinsicHeights, 0);
        int flexibleRows = rows;
        long requestedExtra = 0;
        for (int i = 0; i < getChildCount(); i++) {
            FrameLayout tile = (FrameLayout) getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) tile.getTag();
            if (item.w != HomeLayout.COLS || !"content".equals(item.settings.get("heightMode"))) continue;
            // Measure the component alone. The editor's tap shield and grip do not contribute height.
            View component = tile.getChildAt(0);
            component.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int natural = Math.max(item.h, component.getMeasuredHeight());
            flexibleRows -= item.h;
            requestedExtra += natural - item.h;
            for (int r = 0; r < item.h; r++) {
                intrinsicHeights[item.y + r] = (int) ((long) natural * (r + 1) / item.h
                        - (long) natural * r / item.h);
            }
        }

        if (flexibleRows == rows || height < rows) {
            for (int r = 1; r <= rows; r++) rowEdges[r] = height * r / rows;
            return;
        }

        // Reserve one pixel per track. Share a shortage proportionally between intrinsic
        // tracks, and otherwise give the remaining space to the flexible tracks.
        int spare = height - rows;
        int intrinsicExtra = flexibleRows == 0 ? spare : (int) Math.min(spare, requestedExtra);
        int flexibleExtra = spare - intrinsicExtra;
        long demand = 0;
        int flexible = 0;
        for (int r = 0; r < rows; r++) {
            if (intrinsicHeights[r] == 0) flexible++;
            else demand += intrinsicHeights[r] - 1;
            int intrinsicPixels = requestedExtra == 0
                    ? (flexibleRows == 0 ? intrinsicExtra * (r + 1) / rows : 0)
                    : (int) (demand * intrinsicExtra / requestedExtra);
            int flexiblePixels = flexibleRows == 0 ? 0 : flexible * flexibleExtra / flexibleRows;
            rowEdges[r + 1] = r + 1 + intrinsicPixels + flexiblePixels;
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        measureRows(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) child.getTag();
            int w = (item.x + item.w) * width / HomeLayout.COLS - item.x * width / HomeLayout.COLS;
            int h = rowSpan(item.y, item.h);
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) child.getTag();
            int x = item.x * (r - l) / HomeLayout.COLS, y = rowEdges[item.y];
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

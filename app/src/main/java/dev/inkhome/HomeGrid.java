package dev.inkhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import android.graphics.drawable.ColorDrawable;

/** Live and edited instances have identical pixel bounds, including on odd-width displays. */
final class HomeGrid extends ViewGroup {
    private final Ink ink;
    private final List<HomeTileFrame> faces = new ArrayList<>();
    private List<HomeGridEdges.Strip> strips = Collections.emptyList();
    private final Paint outline = new Paint();
    private final int stroke;
    private final int touchSlop;
    private FrameLayout selected;
    private final int[] rowEdges;
    private final HomeLayout layout;
    private Runnable dropAction;
    private Consumer<Boolean> gestureActive;
    private final Rect ghost = new Rect();
    private final int[] intrinsicHeights;

    HomeGrid(MainActivity host, HomeLayout layout, Consumer<HomeLayout.Item> select,
             Runnable longPress, String selectedKey) {
        super(host);
        this.layout = layout;
        ink = host.ink;
        rowEdges = new int[layout.rows + 1];
        intrinsicHeights = new int[layout.rows];
        if (select != null) setMotionEventSplittingEnabled(false);
        touchSlop = ViewConfiguration.get(host).getScaledTouchSlop();
        boolean editing = select != null;
        stroke = Math.max(1, host.ink.dp(2));
        if (editing) setOnClickListener(v -> select.accept(null));
        if (longPress != null) setOnLongClickListener(v -> { longPress.run(); return true; });
        for (HomeLayout.Item item : layout.items) {
            HomeComponent component = HomeComponents.get(item.type);
            if (component == null) continue;
            FrameLayout tile = editing ? new FrameLayout(host) : new HomeWidgetTarget(host, item, component);
            tile.setTag(item);
            View rendered = component.render(host, item, false);
            HomeTileFrame face = component.squareFace() ? (HomeTileFrame) ((FrameLayout) rendered).getChildAt(0)
                    : (HomeTileFrame) rendered;
            faces.add(face);
            tile.addView(rendered, new FrameLayout.LayoutParams(-1, -1));
            if (editing) {
                tile.setMotionEventSplittingEnabled(false);
                View handle = new View(host);
                handle.setContentDescription(component.name(host));
                handle.setOnClickListener(v -> select.accept(item));
                handle.setFocusable(true);
                handle.setOnTouchListener(new HomeGridGesture(this, layout, item, HomeGridGesture.Handle.MOVE, touchSlop));
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
        addGrip(HomeGridGesture.Handle.TOP_LEFT, Gravity.TOP | Gravity.LEFT, R.string.resize_top_left, resize);
        addGrip(HomeGridGesture.Handle.TOP_RIGHT, Gravity.TOP | Gravity.RIGHT, R.string.resize_top_right, resize);
        addGrip(HomeGridGesture.Handle.BOTTOM_LEFT, Gravity.BOTTOM | Gravity.LEFT, R.string.resize_bottom_left, resize);
        addGrip(HomeGridGesture.Handle.BOTTOM_RIGHT, Gravity.BOTTOM | Gravity.RIGHT, R.string.resize_bottom_right, resize);
    }

    private void addGrip(HomeGridGesture.Handle corner, int gravity, int label,
                         Consumer<HomeLayout.Item> resize) {
        HomeLayout.Item item = (HomeLayout.Item) selected.getTag();
        View grip = new View(getContext());
        grip.setContentDescription(getResources().getString(label));
        grip.setFocusable(true);
        grip.setOnTouchListener(new HomeGridGesture(this, layout, item, corner, touchSlop));
        grip.setOnClickListener(v -> resize.accept(item));
        selected.addView(grip, new FrameLayout.LayoutParams(0, 0, gravity));
    }

    void setDropAction(Runnable action) {
        dropAction = action;
    }

    void setGestureActive(Consumer<Boolean> listener) {
        gestureActive = listener;
    }

    void dragging(boolean active) {
        if (gestureActive != null) gestureActive.accept(active);
        if (!active && !ghost.isEmpty()) {
            ghost.setEmpty();
            rebuildEdges();
            invalidate();
        }
    }

    void showTarget(int x, int y, int w, int h) {
        int left = x * getWidth() / layout.cols, right = (x + w) * getWidth() / layout.cols;
        if (ghost.left == left && ghost.right == right && ghost.top == rowEdges[y]
                && ghost.bottom == rowEdges[y + h]) return;
        ghost.set(left, rowEdges[y], right, rowEdges[y + h]);
        rebuildEdges();
        invalidate();
    }

    void dropped() {
        requestLayout();
        if (dropAction != null) dropAction.run();
    }

    int rowSpan(int y, int rows) {
        return rowEdges[y + rows] - rowEdges[y];
    }

    private void measureRows(int width, int height) {
        final int rows = layout.rows;
        java.util.Arrays.fill(intrinsicHeights, 0);
        int flexibleRows = rows;
        long requestedExtra = 0;
        for (int i = 0; i < getChildCount(); i++) {
            FrameLayout tile = (FrameLayout) getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) tile.getTag();
            if (item.w != layout.cols || !"content".equals(item.settings.get("heightMode"))) continue;
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
            int w = (item.x + item.w) * width / layout.cols - item.x * width / layout.cols;
            int h = rowSpan(item.y, item.h);
            if (child == selected) {
                int target = ((MainActivity) getContext()).ink.dp(44);
                // Leave the middle available for moving even on single-track widgets.
                for (int g = 2; g < selected.getChildCount(); g++) {
                    LayoutParams grip = selected.getChildAt(g).getLayoutParams();
                    grip.width = Math.min(target, Math.max(1, w / 3));
                    grip.height = Math.min(target, Math.max(1, h / 2));
                }
            }
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            HomeLayout.Item item = (HomeLayout.Item) child.getTag();
            int x = item.x * (r - l) / layout.cols, y = rowEdges[item.y];
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
        rebuildEdges();
    }

    private void rebuildEdges() {
        HomeGridEdges edges = new HomeGridEdges();
        Rect bounds = new Rect();
        for (int i = 0; i < faces.size(); i++) {
            HomeTileFrame face = faces.get(i);
            if (face.bordered) {
                face.getDrawingRect(bounds);
                offsetDescendantRectToMyCoords(face, bounds);
                edges.ring(bounds.left, bounds.top, bounds.right, bounds.bottom,
                        ink.folio.rule, ink.folio.palette.strong, HomeGridEdges.BORDER);
            } else {
                HomeLayout.Item item = (HomeLayout.Item) getChildAt(i).getTag();
                if ("next".equals(item.type) || "apps".equals(item.type)) {
                    collectSeparator(edges, face, bounds);
                }
            }
        }
        if (!ghost.isEmpty()) bounds.set(ghost);
        else if (selected != null) selected.getHitRect(bounds);
        else bounds.setEmpty();
        edges.ring(bounds.left, bounds.top, bounds.right, bounds.bottom, stroke,
                ghost.isEmpty() ? ink.TEXT : android.graphics.Color.BLACK, HomeGridEdges.SELECTION);
        strips = edges.resolve();
    }

    private void collectSeparator(HomeGridEdges edges, HomeTileFrame face, Rect bounds) {
        View separator = face.findViewById(R.id.home_section_rule);
        if (separator == null || separator.getHeight() == 0) return;
        separator.getDrawingRect(bounds);
        offsetDescendantRectToMyCoords(separator, bounds);
        Rect frameBounds = new Rect();
        face.getDrawingRect(frameBounds);
        offsetDescendantRectToMyCoords(face, frameBounds);
        boolean atEdge = bounds.top == frameBounds.top && bounds.intersect(frameBounds);
        separator.setAlpha(atEdge ? 0f : 1f);
        if (atEdge) {
            int color = ((ColorDrawable) separator.getBackground()).getColor();
            edges.add(new HomeGridEdges.Edge(false, bounds.top, bounds.left, bounds.right,
                    bounds.height(), color, HomeGridEdges.SEPARATOR));
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!ghost.isEmpty()) {
            float inset = stroke * 1.5f;
            outline.setStyle(Paint.Style.STROKE);
            outline.setColor(android.graphics.Color.WHITE);
            outline.setStrokeWidth(3 * stroke);
            canvas.drawRect(ghost.left + inset, ghost.top + inset,
                    ghost.right - inset, ghost.bottom - inset, outline);
        }
        outline.setStyle(Paint.Style.FILL);
        for (HomeGridEdges.Strip strip : strips) {
            outline.setColor(strip.color());
            canvas.drawRect(strip.left(), strip.top(), strip.right(), strip.bottom(), outline);
        }
        if (selected == null || !ghost.isEmpty()) return;
        int size = stroke * 4;
        for (int x : new int[]{selected.getLeft(), selected.getRight() - size}) {
            for (int y : new int[]{selected.getTop(), selected.getBottom() - size}) {
                outline.setColor(android.graphics.Color.WHITE);
                canvas.drawRect(x, y, x + size, y + size, outline);
                outline.setColor(android.graphics.Color.BLACK);
                canvas.drawRect(x + stroke, y + stroke, x + size - stroke, y + size - stroke, outline);
            }
        }
    }
}

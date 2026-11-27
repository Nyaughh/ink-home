package dev.inkhome.folio.paging;

import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.Pagination;

public final class PagedFlow extends PageLayout {
    private static final Object ITEM = new Object(), GROUP = new Object();
    private final LinearLayout body;
    private final FrameLayout viewport;
    private int visibleHeight;

    public PagedFlow(Folio f, LinearLayout body, int page, IntConsumer remember, Runnable changed) {
        super(f, null, page, remember, changed);
        this.body = body;
        viewport = new FrameLayout(f.context) {
            @Override protected void onMeasure(int width, int height) {
                int w = MeasureSpec.getSize(width);
                body.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                setMeasuredDimension(w, visibleHeight);
            }

            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && event.getY() >= visibleHeight) return false;
                return super.dispatchTouchEvent(event);
            }
        };
        viewport.addView(body, new FrameLayout.LayoutParams(-1, -2));
        install(viewport);
    }

    public static <T extends View> T item(T view) { view.setTag(ITEM); return view; }
    public static <T extends View> T group(T view) { view.setTag(GROUP); return view; }

    public static int topIn(View v, View ancestor) {
        int top = 0;
        while (v != ancestor && v != null) {
            top += v.getTop();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return top;
    }

    public void jumpTo(View view) { jump(topIn(view, body)); }
    @Override int extent() { return body.getHeight(); }

    @Override Pagination paginate(int width, int height, int footer) {
        List<Pagination.Item> items = new ArrayList<>();
        for (int i = 0; i < body.getChildCount(); i++) {
            View child = body.getChildAt(i);
            int before = items.size();
            collect(child, items, -1, -1, -1);
            if (before == items.size()) add(child, items, -1, -1, -1);
        }
        return Pagination.flow(items, extent(), height, footer);
    }

    private void collect(View view, List<Pagination.Item> items, int start, int end, int first) {
        if (view.getVisibility() == GONE) return;
        if (view.getTag() == ITEM) {
            add(view, items, start, end, first);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        boolean grouped = view.getTag() == GROUP;
        if (grouped) {
            start = topIn(view, body);
            end = start + view.getHeight();
            first = items.size();
        }
        ViewGroup container = (ViewGroup) view;
        for (int i = 0; i < container.getChildCount(); i++) collect(container.getChildAt(i), items, start, end, first);
        if (grouped && items.size() == first) add(view, items, start, end, first);
    }

    private void add(View view, List<Pagination.Item> items, int start, int end, int first) {
        if (view.getVisibility() == GONE) return;
        int top = topIn(view, body);
        items.add(new Pagination.Item(top, top + view.getHeight(), start, end, items.size() == first));
    }

    @Override void show(int start, int end) {
        body.setTranslationY(-start);
        if (visibleHeight != end - start) {
            visibleHeight = end - start;
            viewport.requestLayout();
        }
        viewport.setClipBounds(new Rect(0, 0, getWidth(), visibleHeight));
    }
}

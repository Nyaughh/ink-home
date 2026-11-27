package dev.inkhome.folio.paging;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.function.IntConsumer;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.Pagination;
import dev.inkhome.folio.widget.Pager;

/** Common page navigation, visible controls, and pre-draw measurement. */
public abstract class PageLayout extends LinearLayout implements ViewTreeObserver.OnPreDrawListener {
    final Folio folio;
    private final Pager pager;
    private final LinearLayout header;
    private final IntConsumer remember;
    private final Runnable changed;
    private final int footerHeight;
    private ScrollView overflow;
    private int page;
    private Pagination pages;
    private int lastWidth = -1, lastHeight = -1, lastExtent = -1;
    private float downX, downY;
    private boolean swiping;

    PageLayout(Folio folio, LinearLayout header, int initialPage, IntConsumer remember, Runnable changed) {
        super(folio.context);
        this.folio = folio;
        this.page = initialPage;
        this.remember = remember;
        this.changed = changed;
        this.header = header;
        setOrientation(VERTICAL);
        footerHeight = header == null ? folio.u(40) : 0;
        pager = new Pager(folio, header != null, this::previous, this::next);
        if (header != null) {
            header.addView(pager, new LayoutParams(-2, -1));
            addView(header, new LayoutParams(-1, folio.u(40)));
        }
    }

    final void install(View area) {
        overflow = new ScrollView(getContext()) {
            @Override public boolean onInterceptTouchEvent(MotionEvent e) {
                return (canScrollVertically(1) || canScrollVertically(-1)) && super.onInterceptTouchEvent(e);
            }

            @Override public boolean onTouchEvent(MotionEvent e) {
                return (canScrollVertically(1) || canScrollVertically(-1)) && super.onTouchEvent(e);
            }
        };
        overflow.setVerticalScrollBarEnabled(false);
        overflow.setOverScrollMode(OVER_SCROLL_NEVER);
        overflow.setVerticalFadingEdgeEnabled(false);
        overflow.addView(area, new ScrollView.LayoutParams(-1, -2));
        addView(overflow, new LayoutParams(-1, 0, 1));
        if (header == null) addView(pager, new LayoutParams(-1, footerHeight));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        super.onDetachedFromWindow();
    }

    @Override public boolean onPreDraw() {
        int height = getHeight() - (header == null ? 0 : header.getHeight());
        int width = getWidth(), extent = extent();
        if (height <= 0 || width <= 0) return true;
        if (lastWidth == width && lastHeight == height && lastExtent == extent) return true;
        lastWidth = width;
        lastHeight = height;
        lastExtent = extent;
        pages = paginate(width, height, footerHeight);
        page = Math.max(0, Math.min(page, pages.count() - 1));
        drawPage();
        postInvalidateOnAnimation();
        return false;
    }

    int extent() { return 0; }
    abstract Pagination paginate(int width, int height, int footer);
    abstract void show(int start, int end);

    public final void next() { move(page + 1); }
    public final void previous() { move(page - 1); }
    final void jump(int position) { if (pages != null) move(pages.pageAt(position)); }

    private void move(int to) {
        if (pages == null || to < 0 || to >= pages.count() || to == page) return;
        page = to;
        drawPage();
        changed.run();
    }

    private void drawPage() {
        remember.accept(page);
        overflow.scrollTo(0, 0);
        show(pages.start(page), pages.end(page));
        pager.bind(page, pages.count());
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            swiping = false;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && crossed(e)) {
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (Math.abs(dy) > Math.abs(dx) && overflow.canScrollVertically(dy < 0 ? 1 : -1)) return false;
            swiping = true;
            return true;
        }
        return false;
    }

    private boolean crossed(MotionEvent e) {
        return pages != null && pages.count() > 1 &&
                Math.max(Math.abs(e.getX() - downX), Math.abs(e.getY() - downY)) >= folio.u(32);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && (swiping || crossed(e))) {
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (crossed(e)) {
                if ((Math.abs(dx) > Math.abs(dy) ? dx : dy) < 0) next(); else previous();
            }
        }
        return true;
    }
}

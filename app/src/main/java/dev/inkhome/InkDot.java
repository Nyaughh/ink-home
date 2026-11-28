package dev.inkhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Glyph;

/** A tap cleans, a hold opens options, and dragging tracks the pointer. */
final class InkDot extends View {
    interface Actions {
        void beginDrag();
        void move(float dx, float dy);
        void endDrag(boolean save);
        void options();
        void clean();
    }

    private final Folio folio;
    private final Actions actions;
    private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Glyph glyph = new Glyph("refresh");
    private final int slop;
    private float downX, downY;
    private boolean dragged, held, open, tucked, left;
    private int diameter;

    InkDot(Folio folio, Actions actions) {
        super(folio.context);
        this.folio = folio;
        this.actions = actions;
        slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        setContentDescription(getContext().getString(R.string.service_ui_ink_dot_clean_screen));
        setOnClickListener(v -> actions.clean());
        setOnLongClickListener(v -> { actions.options(); return true; });
    }

    void appearance(boolean tucked, boolean left, int size, boolean open) {
        this.tucked = tucked;
        this.left = left;
        this.open = open;
        diameter = folio.u(size);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        int paper = folio.palette.paper, ink = folio.palette.ink;
        int x = getWidth() / 2, y = getHeight() / 2;
        int radius = diameter / 2;
        boolean inverse = dragged || open;
        if (tucked) {
            int width = folio.u(8), height = folio.u(40);
            int start = left ? 0 : getWidth() - width;
            brush.setColor(paper);
            c.drawRect(start-2, y-height/2-2, start+width+2, y+height/2+2, brush);
            brush.setColor(ink);
            c.drawRect(start, y-height/2, start+width, y+height/2, brush);
            return;
        }
        brush.setColor(paper);
        c.drawCircle(x, y, radius+2, brush);
        brush.setColor(ink);
        c.drawCircle(x, y, radius, brush);
        if (!inverse) {
            brush.setColor(paper);
            c.drawCircle(x, y, radius-folio.rule, brush);
        }
        int half = folio.u(11);
        glyph.setTint(inverse ? paper : ink);
        glyph.setBounds(x-half, y-half, x+half, y+half);
        glyph.draw(c);
    }

    private final Runnable hold = () -> { held = true; performLongClick(); };

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getRawX(), y = e.getRawY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                held = dragged = false;
                postDelayed(hold, 400);
                return true;
            case MotionEvent.ACTION_MOVE:
                track(x, y);
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(hold);
                track(x, y);
                if (dragged) actions.endDrag(true);
                else if (!held) performClick();
                dragged = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                if (dragged) actions.endDrag(false);
                held = true;
                dragged = false;
                invalidate();
                return true;
            default: return true;
        }
    }

    private void track(float x, float y) {
        if (held) return;
        float dx = x - downX, dy = y - downY;
        if (!dragged) {
            if (Math.hypot(dx, dy) <= slop) return;
            removeCallbacks(hold);
            dragged = true;
            actions.beginDrag();
            invalidate();
        }
        actions.move(dx, dy);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(hold);
        super.onDetachedFromWindow();
    }
}

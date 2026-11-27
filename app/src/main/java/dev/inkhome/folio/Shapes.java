package dev.inkhome.folio;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

public final class Shapes {
    private final Folio folio;
    public Shapes(Folio folio) { this.folio = folio; }

    public GradientDrawable stroke(int fill, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setStroke(folio.hairline, border);
        return shape;
    }

    private Drawable feedback(Drawable rest, Drawable pressed) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, rest);
        return states;
    }

    public Drawable pressable() {
        Drawable rest = new ColorDrawable(Color.TRANSPARENT);
        return folio.displayClass.isEink() ? rest : feedback(rest, new ColorDrawable(folio.palette.fill));
    }

    public Drawable card(boolean tappable) {
        Drawable rest = stroke(folio.palette.paper, folio.palette.rule);
        return tappable && !folio.displayClass.isEink()
                ? feedback(rest, stroke(folio.palette.fill, folio.palette.rule)) : rest;
    }

    public Drawable listRow(int insetPx) {
        Drawable rest = underlined(Color.TRANSPARENT, insetPx);
        return folio.displayClass.isEink() ? rest : feedback(rest, underlined(folio.palette.fill, insetPx));
    }

    private Drawable underlined(int fill, int insetPx) {
        return new Drawable() {
            private final Paint paint = new Paint();
            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                paint.setColor(fill);
                canvas.drawRect(bounds, paint);
                paint.setColor(folio.palette.rule);
                canvas.drawRect(bounds.left + insetPx, bounds.bottom - folio.hairline,
                        bounds.right, bounds.bottom, paint);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter filter) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }
}

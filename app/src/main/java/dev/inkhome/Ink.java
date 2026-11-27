package dev.inkhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkhome.folio.*;
import dev.inkhome.folio.core.TypeScale;

/** Compatibility facade for screens as they migrate to Folio components. */
final class Ink {
    final Folio folio;
    final Shapes shapes;
    final Context c;
    final float scale, textOnly;
    final int TEXT, TEXT2, TEXT3, PAPER, LINE, FILL, PRESSED;
    final Typeface REGULAR, MEDIUM, BOLD;

    Ink(Context c, float textScale, float uiScale) {
        this.c = c;
        folio = new Folio(c, textScale, uiScale);
        shapes = new Shapes(folio);
        textOnly = textScale;
        scale = textScale * uiScale;
        TEXT = folio.palette.ink;
        TEXT2 = folio.palette.ink2;
        TEXT3 = folio.palette.ink3;
        PAPER = folio.palette.paper;
        LINE = folio.palette.rule;
        FILL = folio.palette.fill;
        PRESSED = folio.displayClass.isEink() ? PAPER : FILL;
        REGULAR = Type.sans(c, TypeScale.BODY.weight(folio.displayClass));
        MEDIUM = Type.sans(c, TypeScale.BODY_LG.weight(folio.displayClass));
        BOLD = Type.sans(c, TypeScale.TITLE.weight(folio.displayClass));
    }

    int dp(float units) { return folio.u(units); }

    TextView text(CharSequence s, TypeScale role, int color) { return Type.text(folio, s, role, color); }
    TextView body(CharSequence s) { return text(s, TypeScale.BODY_LG, TEXT); }
    TextView medium(CharSequence s, float size) {
        return text(s, size >= 17 ? TypeScale.TITLE_SM : size >= 15 ? TypeScale.BODY_LG : TypeScale.LABEL, TEXT);
    }
    TextView muted(CharSequence s, float size) { return text(s, size >= 15 ? TypeScale.BODY : TypeScale.META, TEXT2); }
    TextView faint(CharSequence s, float size) { return text(s, size >= 15 ? TypeScale.BODY : TypeScale.META, TEXT3); }
    TextView title(CharSequence s) { return text(s, TypeScale.TITLE_LG, TEXT); }

    TextView heading(CharSequence s) {
        TextView t = text(s, TypeScale.LABEL, TEXT2);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    TextView lines(TextView t, int n) {
        t.setMaxLines(n);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    TextView center(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    // ---------------------------------------------------------------- layout

    LinearLayout col() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    View hair() {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, folio.hairline));
        return v;
    }

    View flex() {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    View vflex() {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        return v;
    }

    LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }

    LinearLayout.LayoutParams m(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    /** A scroll container with no scroll bar, edge glow or fading: plain e-ink friendly scrolling. */
    ScrollView scroller(View child) {
        ScrollView s = new ScrollView(c);
        s.setVerticalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setVerticalFadingEdgeEnabled(false);
        s.setSmoothScrollingEnabled(false);
        s.addView(child, new ScrollView.LayoutParams(-1, -2));
        return s;
    }

    // ---------------------------------------------------------------- pieces

    ImageView icon(String name, int sizeDp) { return icon(new Glyph(name), sizeDp); }

    ImageView icon(Drawable d, int sizeDp) {
        ImageView iv = new ImageView(c);
        if (d instanceof Glyph) d.setTint(TEXT);
        iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** Toolbar action with a 48-unit touch target. */
    ImageView iconButton(String name, Runnable click) {
        ImageView iv = icon(name, 48);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        iv.setBackground(pressable());
        iv.setOnClickListener(v -> click.run());
        return iv;
    }

    ImageView cover(Bitmap b) {
        ImageView iv = new ImageView(c);
        iv.setImageBitmap(b);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setPadding(1, 1, 1, 1);
        iv.setBackgroundColor(TEXT);
        return iv;
    }

    View progress(float fraction, int heightDp) {
        return new Bar(c, fraction, dp(heightDp));
    }

    /** Compact filter chip. */
    TextView pill(String s, boolean on) {
        TextView t = text(s, TypeScale.LABEL, on ? folio.palette.onInverse : TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(on ? solid(folio.palette.inverse, 0) : card(7, true));
        return t;
    }

    /** Single-choice setting with inverse fill on the selected segment. */
    LinearLayout segmented(String[] labels, int active, java.util.function.IntConsumer pick) {
        LinearLayout s = row();
        s.setPadding(dp(3), dp(3), dp(3), dp(3));
        s.setBackground(stroke(PAPER, 0, LINE));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == active;
            TextView t = text(labels[i], TypeScale.LABEL, on ? folio.palette.onInverse : TEXT);
            t.setGravity(Gravity.CENTER);
            t.setBackground(on ? solid(folio.palette.inverse, 0) : pressable());
            t.setOnClickListener(v -> pick.accept(idx));
            s.addView(t, new LinearLayout.LayoutParams(0, dp(34), 1f));
        }
        return s;
    }

    // ---------------------------------------------------------------- backgrounds

    Drawable pressable() { return shapes.pressable(); }
    Drawable card(int radiusDp, boolean tappable) { return shapes.card(tappable); }
    GradientDrawable stroke(int fill, int radiusDp, int strokeColor) { return shapes.stroke(fill, strokeColor); }
    Drawable solid(int color, int radiusDp) { return new ColorDrawable(color); }
    Drawable listRow(int insetLeftDp) { return shapes.listRow(dp(insetLeftDp)); }

    final class Bar extends View {
        private final float f;
        private final int h;
        private final Paint p = new Paint();

        Bar(Context c, float fraction, int heightPx) {
            super(c);
            f = Math.max(0f, Math.min(1f, fraction));
            h = Math.max(2, heightPx);
        }

        @Override protected void onMeasure(int w, int hs) {
            setMeasuredDimension(MeasureSpec.getSize(w), h);
        }

        @Override protected void onDraw(Canvas c) {
            p.setColor(PAPER);
            c.drawRect(0, 0, getWidth(), h, p);
            p.setColor(LINE);
            c.drawRect(0, h - folio.hairline, getWidth(), h, p);
            p.setColor(folio.palette.accent);
            c.drawRect(0, 0, Math.round(getWidth() * f), h, p);
        }
    }
}

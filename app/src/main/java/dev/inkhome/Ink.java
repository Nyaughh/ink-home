package dev.inkhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Ink Home's design system: quiet, Linear-style UI tuned for e-ink.
 * Hairlines instead of heavy rules, grey secondary text, rounded bordered cards,
 * medium-weight titles, and nothing that animates.
 */
final class Ink {

    static final int TEXT = 0xFF111111;
    static final int TEXT2 = 0xFF555555;
    static final int TEXT3 = 0xFF8A8A8A;
    static final int LINE = 0xFFC9C9C9;
    static final int FILL = 0xFFEDEDED;
    static final int PRESSED = 0xFFE0E0E0;

    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    static final Typeface SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    final Context c;
    /** Text scale (the user's text size times the interface size). */
    final float scale;
    /** The user's text size alone, used for layout decisions. */
    final float textOnly;
    private final float density;

    Ink(Context c, float textScale, float uiScale) {
        this.c = c;
        this.textOnly = textScale;
        this.scale = textScale * uiScale;
        this.density = c.getResources().getDisplayMetrics().density * uiScale;
    }

    int dp(float v) { return Math.round(v * density); }

    // ---------------------------------------------------------------- type

    TextView text(CharSequence s, float sp, Typeface face, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * scale);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        return t;
    }

    TextView body(CharSequence s) { return text(s, 16, REGULAR, TEXT); }
    TextView medium(CharSequence s, float sp) { return text(s, sp, MEDIUM, TEXT); }
    TextView muted(CharSequence s, float sp) { return text(s, sp, REGULAR, TEXT2); }
    TextView faint(CharSequence s, float sp) { return text(s, sp, REGULAR, TEXT3); }

    /** Page title, e.g. "Library". */
    TextView title(CharSequence s) {
        TextView t = text(s, 24, MEDIUM, TEXT);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    /** Small grey group heading, e.g. "Appearance". */
    TextView heading(CharSequence s) {
        TextView t = text(s, 13, MEDIUM, TEXT2);
        t.setPadding(dp(2), 0, 0, dp(8));
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

    /** Hairline divider. Drawn anti-aliased across two pixels: Boox darkens crisp 1px grey lines to black. */
    View hair() {
        View v = new View(c);
        v.setBackground(new Hair(Color.TRANSPARENT, 0));
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
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

    ImageView icon(String name, int sizeDp) { return icon(new Icons(name), sizeDp); }

    ImageView icon(Drawable d, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    /** Square bordered icon button, like Linear's toolbar buttons. */
    ImageView iconButton(String name, Runnable click) {
        ImageView iv = icon(name, 36);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        iv.setBackground(card(8, true));
        iv.setOnClickListener(v -> click.run());
        return iv;
    }

    ImageView cover(Bitmap b) {
        ImageView iv = new ImageView(c);
        iv.setImageBitmap(b);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setPadding(1, 1, 1, 1);
        iv.setBackgroundColor(0xFF9A9A9A);
        return iv;
    }

    View progress(float fraction, int heightDp) {
        return new Bar(c, fraction, dp(heightDp));
    }

    /** Small rounded label, e.g. "Sort: Recent". */
    TextView pill(String s, boolean on) {
        TextView t = text(s, 14, MEDIUM, on ? Color.WHITE : TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(on ? solid(TEXT, 7) : card(7, true));
        return t;
    }

    /** Segmented control: a bordered strip with the active segment filled grey. */
    LinearLayout segmented(String[] labels, int active, java.util.function.IntConsumer pick) {
        LinearLayout s = row();
        s.setPadding(dp(3), dp(3), dp(3), dp(3));
        s.setBackground(stroke(Color.WHITE, 9, LINE));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == active;
            TextView t = text(labels[i], 14, on ? MEDIUM : REGULAR, on ? TEXT : TEXT2);
            t.setGravity(Gravity.CENTER);
            t.setBackground(on ? stroke(FILL, 7, 0xFFB5B5B5) : pressable());
            t.setOnClickListener(v -> pick.accept(idx));
            s.addView(t, new LinearLayout.LayoutParams(0, dp(34), 1f));
        }
        return s;
    }

    // ---------------------------------------------------------------- backgrounds

    Drawable pressable() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(PRESSED));
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    /** Rounded card with a hairline border; turns grey while pressed if tappable. */
    Drawable card(int radiusDp, boolean tappable) {
        if (!tappable) return stroke(Color.WHITE, radiusDp, LINE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, stroke(PRESSED, radiusDp, LINE));
        s.addState(new int[]{}, stroke(Color.WHITE, radiusDp, LINE));
        return s;
    }

    GradientDrawable stroke(int fill, int radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(Math.max(1, dp(1)), strokeColor);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    Drawable solid(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Row with a hairline underneath (inset on the left like Linear's lists). */
    Drawable listRow(int insetLeftDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, underlined(PRESSED, insetLeftDp));
        s.addState(new int[]{}, underlined(Color.TRANSPARENT, insetLeftDp));
        return s;
    }

    private Drawable underlined(int fill, int insetLeftDp) {
        return new Hair(fill, dp(insetLeftDp));
    }

    /** Fill (may be transparent) with an anti-aliased hairline along the bottom edge. */
    static final class Hair extends Drawable {
        private final int fill, inset;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Hair(int fill, int insetPx) {
            this.fill = fill;
            this.inset = insetPx;
        }

        @Override public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            if (Color.alpha(fill) > 0) {
                p.setColor(fill);
                c.drawRect(b, p);
            }
            p.setColor(LINE);
            // Straddle two pixel rows so the line stays a light grey on e-ink.
            c.drawRect(b.left + inset, b.bottom - 1.5f, b.right, b.bottom - 0.5f, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    // ---------------------------------------------------------------- custom views

    /** Thin rounded progress bar: light grey track, black fill. */
    static final class Bar extends View {
        private final float f;
        private final int h;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Bar(Context c, float fraction, int heightPx) {
            super(c);
            f = Math.max(0f, Math.min(1f, fraction));
            h = Math.max(2, heightPx);
        }

        @Override protected void onMeasure(int w, int hs) {
            setMeasuredDimension(MeasureSpec.getSize(w), h);
        }

        @Override protected void onDraw(Canvas c) {
            float r = h / 2f;
            p.setColor(0xFFD6D6D6);
            c.drawRoundRect(0, 0, getWidth(), h, r, r, p);
            if (f > 0) {
                p.setColor(TEXT);
                c.drawRoundRect(0, 0, Math.max(h, getWidth() * f), h, r, r, p);
            }
        }
    }
}

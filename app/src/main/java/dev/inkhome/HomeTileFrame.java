package dev.inkhome;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

/** Supplies the actual available pixels to every widget, including gallery previews. */
final class HomeTileFrame extends FrameLayout {
    interface Content {
        View build(int width, int height);
    }

    private final Content factory;
    private final boolean responsive;
    private final int preferredInset;
    private int builtWidth = -1, builtHeight = -1;
    private int naturalWidth = -1, naturalHeight;

    HomeTileFrame(MainActivity host, HomeLayout.Item item, boolean responsive, Content factory) {
        super(host);
        this.factory = factory;
        this.responsive = responsive;
        if (HomeComponent.hasBorder(host, item)) {
            Ink ink = host.ink;
            GradientDrawable border = new GradientDrawable();
            border.setColor(ink.PAPER);
            border.setStroke(ink.folio.rule, ink.folio.palette.strong);
            setBackground(border);
            boolean compact = Boolean.parseBoolean(item.settings.get("compact"));
            preferredInset = ink.folio.rule + ink.dp(compact ? 4 : 8);
        } else preferredInset = 0;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int shorterEdge = MeasureSpec.getSize(widthSpec);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            shorterEdge = Math.min(shorterEdge, MeasureSpec.getSize(heightSpec));
        }
        int inset = Math.min(preferredInset, shorterEdge / 8);
        if (inset != getPaddingTop()) setPadding(inset, inset, inset, inset);
        int width = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        boolean natural = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED;
        int availableHeight = Math.max(0, MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom());

        // HomeGrid first asks for the stock preset's natural height, then assigns exact
        // bounds to every cell. Avoid rebuilding its content on each measurement pass.
        if (natural && naturalWidth == width && builtHeight != Integer.MAX_VALUE) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), naturalHeight);
            return;
        }
        boolean fitsNaturally = naturalWidth == width && naturalHeight <= MeasureSpec.getSize(heightSpec);
        int height = natural || fitsNaturally ? Integer.MAX_VALUE : availableHeight;
        if (getChildCount() == 0 || responsive && (builtWidth != width || builtHeight != height)) {
            removeAllViewsInLayout();
            View child = factory.build(width, height);
            addViewInLayout(child, -1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT), true);
            builtWidth = width;
            builtHeight = height;
        }
        super.onMeasure(widthSpec, heightSpec);
        if (natural) {
            naturalWidth = width;
            naturalHeight = getMeasuredHeight();
        }
    }
}

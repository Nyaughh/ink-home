package dev.inkhome.folio.core;

import static dev.inkhome.folio.core.Tokens.Window.*;

public enum WindowClass {
    COMPACT(COMPACT_MARGIN, COMPACT_GUTTER), MEDIUM(MEDIUM_MARGIN, MEDIUM_GUTTER),
    EXPANDED(EXPANDED_MARGIN, EXPANDED_GUTTER);

    public final int margin, gutter;

    WindowClass(int margin, int gutter) {
        this.margin = margin;
        this.gutter = gutter;
    }

    public static WindowClass fromWidth(float widthU) {
        if (widthU >= EXPANDED_MIN) return EXPANDED;
        if (widthU >= MEDIUM_MIN) return MEDIUM;
        return COMPACT;
    }

    public int columns(float widthU, int minCell) {
        return Math.max(1, (int) Math.floor((widthU - 2 * margin + gutter) / (minCell + gutter)));
    }

    public static int hairline(float density) {
        return Math.max(Tokens.Stroke.HAIRLINE_MIN, (int) Math.floor(density * Tokens.Stroke.HAIRLINE_DENSITY));
    }
}

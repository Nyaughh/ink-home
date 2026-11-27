package dev.inkhome.folio.core;

import dev.inkhome.folio.core.Tokens.*;

public final class Palette {
    public final int paper, ink, ink2, ink3, rule, strong, fill, inverse, onInverse, accent, onAccent;

    private Palette(int paper, int ink, int ink2, int ink3, int rule, int strong, int fill,
                    int inverse, int onInverse, int accent, int onAccent) {
        this.paper = paper;
        this.ink = ink;
        this.ink2 = ink2;
        this.ink3 = ink3;
        this.rule = rule;
        this.strong = strong;
        this.fill = fill;
        this.inverse = inverse;
        this.onInverse = onInverse;
        this.accent = accent;
        this.onAccent = onAccent;
    }

    private static final Palette MONO = new Palette(
            Mono.PAPER, Mono.INK, Mono.INK2, Mono.INK3, Mono.RULE,
            Mono.STRONG, Mono.FILL, Mono.INVERSE, Mono.ON_INVERSE, Mono.ACCENT, Mono.ON_ACCENT);
    private static final Palette COLOR = new Palette(
            ColorEink.PAPER, ColorEink.INK, ColorEink.INK2, ColorEink.INK3, ColorEink.RULE,
            ColorEink.STRONG, ColorEink.FILL, ColorEink.INVERSE, ColorEink.ON_INVERSE, ColorEink.ACCENT, ColorEink.ON_ACCENT);
    private static final Palette PAPER = new Palette(
            Paper.PAPER, Paper.INK, Paper.INK2, Paper.INK3, Paper.RULE,
            Paper.STRONG, Paper.FILL, Paper.INVERSE, Paper.ON_INVERSE, Paper.ACCENT, Paper.ON_ACCENT);
    private static final Palette NIGHT = new Palette(
            Night.PAPER, Night.INK, Night.INK2, Night.INK3, Night.RULE,
            Night.STRONG, Night.FILL, Night.INVERSE, Night.ON_INVERSE, Night.ACCENT, Night.ON_ACCENT);
    public static Palette of(DisplayClass display, boolean night) {
        if (display == DisplayClass.EMISSIVE) return night ? NIGHT : PAPER;
        Palette day = display == DisplayClass.COLOR_EINK ? COLOR : MONO;
        if (!night) return day;
        return new Palette(day.ink, day.paper, day.paper, day.paper, day.paper, day.paper,
                day.ink, day.paper, day.ink, display == DisplayClass.COLOR_EINK ? day.accent : day.paper,
                display == DisplayClass.COLOR_EINK ? day.onAccent : day.ink);
    }
}

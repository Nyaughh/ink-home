package dev.inkhome.folio.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoundationTest {
    @Test public void c69UsesSinglePixelHairlinesAndCompactMargins() {
        assertEquals(1, WindowClass.hairline(1.325f));
        assertEquals(2, WindowClass.hairline(3f));
        WindowClass window = WindowClass.fromWidth(572 / 1.15f);
        assertEquals(WindowClass.COMPACT, window);
        assertEquals(4, window.columns(497, Tokens.Window.MIN_BOOK_CELL));
        assertEquals(1, window.columns(25, Tokens.Window.MIN_BOOK_CELL));
    }

    @Test public void classificationsChangeAtWindowEdges() {
        assertEquals(WindowClass.COMPACT, WindowClass.fromWidth(599.9f));
        assertEquals(WindowClass.MEDIUM, WindowClass.fromWidth(600));
        assertEquals(WindowClass.MEDIUM, WindowClass.fromWidth(839.9f));
        assertEquals(WindowClass.EXPANDED, WindowClass.fromWidth(840));
        assertEquals(TypeScale.TITLE, TypeScale.TITLE.forWindow(WindowClass.MEDIUM));
        assertEquals(TypeScale.TITLE_EXPANDED, TypeScale.TITLE.forWindow(WindowClass.EXPANDED));
    }

    @Test public void roleWeightsCompensateForInk() {
        assertEquals(500, TypeScale.BODY.weight(DisplayClass.MONO_EINK));
        assertEquals(400, TypeScale.BODY.weight(DisplayClass.EMISSIVE));
        assertEquals(700, TypeScale.TITLE.weight(DisplayClass.EMISSIVE));
        assertEquals(500, TypeScale.APP_NAME.weight(DisplayClass.COLOR_EINK));
        assertEquals(1.15f, DisplayClass.MONO_EINK.defaultInterfaceScale(), 0.0001f);
        assertEquals(1f, DisplayClass.EMISSIVE.defaultInterfaceScale(), 0.0001f);
    }

    @Test public void monoUsesTwoTonesIncludingInNightMode() {
        for (boolean night : new boolean[]{false, true}) {
            Palette p = Palette.of(DisplayClass.MONO_EINK, night);
            assertEquals(night ? 0xff000000 : 0xffffffff, p.paper);
            assertEquals(~p.paper | 0xff000000, p.ink);
            for (int role : new int[]{p.ink2, p.ink3, p.rule, p.strong, p.accent, p.inverse}) {
                assertEquals(p.ink, role);
            }
            assertEquals(p.paper, p.fill);
            assertEquals(p.paper, p.onAccent);
            assertEquals(p.paper, p.onInverse);
        }
    }

    @Test public void displayDetectionDistinguishesReadersFromOtherDevicesByTheSameMaker() {
        assertEquals(DisplayClass.MONO_EINK, DisplayClass.detect("Onyx", "C69", true));
        assertEquals(DisplayClass.COLOR_EINK, DisplayClass.detect("Onyx", "Note Air4 C", false));
        assertEquals(DisplayClass.MONO_EINK, DisplayClass.detect("Hisense", "HLTE556N", false));
        assertEquals(DisplayClass.COLOR_EINK, DisplayClass.detect("Hisense", "A5 Pro CC", false));
        assertEquals(DisplayClass.EMISSIVE, DisplayClass.detect("Hisense", "Infinity H40", false));
        assertEquals(DisplayClass.MONO_EINK, DisplayClass.detect("Barnes & Noble", "BNRV1000", false));
        assertEquals(DisplayClass.EMISSIVE, DisplayClass.detect("Barnes & Noble", "Nook Tablet", false));
        assertEquals(DisplayClass.MONO_EINK, DisplayClass.detect("Xiaomi", "InkPalm 5", false));
        assertEquals(DisplayClass.EMISSIVE, DisplayClass.detect("Xiaomi", "Redmi Note", false));
        assertEquals(DisplayClass.MONO_EINK, DisplayClass.detect("Unknown", "Unknown", true));
    }

    @Test public void alternativeAccentsPreserveInkAndPaper() {
        for (boolean night : new boolean[]{false, true}) {
            for (DisplayClass display : DisplayClass.values()) {
                Palette base = Palette.of(display, night);
                for (int i = 1; i <= 3; i++) {
                    Palette p = Palette.of(display, night, i);
                    assertEquals(base.ink, p.ink);
                    assertEquals(base.rule, p.rule);
                    assertEquals(base.paper, p.paper);
                    if (display == DisplayClass.MONO_EINK) assertEquals(base.ink, p.accent);
                    else assertNotEquals(base.accent, p.accent);
                }
            }
        }
        assertEquals(Tokens.Mono.INK, Palette.of(DisplayClass.COLOR_EINK, false, 3).onAccent);
    }

    @Test public void accentIsReservedForColourSurfaces() {
        Palette color = Palette.of(DisplayClass.COLOR_EINK, false);
        assertEquals(Tokens.ColorEink.ACCENT, color.accent);
        assertEquals(color.ink, color.rule);
        assertEquals(Tokens.Paper.INK2, Palette.of(DisplayClass.EMISSIVE, false).ink2);
        assertEquals(Tokens.Night.INK2, Palette.of(DisplayClass.EMISSIVE, true).ink2);
    }
}

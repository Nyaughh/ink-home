package dev.inkhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeMosaicTest {
    private static HomeLayout.Tile add(HomeLayout layout, int x, int y, int w, int h) {
        HomeLayout.Tile tile = new HomeLayout.Tile("clock", 0, x, y, w, h, -1);
        layout.tiles.add(tile);
        return tile;
    }

    private void assertFilled(HomeLayout layout, int page) {
        int covered = 0;
        for (HomeLayout.Tile tile : layout.tiles) if (tile.page == page) {
            assertTrue(layout.fits(tile, page, tile.x, tile.y, tile.w, tile.h));
            covered += tile.w * tile.h;
        }
        assertEquals(HomeLayout.COLS * HomeLayout.ROWS, covered);
    }

    @Test public void fillsFragmentedPagesOfEveryDensity() {
        for (int count = 1; count <= 24; count++) {
            HomeLayout layout = new HomeLayout();
            for (int i = 0; i < count; i++) {
                int pos = (i * 7) % 24;
                add(layout, pos % 4, pos / 4, 1, 1);
            }
            HomeMosaic.tidy(layout, 0);
            assertEquals(count, layout.tiles.size());
            assertFilled(layout, 0);
        }
    }

    @Test public void retainsMixedSizesAndWidgetIdentityWhereTheyFit() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Tile hero = add(layout, 0, 0, 4, 2);
        HomeLayout.Tile widget = add(layout, 1, 3, 2, 2);
        widget.kind = "widget";
        widget.widgetId = 44;
        HomeLayout.Tile clock = add(layout, 3, 5, 1, 1);
        HomeMosaic.tidy(layout, 0);
        assertFilled(layout, 0);
        assertEquals(3, layout.tiles.size());
        assertSame(widget, layout.tiles.get(1));
        assertEquals(44, widget.widgetId);
        assertTrue(hero.w == 4 && hero.h >= 2);
        assertTrue(widget.w >= 2 && widget.h >= 2);
        assertTrue(clock.w >= 1 && clock.h >= 1);
    }

    @Test public void fullAndUnselectedPagesKeepExactPlacement() {
        HomeLayout layout = new HomeLayout();
        layout.pages = 2;
        HomeLayout.Tile right = add(layout, 2, 0, 2, 6);
        HomeLayout.Tile left = add(layout, 0, 0, 2, 6);
        HomeLayout.Tile other = add(layout, 1, 4, 1, 1);
        other.page = 1;
        HomeMosaic.tidy(layout, 0);
        assertFilled(layout, 0);
        assertEquals(2, right.x);
        assertEquals(0, left.x);
        assertEquals(1, other.x);
        assertEquals(4, other.y);
        assertEquals(1, other.w * other.h);
    }

    @Test public void splitsEveryMultiCellSlotWithoutChangingCoveredArea() {
        for (int w = 1; w <= 4; w++) for (int h = 1; h <= 6; h++) {
            if (w * h == 1) continue;
            HomeLayout layout = new HomeLayout();
            HomeLayout.Tile original = add(layout, 4 - w, 6 - h, w, h);
            HomeLayout.Tile inserted = HomeMosaic.split(original, "widget", 21);
            layout.tiles.add(inserted);
            assertEquals(21, inserted.widgetId);
            assertEquals("widget", inserted.kind);
            assertEquals(w * h, original.w * original.h + inserted.w * inserted.h);
            assertTrue(layout.fits(original, 0, original.x, original.y, original.w, original.h));
            assertTrue(layout.fits(inserted, 0, inserted.x, inserted.y, inserted.w, inserted.h));
            assertEquals(4 - w, original.x);
            assertEquals(6 - h, original.y);
            assertEquals(4, inserted.x + inserted.w);
            assertEquals(6, inserted.y + inserted.h);
        }
    }

    @Test public void swapsSlotsAcrossPagesKeepingWidgetIdentity() {
        HomeLayout layout = new HomeLayout();
        layout.pages = 2;
        HomeLayout.Tile a = add(layout, 0, 1, 1, 3);
        a.kind = "widget";
        a.widgetId = 17;
        HomeLayout.Tile b = add(layout, 1, 0, 3, 2);
        b.page = 1;
        HomeMosaic.swap(a, b);
        assertArrayEquals(new int[]{1, 1, 0, 3, 2}, new int[]{a.page, a.x, a.y, a.w, a.h});
        assertArrayEquals(new int[]{0, 0, 1, 1, 3}, new int[]{b.page, b.x, b.y, b.w, b.h});
        assertEquals("widget", a.kind);
        assertEquals(17, a.widgetId);
        assertEquals("clock", b.kind);
    }

    @Test public void emptyPageStaysEmpty() {
        HomeLayout layout = new HomeLayout();
        HomeMosaic.tidy(layout, 0);
        assertTrue(layout.tiles.isEmpty());
    }
}

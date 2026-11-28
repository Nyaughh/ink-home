package dev.inkhome;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HomeReflowTest {
    private static HomeLayout.Item add(HomeLayout layout, int x, int y, int w, int h) {
        HomeLayout.Item item = new HomeLayout.Item("clock", "default", x, y, w, h);
        layout.items.add(item);
        return item;
    }

    private static List<String> positions(HomeLayout layout) {
        List<String> result = new ArrayList<>();
        for (HomeLayout.Item item : layout.items) {
            result.add(item.key + ":" + item.x + "," + item.y + "," + item.w + "," + item.h);
        }
        return result;
    }

    private static void valid(HomeLayout layout) {
        for (HomeLayout.Item item : layout.items)
            assertTrue(item.key, layout.fits(item, item.x, item.y, item.w, item.h));
    }

    @Test public void upAndDownSwapUnequalBandsWithoutMovingThirdWidget() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item top = add(layout, 0, 0, 4, 6);
        HomeLayout.Item middle = add(layout, 0, 6, 4, 7);
        HomeLayout.Item bottom = add(layout, 0, 13, 4, 3);
        top.settings.put("borders", "true");
        List<String> before = positions(layout);
        HomeLayout.Placement move = layout.move(middle, 0, -1);
        assertNotNull(move);
        assertEquals(before, positions(layout));
        move.apply();
        assertEquals(0, middle.y);
        assertEquals(7, top.y);
        assertEquals(13, bottom.y);
        assertEquals("true", top.settings.get("borders"));
        valid(layout);
        layout.move(middle, 0, 1).apply();
        assertEquals(before, positions(layout));
    }

    @Test public void wideNeighbourCanSwapTwoNarrowerWidgets() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item left = add(layout, 0, 1, 1, 2);
        HomeLayout.Item right = add(layout, 1, 1, 2, 2);
        HomeLayout.Item block = add(layout, 0, 3, 3, 4);
        layout.move(block, 0, -1).apply();
        assertEquals(1, block.y);
        assertEquals(5, left.y);
        assertEquals(5, right.y);
        valid(layout);
    }

    @Test public void horizontalSwapUsesOppositeDirectionAtFullCapacity() {
        HomeLayout layout = new HomeLayout();
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 4; x++) add(layout, x, y, 1, 1);
        HomeLayout.Item last = layout.items.get(63), previous = layout.items.get(62);
        List<String> original = positions(layout);
        layout.move(last, -1, 0).apply();
        assertEquals(2, last.x);
        assertEquals(3, previous.x);
        valid(layout);
        layout.move(last, 1, 0).apply();
        assertEquals(original, positions(layout));
    }

    @Test public void largerResizeReflowsThirdPartyToMakeRoomForDisplacedWidget() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item selected = add(layout, 0, 0, 1, 1);
        HomeLayout.Item displaced = add(layout, 1, 0, 2, 1);
        add(layout, 3, 0, 1, 1);
        add(layout, 1, 1, 1, 1);
        add(layout, 3, 1, 1, 1);
        add(layout, 0, 2, 4, 14);
        // Only isolated single cells remain in the first two rows with third parties pinned.
        HomeLayout copy = layout.copy();
        layout.resize(selected, 2, 1).apply();
        copy.resize(copy.items.get(0), 2, 1).apply();
        assertEquals(2, selected.w);
        assertEquals(2, displaced.w);
        valid(layout);
        assertEquals(positions(layout), positions(copy));
    }

    @Test public void movePastAnOverhangingNeighbourLeavesUnrelatedWidgetsInPlace() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item neighbour = add(layout, 0, 0, 2, 2);
        HomeLayout.Item selected = add(layout, 1, 2, 1, 1);
        HomeLayout.Item unrelated = add(layout, 0, 2, 1, 1);
        // A direct swap would land the neighbour on the unrelated tile.
        layout.move(selected, 0, -1).apply();
        assertEquals(1, selected.y);
        assertEquals(0, unrelated.x);
        assertEquals(2, unrelated.y);
        assertEquals(2, neighbour.w);
        assertEquals(2, neighbour.h);
        valid(layout);
    }

    @Test public void resizeAtEdgeKeepsRequestedSizeAndSalvagesAnchor() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item selected = add(layout, 3, 15, 1, 1);
        layout.resize(selected, 2, 3).apply();
        assertArrayEquals(new int[]{2, 13, 2, 3},
                new int[]{selected.x, selected.y, selected.w, selected.h});
        valid(layout);
    }

    @Test public void impossibleEditsPreserveEveryInstanceExactly() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item selected = add(layout, 0, 0, 3, 16);
        add(layout, 3, 0, 1, 16);
        List<String> original = positions(layout);
        assertNull(layout.resize(selected, 4, 16));
        assertNull(layout.resize(selected, 5, 1));
        assertNull(layout.move(selected, 0, -1));
        assertNull(layout.move(selected, 2, 0));
        assertNull(layout.move(selected.copy(), 1, 0));
        assertEquals(original, positions(layout));
        // Enough free area alone cannot make a 2x16 neighbour fit beside a 3x1 tile.
        HomeLayout fragmented = new HomeLayout();
        HomeLayout.Item narrow = add(fragmented, 0, 0, 1, 1);
        add(fragmented, 1, 0, 2, 16);
        assertNull(fragmented.resize(narrow, 3, 1));
        valid(fragmented);
    }

    @Test public void longDistanceDropSwapsAcrossAll96Cells() {
        HomeLayout layout = new HomeLayout();
        assertTrue(layout.changeGrid(6, 16));
        for (int y = 0; y < layout.rows; y++)
            for (int x = 0; x < layout.cols; x++) add(layout, x, y, 1, 1);
        HomeLayout.Item first = layout.items.get(0), last = layout.items.get(95);
        List<String> before = positions(layout);
        assertNull(layout.moveTo(first, 6, 0));
        assertNull(layout.moveTo(first.copy(), 2, 2));
        HomeLayout.Placement placement = layout.moveTo(first, 5, 15);
        assertNotNull(placement);
        assertEquals(before, positions(layout));
        placement.apply();
        assertEquals(5, first.x);
        assertEquals(15, first.y);
        assertEquals(0, last.x);
        assertEquals(0, last.y);
        valid(layout);
    }

    @Test public void changingGridKeepsInstancesAndMapsFullWidthBands() {
        HomeLayout layout = new HomeLayout();
        HomeLayout.Item first = add(layout, 0, 0, 4, 8);
        HomeLayout.Item second = add(layout, 0, 8, 4, 8);
        first.settings.put("count", "3");
        layout.hiddenWidgetIds.add(72);
        assertTrue(layout.changeGrid(3, 4));
        assertSame(first, layout.items.get(0));
        assertSame(second, layout.items.get(1));
        assertEquals(3, first.w);
        assertEquals(2, first.h);
        assertEquals(2, second.y);
        assertEquals("3", first.settings.get("count"));
        assertEquals(List.of(72), layout.hiddenWidgetIds);
        assertEquals(3, layout.copy().cols);
        assertEquals(4, layout.copy().rows);
        valid(layout);
    }

    @Test public void invalidOrOvercrowdedGridChangeLeavesCanvasUntouched() {
        HomeLayout layout = new HomeLayout();
        for (int i = 0; i < 13; i++) add(layout, i % 4, i / 4, 1, 1);
        List<String> before = positions(layout);
        assertFalse(layout.changeGrid(3, 4));
        assertFalse(layout.changeGrid(7, 16));
        assertFalse(layout.changeGrid(4, 17));
        assertEquals(before, positions(layout));
        assertEquals(4, layout.cols);
        assertEquals(16, layout.rows);
    }
}

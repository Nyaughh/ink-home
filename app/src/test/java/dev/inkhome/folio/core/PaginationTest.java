package dev.inkhome.folio.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class PaginationTest {
    private static Pagination.Item row(int top, int bottom) {
        return new Pagination.Item(top, bottom, -1, -1, false);
    }

    @Test public void emptyAndSinglePagesReclaimTheFolio() {
        assertEquals(1, Pagination.uniform(0, 1, 30, 100, 40).count());
        assertEquals(1, Pagination.uniform(1, 1, 100, 100, 40).count());
        assertEquals(1, Pagination.uniform(3, 1, 30, 100, 40).count());
        assertEquals(1, Pagination.flow(Collections.emptyList(), 0, 100, 40).count());
        assertEquals(1, Pagination.flow(Collections.singletonList(row(0, 100)), 100, 100, 40).count());
    }

    @Test public void gridFitsWholeRowsWithStableFolio() {
        Pagination pages = Pagination.uniform(13, 3, 25, 100, 25);
        Pagination visible = Pagination.uniform(13, 3, 25, 75, 0);
        assertEquals(visible.count(), pages.count());
        assertEquals(2, pages.count());
        assertEquals(9, pages.end(0));
        assertEquals(9, pages.start(1));
        assertEquals(13, pages.end(1));
    }

    @Test public void measuredRowsNeverStraddlePagesEvenBeyondSixtyPages() {
        List<Pagination.Item> items = new ArrayList<>();
        int top = 0;
        for (int i = 0; i < 300; i++) {
            int height = 19 + (i % 3) * 12;
            items.add(row(top, top + height));
            top += height;
        }
        Pagination pages = Pagination.flow(items, top, 100, 20);
        Pagination visible = Pagination.flow(items, top, 80, 0);
        assertTrue(pages.count() > 60);
        assertEquals(visible.count(), pages.count());
        for (Pagination.Item item : items) {
            int p = pages.pageAt(item.top);
            assertTrue(item.bottom <= pages.end(p));
            assertTrue(pages.end(p) - pages.start(p) <= 80);
        }
    }

    @Test public void oversizedItemHasItsOwnScrollablePage() {
        List<Pagination.Item> items = Arrays.asList(row(0, 30), row(30, 210), row(210, 240));
        Pagination pages = Pagination.flow(items, 240, 100, 20);
        assertEquals(3, pages.count());
        assertEquals(30, pages.start(1));
        assertEquals(210, pages.end(1));
    }

    @Test public void groupThatFitsMovesAsOne() {
        List<Pagination.Item> items = Arrays.asList(row(0, 45),
                new Pagination.Item(55, 75, 45, 95, true),
                new Pagination.Item(75, 95, 45, 95, false), row(95, 130));
        Pagination pages = Pagination.flow(items, 130, 100, 20);
        assertEquals(45, pages.start(1));
        assertEquals(pages.pageAt(45), pages.pageAt(94));
    }

    @Test public void longGroupMovesHeadingWithFirstRow() {
        List<Pagination.Item> items = Arrays.asList(row(0, 65),
                new Pagination.Item(75, 100, 65, 175, true),
                new Pagination.Item(100, 140, 65, 175, false),
                new Pagination.Item(140, 175, 65, 175, false));
        Pagination pages = Pagination.flow(items, 175, 90, 10);
        assertEquals(65, pages.start(1));
        assertEquals(pages.pageAt(65), pages.pageAt(99));
    }
}

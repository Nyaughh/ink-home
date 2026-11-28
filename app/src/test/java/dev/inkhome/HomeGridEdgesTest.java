package dev.inkhome;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HomeGridEdgesTest {
    private static int weightAcross(List<HomeGridEdges.Strip> strips, int x, int y, boolean vertical) {
        int pixels = 0;
        for (int d = -5; d < 5; d++) {
            int px = vertical ? x + d : x;
            int py = vertical ? y : y + d;
            for (HomeGridEdges.Strip s : strips) {
                if (px >= s.left() && px < s.right() && py >= s.top() && py < s.bottom()) pixels++;
            }
        }
        return pixels;
    }

    @Test public void sharedEdgesAtTJunctionAreOneRuleWideOnBothAxes() {
        HomeGridEdges edges = new HomeGridEdges();
        edges.ring(0, 0, 30, 60, 2, 1, HomeGridEdges.BORDER);
        edges.ring(30, 0, 60, 30, 2, 1, HomeGridEdges.BORDER);
        edges.ring(30, 30, 60, 60, 2, 1, HomeGridEdges.BORDER);
        List<HomeGridEdges.Strip> strips = edges.resolve();
        assertEquals(2, weightAcross(strips, 30, 15, true));
        assertEquals(2, weightAcross(strips, 30, 45, true));
        assertEquals(2, weightAcross(strips, 45, 30, false));
        assertEquals(2, weightAcross(strips, 0, 15, true));
    }

    @Test public void selectionAndPartialSeparatorResolveOnlyTheirCoincidentSpans() {
        HomeGridEdges edges = new HomeGridEdges();
        edges.ring(10, 10, 60, 40, 2, 1, HomeGridEdges.BORDER);
        edges.add(new HomeGridEdges.Edge(false, 40, 0, 100, 1, 2, HomeGridEdges.SEPARATOR));
        edges.ring(30, 40, 60, 80, 3, 3, HomeGridEdges.SELECTION);
        List<HomeGridEdges.Strip> strips = edges.resolve();
        assertEquals(2, weightAcross(strips, 20, 40, false));
        assertEquals(3, weightAcross(strips, 45, 40, false));
        assertEquals(1, weightAcross(strips, 80, 40, false));
    }
}

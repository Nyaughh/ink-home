package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DotPositionTest {
    private static SharedPreferences preferences(Map<String, Object> values) {
        Map<String, Float> pending = new HashMap<>();
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        when(editor.putFloat(anyString(), anyFloat())).thenAnswer(call -> {
            pending.put(call.getArgument(0), call.getArgument(1));
            return editor;
        });
        doAnswer(call -> {
            values.putAll(pending);
            pending.clear();
            return null;
        }).when(editor).apply();
        return mock(SharedPreferences.class, call -> {
            switch (call.getMethod().getName()) {
                case "edit": return editor;
                case "getInt":
                case "getFloat":
                case "getBoolean":
                    return values.getOrDefault(call.getArgument(0), call.getArgument(1));
                default: throw new AssertionError(call.getMethod());
            }
        });
    }

    @Test public void restoresNearestSideAndReleaseHeightForEachRotation() {
        Map<String, Object> stored = new HashMap<>();
        SharedPreferences prefs = preferences(stored);
        new DotPosition(100, 600).save(prefs, 0, 400, 800);
        new DotPosition(600, 100).save(prefs, 1, 800, 400);

        SharedPreferences reopened = preferences(new HashMap<>(stored));
        DotPosition portrait = DotPosition.load(reopened, 0, 400, 800, false);
        DotPosition landscape = DotPosition.load(reopened, 1, 800, 400, false);
        assertEquals(0, portrait.x);
        assertEquals(600, portrait.y);
        assertEquals(800, landscape.x);
        assertEquals(100, landscape.y);
    }

    @Test public void restoresLegacyFreePositionThenRemembersTheNextRelease() {
        SharedPreferences prefs = preferences(new HashMap<>(Map.of("edgeLeft2", true, "edgeHeight2", .25f, "freeX2", .3f, "freeY2", .1f)));
        DotPosition legacy = DotPosition.load(prefs, 2, 400, 882, false);
        assertEquals(0, legacy.x);
        assertEquals(88, legacy.y);

        new DotPosition(300, 500).save(prefs, 2, 400, 882);
        DotPosition moved = DotPosition.load(prefs, 2, 400, 882, false);
        assertEquals(400, moved.x);
        assertEquals(500, moved.y);
    }

    @Test public void oldEdgeOnlyPositionKeepsItsSideAndRelativeHeight() {
        SharedPreferences prefs = preferences(Map.of("edgeLeft3", true, "edgeHeight3", .25f));
        DotPosition saved = DotPosition.load(prefs, 3, 500, 800, false);
        assertEquals(0, saved.x);
        assertEquals(200, saved.y);
    }

    @Test public void newTabStartsHalfwayDownThePreferredEdge() {
        SharedPreferences prefs = preferences(Map.of());
        DotPosition right = DotPosition.load(prefs, 0, 400, 800, false);
        DotPosition left = DotPosition.load(prefs, 0, 400, 800, true);
        assertEquals(400, right.x);
        assertEquals(0, left.x);
        assertEquals(400, right.y);
        assertEquals(400, left.y);
    }

    @Test public void docksPreviouslySavedInteriorPositionsOnLoad() {
        SharedPreferences prefs = preferences(new HashMap<>(Map.of(
                "refreshEdgeX0", .49f, "refreshEdgeY0", .75f,
                "refreshEdgeX1", .51f, "refreshEdgeY1", .25f)));
        DotPosition left = DotPosition.load(prefs, 0, 600, 800, false);
        DotPosition right = DotPosition.load(prefs, 1, 800, 600, true);
        assertEquals(0, left.x);
        assertEquals(600, left.y);
        assertEquals(800, right.x);
        assertEquals(150, right.y);
        assertEquals(600, DotPosition.nearestSide(300, 600));
    }

    @Test public void onlyPreviouslyHiddenAppsRemainHidden() {
        Context context = mock(Context.class);
        SharedPreferences saved = preferences(Map.of(
                "dotTucked", false, "dotPolicy:shown", 0, "dotPolicy:tucked", 1, "dotPolicy:hidden", 2));
        when(context.getSharedPreferences("inkhome", Context.MODE_PRIVATE)).thenReturn(saved);
        assertFalse(ShellService.dotHidden(context, "new"));
        assertFalse(ShellService.dotHidden(context, "shown"));
        assertFalse(ShellService.dotHidden(context, "tucked"));
        assertTrue(ShellService.dotHidden(context, "hidden"));
    }

    @Test public void restoresInteriorHeightAfterResize() {
        float saved = DotPosition.fraction(314, 800);
        assertEquals(314, DotPosition.pixel(saved, 800));
        assertEquals(157, DotPosition.pixel(saved, 400));
    }

    @Test public void limitsOffsetsAtBothEdges() {
        assertEquals(0, DotPosition.clamp(-20, 400));
        assertEquals(400, DotPosition.clamp(520, 400));
        assertEquals(400, DotPosition.pixel(2f, 400));
        assertEquals(0, DotPosition.pixel(-1f, 400));
    }

    @Test public void handlesDotFillingAvailableSpace() {
        assertEquals(0f, DotPosition.fraction(30, 0), 0f);
        assertEquals(0, DotPosition.pixel(.6f, 0));
    }
}

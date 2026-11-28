package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Reads raw v1.1.2 preferences without invoking any current-version writers. */
public class UpgradeDataTest {
    private static final String BOOK = "/storage/ABCD-1234/Books/One | Two.epub";
    private static final String OTHER = "/storage/emulated/0/Books/Other.pdf";

    private static Context legacyContext(Map<String, Object> values) {
        SharedPreferences prefs = mock(SharedPreferences.class, call -> {
            if (call.getMethod().getName().equals("getAll")) return values;
            String key = call.getArgument(0);
            switch (call.getMethod().getName()) {
                case "contains": return values.containsKey(key);
                case "getInt":
                case "getLong":
                case "getBoolean":
                case "getFloat":
                case "getString":
                    return values.getOrDefault(key, call.getArgument(1));
                default: throw new AssertionError(call.getMethod());
            }
        });
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences("inkhome", Context.MODE_PRIVATE)).thenReturn(prefs);
        return context;
    }

    @Test public void bordersFollowDefaultUnlessAnInstanceHasAnExplicitChoice() {
        MainActivity activity = mock(MainActivity.class);
        HomeLayout.Item tile = new HomeLayout.Item("clock", "default", 0, 0, 1, 1);
        activity.store = new Store(legacyContext(Map.of()));
        assertFalse(HomeComponent.hasBorder(activity, tile));
        tile.settings.put("borders", "true");
        assertTrue(HomeComponent.hasBorder(activity, tile));
        activity.store = new Store(legacyContext(Map.of("widgetBorders", true)));
        tile.settings.put("borders", "false");
        assertFalse(HomeComponent.hasBorder(activity, tile));
        tile.settings.remove("borders");
        assertTrue(HomeComponent.hasBorder(activity, tile));
    }

    @Test public void locksPortraitOnFreshInstallAndHonorsExplicitOptOut() {
        assertTrue(new Store(legacyContext(Map.of())).portraitLocked());
        assertFalse(new Store(legacyContext(Map.of("portraitLocked", false))).portraitLocked());
    }

    @Test public void unchangedControlsOnUpgradeRespectExistingKeyOrderAndTouchZones() {
        for (boolean reversed : new boolean[] {false, true}) {
            Controls controls = new Store(legacyContext(Map.of("pageRev", reversed))).controls();
            Controls.Action next = reversed ? Controls.Action.PREV_PAGE : Controls.Action.NEXT_PAGE;
            Controls.Action prev = reversed ? Controls.Action.NEXT_PAGE : Controls.Action.PREV_PAGE;
            for (Controls.Input input : Arrays.asList(Controls.Input.PAGE_DOWN,
                    Controls.Input.VOLUME_DOWN, Controls.Input.DPAD_RIGHT)) {
                assertEquals(next, controls.action(Controls.key(input.keyCode)));
                assertFalse(controls.overridden(input));
            }
            for (Controls.Input input : Arrays.asList(Controls.Input.PAGE_UP,
                    Controls.Input.VOLUME_UP, Controls.Input.DPAD_LEFT)) {
                assertEquals(prev, controls.action(Controls.key(input.keyCode)));
                assertFalse(controls.overridden(input));
            }
            assertEquals(Controls.Layout.CLASSIC, controls.layout());
            assertEquals(Controls.Action.PREV_PAGE, controls.action(controls.tap(.1f, .1f)));
            assertEquals(Controls.Action.MENU, controls.action(controls.tap(.5f, .5f)));
            assertEquals(Controls.Action.NEXT_PAGE, controls.action(controls.tap(.5f, .1f)));
            assertEquals(Controls.Action.NEXT_PAGE, controls.action(controls.tap(.3f, .5f)));
            assertEquals(Controls.Action.NEXT_PAGE, controls.action(Controls.Input.SWIPE_LEFT));
            assertEquals(Controls.Action.PREV_PAGE, controls.action(Controls.Input.SWIPE_RIGHT));
            assertEquals(Controls.Action.NONE, controls.action(Controls.key(280)));
        }
    }

    @Test public void restoresExplicitControlsAlongsideLegacyData() {
        Store store = new Store(legacyContext(Map.of(
                "pageRev", true, "rFont", 23, "controls.layout", "CROSS",
                "controls.binding.PAGE_DOWN", "NONE", "controls.binding.KEY_280", "STRONG_CLEAN",
                "controls.binding.TAP_TOP", "STRONG_CLEAN")));
        Controls controls = store.controls();
        assertEquals(23, store.readerFontSize());
        assertEquals(Controls.Action.NONE, controls.action(Controls.Input.PAGE_DOWN));
        assertEquals(Controls.Action.PREV_PAGE, controls.action(Controls.Input.VOLUME_DOWN));
        assertEquals(Controls.Action.STRONG_CLEAN, controls.action(Controls.key(280)));
        assertTrue(Arrays.asList(controls.inputs()).contains(Controls.key(280)));
        assertEquals(Controls.Action.STRONG_CLEAN, controls.action(controls.tap(.5f, .1f)));
        assertEquals(Controls.Input.TAP_BOTTOM, controls.tap(.5f, .9f));
    }

    @Test public void swapCommitsBothAssignmentsAndResetPreservesOtherPreferences() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        SharedPreferences.Editor edit = mock(SharedPreferences.Editor.class, RETURNS_SELF);
        when(prefs.edit()).thenReturn(edit);
        Controls controls = new Controls(prefs);
        controls.swap(Controls.Input.SWIPE_LEFT, Controls.Input.TAP_CENTER);
        verify(edit).putString("controls.binding.SWIPE_LEFT", "MENU");
        verify(edit).putString("controls.binding.TAP_CENTER", "NEXT_PAGE");
        verify(edit).apply();

        doReturn(Map.of("controls.binding.SWIPE_LEFT", "MENU",
                "controls.binding.TAP_CENTER", "NEXT_PAGE", "controls.layout", "ROWS",
                "pageRev", true, "readpos", "{}")).when(prefs).getAll();
        controls.reset();
        verify(edit).remove("controls.binding.SWIPE_LEFT");
        verify(edit).remove("controls.binding.TAP_CENTER");
        verify(edit).remove("controls.layout");
        verify(edit, times(2)).apply();
        verifyNoMoreInteractions(edit);
    }

    @Test public void resumesLegacyBookAndReadsCollectionsAndSettings() {
        Context c = legacyContext(Map.ofEntries(
                Map.entry("readpos", "{\"" + BOOK + "\":{\"c\":7,\"o\":2481,\"p\":0.625,\"t\":1716600000000}}"),
                Map.entry("opened", "{\"" + BOOK + "\":1716590000000}"),
                Map.entry("dismissed", "{\"" + OTHER + "\":1716550000000}"),
                Map.entry("finished", "{\"" + OTHER + "\":1,\"" + BOOK + "\":0}"),
                Map.entry("collections", "{\"Favorites\":[\"" + OTHER + "\",\"" + BOOK + "\"],\"Later\":[]}"),
                Map.entry("pinned", "pkg.two\npkg.one"), Map.entry("hidden", "pkg.hidden"),
                Map.entry("readerBooks", "inkhome"), Map.entry("readerPdf", "pkg.pdf"),
                Map.entry("readingApps", "pkg.extra"), Map.entry("homeLayout", "shelf"),
                Map.entry("uiScale", 1.3f), Map.entry("rSpacing", 1.6f), Map.entry("rFont", 23),
                Map.entry("rSerif", false), Map.entry("sleepMode", "custom"),
                Map.entry("sleepFile", "custom"), Map.entry("libSort", "progress"),
                Map.entry("listScroll", true), Map.entry("updCheck", 1716610000000L)));
        Store store = new Store(c);
        assertTrue(store.portraitLocked());
        assertArrayEquals(new int[]{7, 2481}, store.readerPosition(BOOK));
        assertArrayEquals(new double[]{0.625, 1716600000000L}, store.readerProgress(BOOK), 0);
        assertEquals(1716590000000L, store.openedAt(BOOK));
        assertEquals(1716550000000L, store.dismissedAt(OTHER));
        assertEquals(1, store.finishedOverride(OTHER));
        assertEquals(0, store.finishedOverride(BOOK));
        Map<String, List<String>> collections = store.collections();
        assertEquals(java.util.Set.of("Favorites", "Later"), collections.keySet());
        assertEquals(Arrays.asList(OTHER, BOOK), collections.get("Favorites"));
        assertTrue(collections.get("Later").isEmpty());
        assertTrue(store.favoritesInitialised());
        assertEquals(Arrays.asList("pkg.two", "pkg.one"), store.favorites());
        assertTrue(store.hidden().contains("pkg.hidden"));
        assertEquals("inkhome", store.bookReader());
        assertEquals("pkg.pdf", store.pdfReader());
        assertTrue(store.readingApps().contains("pkg.extra"));
        assertEquals("shelf", store.homeLayout());
        assertEquals(1.3f, store.uiScale(), 0f);
        assertEquals(1.6f, store.readerLineSpacing(), 0f);
        assertEquals(23, store.readerFontSize());
        assertEquals("system_sans", store.readerFont());
        assertEquals("custom", store.sleepMode());
        assertEquals("custom", store.sleepFileMode());
        assertEquals("progress", store.librarySort());
        assertTrue(store.listScroll());
        assertEquals(1716610000000L, store.lastUpdateCheck());
    }

    private static MainActivity homeActivity(Map<String, Object> values) {
        SharedPreferences prefs = legacyContext(values).getSharedPreferences("inkhome", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = mock(SharedPreferences.Editor.class, call -> {
            switch (call.getMethod().getName()) {
                case "putString": values.put(call.getArgument(0), call.getArgument(1)); break;
                case "remove": values.remove((String) call.getArgument(0)); break;
                case "apply": return null;
                default: throw new AssertionError(call.getMethod());
            }
            return call.getMock();
        });
        doReturn(edit).when(prefs).edit();
        MainActivity activity = mock(MainActivity.class);
        when(activity.getSharedPreferences("inkhome", Context.MODE_PRIVATE)).thenReturn(prefs);
        return activity;
    }

    @Test public void legacyHomeReadsDefaultsWithoutWritingUntilEdited() {
        Map<String, Object> values = Map.of("homeLayout", "shelf", "pinned", "pkg.two\npkg.one");
        MainActivity activity = homeActivity(values);
        HomeLayout layout = HomeLayout.load(activity);
        assertEquals(List.of("current", "apps"), types(layout));
        assertEquals("shelf", layout.items.get(0).variant);
        verify(activity.getSharedPreferences("inkhome", Context.MODE_PRIVATE), never()).edit();
    }

    private static final String GRID = """
            {"pages":2,"tiles":[
              {"kind":"widget","page":1,"x":0,"y":0,"widgetId":92},
              {"kind":"moon","page":0,"x":2,"y":0},
              {"kind":"battery","page":0,"x":0,"y":3},
              {"kind":"clock","page":0,"x":1,"y":0},
              {"kind":"clock","page":1,"x":0,"y":1},
              {"kind":"widget","page":0,"x":0,"y":0,"widgetId":91}
            ]}
            """;

    @Test public void migratesInVisualOrderAndRetainsLegacyAppData() {
        Map<String, Object> values = new java.util.HashMap<>(Map.of(
                "homeGridV1", GRID, "moonLatitude", 21f, "moonLongitude", 78f,
                "useHomePreset", false, "homeLayout", "shelf", "pinned", "pkg.two"));
        MainActivity activity = homeActivity(values);
        HomeLayout layout = HomeLayout.load(activity);
        assertEquals(List.of("android", "clock", "battery", "android"), types(layout));
        assertEquals("91", layout.items.get(0).settings.get("widgetId"));
        assertEquals("92", layout.items.get(3).settings.get("widgetId"));
        assertEquals(java.util.Set.of("homeCanvasV1", "pinned", "homeLayout"), values.keySet());
        assertEquals("pkg.two", values.get("pinned"));
        assertEquals("shelf", values.get("homeLayout"));
        assertEquals(layout.items.get(0).key, HomeLayout.load(activity).items.get(0).key);
        assertValid(layout);
    }

    private static List<String> types(HomeLayout layout) {
        return layout.items.stream().map(item -> item.type).collect(java.util.stream.Collectors.toList());
    }

    private static void assertValid(HomeLayout layout) {
        for (HomeLayout.Item item : layout.items) {
            assertTrue(item.type, layout.fits(item, item.x, item.y, item.w, item.h));
            assertTrue(item.type, HomeComponents.get(item.type).supports(item.w, item.h));
        }
    }

    @Test public void inactiveGridKeepsAllocationsHiddenAndStyleEffective() {
        MainActivity activity = homeActivity(new java.util.HashMap<>(Map.of(
                "homeGridV1", GRID, "useHomePreset", true, "homeLayout", "minimal")));
        HomeLayout layout = HomeLayout.load(activity);
        assertEquals(List.of("clock", "current", "apps"), types(layout));
        assertEquals("minimal", layout.items.get(1).variant);
        assertEquals(List.of(91, 92), layout.hiddenWidgetIds);
        assertEquals(layout.hiddenWidgetIds, HomeLayout.load(activity).hiddenWidgetIds);
    }

    @Test public void customSectionsPreserveOrderOrientationAndIndependentSettings() {
        String sections = """
                {"order":["next","apps","widget:13","current","widget:11"],
                 "visible":["next","apps","current","widget:11"],
                 "compact":true,"borders":true,"verticalApps":true,"appCount":4,"nextCount":2}
                """;
        MainActivity activity = homeActivity(new java.util.HashMap<>(Map.of("homeSectionsV1", sections)));
        HomeLayout layout = HomeLayout.load(activity);
        assertEquals(List.of("next","apps","current","android"), types(layout));
        assertEquals(List.of(13), layout.hiddenWidgetIds);
        HomeLayout.Item apps = layout.items.get(1);
        assertEquals("column", apps.variant);
        assertEquals(Map.of("compact","true","borders","true","count","4"), apps.settings);
        assertEquals("2", layout.items.get(0).settings.get("count"));
        assertEquals("11", layout.items.get(3).settings.get("widgetId"));
        assertValid(layout);
        HomeLayout draft = layout.copy();
        draft.items.get(1).settings.put("count","6");
        draft.items.get(1).variant = "grid";
        draft.hiddenWidgetIds.clear();
        assertEquals("4", apps.settings.get("count"));
        assertEquals(List.of(13), layout.hiddenWidgetIds);
        draft.save(activity);
        HomeLayout restored = HomeLayout.load(activity);
        assertEquals("grid", restored.items.get(1).variant);
        assertEquals("6", restored.items.get(1).settings.get("count"));
        assertTrue(restored.hiddenWidgetIds.isEmpty());
        assertValid(restored);
    }

    @Test public void presetFitsAndDraftCannotOverlapOrLeaveCanvas() {
        HomeLayout layout = HomeLayout.preset(homeActivity(Map.of()));
        assertEquals(List.of("current", "next", "apps"), types(layout));
        assertValid(layout);
        assertEquals("content", layout.items.get(0).settings.get("heightMode"));
        assertNull(layout.items.get(1).settings.get("heightMode"));
        assertEquals("content", layout.items.get(2).settings.get("heightMode"));
        assertFalse(layout.fits(null, 0, 0, 1, 1));
        assertFalse(layout.fits(null, 0, 0, 0, 1));
        assertFalse(layout.fits(null, Integer.MAX_VALUE, 0, 1, 1));
        assertFalse(layout.fits(null, 0, 0, 1, Integer.MAX_VALUE));
    }

    @Test public void firstCanvasPresetRecoversOriginalSizingWithoutChangingEditedLayouts() {
        MainActivity activity = homeActivity(new java.util.HashMap<>());
        HomeLayout old = HomeLayout.preset(activity);
        for (HomeLayout.Item item : old.items) item.settings.clear();
        old.save(activity);
        assertEquals("content", HomeLayout.load(activity).items.get(2).settings.get("heightMode"));
        old.items.get(2).w = 3;
        old.save(activity);
        assertTrue(HomeLayout.load(activity).items.get(2).settings.isEmpty());
    }

    @Test public void firstCanvasWithoutDimensionsRetainsLegacyGoalAcrossSave() {
        Map<String, Object> values = new java.util.HashMap<>();
        values.put("homeCanvasV1", """
                {"items":[{"key":"my-goal","type":"goal","x":0,"y":12,"w":4,"h":2,
                "settings":{"borders":"true"}}],"hiddenWidgetIds":[91]}
                """);
        MainActivity activity = homeActivity(values);
        HomeLayout layout = HomeLayout.load(activity);
        assertEquals(4, layout.cols);
        assertEquals(16, layout.rows);
        layout.save(activity);
        layout = HomeLayout.load(activity);
        assertEquals(4, layout.items.get(0).w);
        assertEquals(2, layout.items.get(0).h);
        assertTrue(layout.changeGrid(6, 14));
        layout.save(activity);
        layout = HomeLayout.load(activity);
        assertEquals(6, layout.cols);
        assertEquals(14, layout.rows);
        assertEquals("my-goal", layout.items.get(0).key);
        assertEquals("true", layout.items.get(0).settings.get("borders"));
        assertEquals(List.of(91), layout.hiddenWidgetIds);
        assertValid(layout);
    }

    @Test public void emptyCanvasAndRepeatedComponentsRoundTrip() {
        MainActivity activity = homeActivity(new java.util.HashMap<>());
        HomeLayout layout = new HomeLayout();
        layout.save(activity);
        assertTrue(HomeLayout.load(activity).items.isEmpty());
        layout.items.add(new HomeLayout.Item("clock", "default", 2, 4, 2, 2));
        layout.items.add(new HomeLayout.Item("clock", "default", 1, 8, 1, 3));
        layout.save(activity);
        HomeLayout restored = HomeLayout.load(activity);
        assertEquals(List.of("clock", "clock"), types(restored));
        for (int i = 0; i < 2; i++) {
            HomeLayout.Item before = layout.items.get(i), after = restored.items.get(i);
            assertEquals(before.key, after.key);
            assertArrayEquals(new int[]{before.x, before.y, before.w, before.h},
                    new int[]{after.x, after.y, after.w, after.h});
        }
        assertNotEquals(restored.items.get(0).key, restored.items.get(1).key);
    }

    @Test public void exactAutomaticReadingGridRestoresClassicHome() throws Exception {
        String[] kinds = {"current", "next", "clock", "goal", "month", "apps", "battery", "moon"};
        int[][] bounds = {{0,0,4,2},{0,2,2,2},{2,2,2,1},{2,3,2,1},
                {0,4,2,2},{2,4,2,1},{2,5,1,1},{3,5,1,1}};
        org.json.JSONArray tiles = new org.json.JSONArray();
        for (int i = 0; i < kinds.length; i++) {
            tiles.put(new org.json.JSONObject().put("kind", kinds[i]).put("page", 0)
                    .put("widgetId", -1).put("x", bounds[i][0]).put("y", bounds[i][1])
                    .put("w", bounds[i][2]).put("h", bounds[i][3]));
        }
        org.json.JSONObject grid = new org.json.JSONObject().put("pages", 1).put("tiles", tiles);
        assertEquals(List.of("current","next","apps"), types(HomeLayout.load(homeActivity(
                new java.util.HashMap<>(Map.of("homeGridV1", grid.toString()))))));
        tiles.getJSONObject(0).put("w", 3);
        HomeLayout custom = HomeLayout.load(homeActivity(new java.util.HashMap<>(Map.of(
                "homeGridV1", grid.toString()))));
        assertEquals(7, custom.items.size());
        assertValid(custom);
    }

    @Test public void rebuildsTimeAndStreaksFromLegacyDayAndBookLogsWithoutUsageAccess() {
        Calendar day = Calendar.getInstance();
        SimpleDateFormat key = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        String today = key.format(day.getTime());
        day.add(Calendar.DATE, -1);
        String yesterday = key.format(day.getTime());
        day.add(Calendar.DATE, -1);
        String earlier = key.format(day.getTime());
        Context c = legacyContext(Map.of(
                "goal", 20, "monday", false,
                "log", "{\"" + earlier + "\":5,\"" + yesterday + "\":21,\"" + today + "\":35}",
                "booklog", "{\"" + BOOK + "|" + yesterday + "\":19,\"" + BOOK + "|" + today + "\":25,"
                        + "\"" + OTHER + "|" + today + "\":10}"));
        Stats stats = Stats.refresh(c, new Store(c));
        assertEquals(61, stats.total());
        assertEquals(44, stats.forBook(BOOK));
        assertEquals(10, stats.forBook(OTHER));
        assertEquals(20, stats.goal);
        assertFalse(stats.mondayFirst);
        assertEquals(2, stats.currentStreak());
        assertEquals(2, stats.bestStreak());
    }
}

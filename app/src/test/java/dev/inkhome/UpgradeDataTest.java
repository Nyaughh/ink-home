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

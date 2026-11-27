package dev.inkhome;

import android.content.Context;
import java.io.File;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything Ink Home remembers, in one SharedPreferences file. */
final class Store {
    private final SharedPreferences p;
    private final Context context;

    Store(Context c) {
        context = c.getApplicationContext();
        p = c.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
    }

    boolean quiet() { return p.getBoolean("quiet", false); }
    void setQuiet(boolean enabled) { p.edit().putBoolean("quiet", enabled).apply(); }

    boolean setupDismissed() { return p.getBoolean("setupDismissed", false); }
    void dismissSetup() { p.edit().putBoolean("setupDismissed", true).apply(); }
    boolean displayConfirmed() { return p.getBoolean("displayConfirmed", false); }
    String displayOverride() { return p.getString("displayClass", ""); }
    void confirmDisplay(String displayClass) {
        p.edit().putBoolean("displayConfirmed", true).putString("displayClass", displayClass).apply();
    }
    boolean swipePages() { return p.getBoolean("swipePages", true); }
    void setSwipePages(boolean enabled) { p.edit().putBoolean("swipePages", enabled).apply(); }
    boolean hasThemeOverride() { return p.contains("night"); }
    void setNight(Boolean night) {
        if (night == null) p.edit().remove("night").apply();
        else p.edit().putBoolean("night", night).apply();
    }

    // ---------------------------------------------------------------- favorites & hidden apps

    List<String> favorites() { return list("pinned"); }
    boolean favoritesInitialised() { return p.contains("pinned"); }
    void setFavorites(List<String> v) { putList("pinned", v); }
    boolean isFavorite(String k) { return favorites().contains(k); }
    void toggleFavorite(String k) {
        List<String> l = favorites();
        if (!l.remove(k)) l.add(k);
        setFavorites(l);
    }

    Set<String> hidden() { return new HashSet<>(list("hidden")); }
    void setHidden(String k, boolean hide) {
        List<String> l = list("hidden");
        l.remove(k);
        if (hide) l.add(k);
        putList("hidden", l);
    }

    /** Apps whose screen time counts as reading. NeoReader is always included. */
    /** The built-in reader's id in the reader settings. */
    static final String BUILT_IN = "inkhome";

    Set<String> readingApps() {
        Set<String> s = new HashSet<>(list("readingApps"));
        s.add("com.onyx.kreader");
        String b = bookReader(), pdf = pdfReader();
        if (!b.isEmpty() && !b.equals("ask") && !b.equals(BUILT_IN)) s.add(b);
        if (!pdf.isEmpty() && !pdf.equals("ask")) s.add(pdf);
        return s;
    }
    void setReadingApp(String pkg, boolean on) {
        List<String> l = list("readingApps");
        l.remove(pkg);
        if (on) l.add(pkg);
        putList("readingApps", l);
    }

    // ---------------------------------------------------------------- books

    /** When Ink Home itself opened each book (path -> epoch ms). */
    long openedAt(String path) { return map("opened").optLong(path, 0); }
    JSONObject openedAll() { return map("opened"); }
    void markOpened(String path) { putInMap("opened", path, System.currentTimeMillis()); }

    /** User overrides of NeoReader's finished flag: 1 finished, 0 not finished, absent = follow reader. */
    int finishedOverride(String path) { return map("finished").optInt(path, -1); }
    void setFinished(String path, boolean v) { putInMap("finished", path, v ? 1 : 0); }

    /** Books the user removed from "Reading" (path -> time removed). Re-opening brings them back. */
    long dismissedAt(String path) { return map("dismissed").optLong(path, 0); }
    void dismiss(String path) { setDismissedAt(path, System.currentTimeMillis()); }
    void setDismissedAt(String path, long time) { putInMap("dismissed", path, time); }

    // collections: name -> list of paths, kept in creation order
    Map<String, List<String>> collections() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        JSONObject o = map("collections");
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String name = it.next();
            JSONArray a = o.optJSONArray(name);
            List<String> paths = new ArrayList<>();
            if (a != null) for (int i = 0; i < a.length(); i++) paths.add(a.optString(i));
            out.put(name, paths);
        }
        return out;
    }
    void setCollections(Map<String, List<String>> m) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> e : m.entrySet()) o.put(e.getKey(), new JSONArray(e.getValue()));
        } catch (Exception ignored) { }
        p.edit().putString("collections", o.toString()).apply();
    }

    // ---------------------------------------------------------------- built-in reader

    /** {chapter, character offset} where the built-in reader left this book. */
    int[] readerPosition(String path) {
        JSONObject o = map("readpos").optJSONObject(path);
        return o == null ? new int[]{0, 0} : new int[]{o.optInt("c", 0), o.optInt("o", 0)};
    }

    /** Whole-book progress (0..1) and last-read time from the built-in reader, or null. */
    double[] readerProgress(String path) {
        JSONObject o = map("readpos").optJSONObject(path);
        return o == null ? null : new double[]{o.optDouble("p", 0), o.optLong("t", 0)};
    }

    void setReaderPosition(String path, int chapter, int offset, float progress) {
        JSONObject all = map("readpos");
        try {
            JSONObject o = new JSONObject();
            o.put("c", chapter);
            o.put("o", offset);
            o.put("p", progress);
            o.put("t", System.currentTimeMillis());
            all.put(path, o);
        } catch (Exception ignored) { }
        p.edit().putString("readpos", all.toString()).apply();
    }

    int readerFontSize() { return p.getInt("rFont", 19); }
    void setReaderFontSize(int v) { p.edit().putInt("rFont", v).apply(); }
    float readerLineSpacing() { return p.getFloat("rSpacing", 1.35f); }
    void setReaderLineSpacing(float v) { p.edit().putFloat("rSpacing", v).apply(); }
    int readerMargin() { return p.getInt("rMargin", 24); }
    void setReaderMargin(int v) { p.edit().putInt("rMargin", v).apply(); }
    String readerFont() {
        String font = p.getString("rFontFace", null);
        if (font != null) return font;
        // A saved switch explicitly selected one of Android's system families.
        if (!p.contains("rSerif")) return "literata";
        return p.getBoolean("rSerif", true) ? "system_serif" : "system_sans";
    }
    void setReaderFont(String font) { p.edit().putString("rFontFace", font).apply(); }
    String readerFooter() { return p.getString("rFooter", "off"); }
    void setReaderFooter(String footer) { p.edit().putString("rFooter", footer).apply(); }
    boolean readerJustify() { return p.getBoolean("rJustify", true); }
    void setReaderJustify(boolean v) { p.edit().putBoolean("rJustify", v).apply(); }
    int readerRefreshEvery() { return p.getInt("rRefresh", 8); }
    void setReaderRefreshEvery(int v) { p.edit().putInt("rRefresh", v).apply(); }

    // EPUB title/author cache: key -> "title\u0001author"
    String meta(String key) { return p.getString("meta:" + key, null); }
    void setMeta(String key, String v) { p.edit().putString("meta:" + key, v).apply(); }

    // ---------------------------------------------------------------- reading log (yyyyMMdd -> minutes)

    JSONObject readingLog() { return map("log"); }
    void setReadingLog(JSONObject o) { p.edit().putString("log", o.toString()).apply(); }

    // ---------------------------------------------------------------- settings

    String homeLayout() { return p.getString("homeLayout", "hero"); }
    void setHomeLayout(String v) { p.edit().putString("homeLayout", v).apply(); }

    /** Scales the whole interface: spacing, icons, covers and text. */
    float uiScale() { return p.contains("uiScale") ? p.getFloat("uiScale", 1.15f)
            : dev.inkhome.folio.DisplayProbe.displayClass(context).defaultInterfaceScale(); }
    void setUiScale(float v) { p.edit().putFloat("uiScale", v).apply(); }

    /** Home wallpaper: absolute path of a picture, or "" for none. */
    String wallpaper() { return p.getString("wallpaper", ""); }
    void setWallpaper(String v) { p.edit().putString("wallpaper", v).apply(); }

    /** How much the wallpaper is faded toward white: 0.45, 0.65 or 0.8. */
    float wallpaperFade() { return p.getFloat("wallFade", 0.65f); }
    void setWallpaperFade(float v) { p.edit().putFloat("wallFade", v).apply(); }

    float textScale() { return p.getFloat("textScale", 1f); }
    void setTextScale(float v) { p.edit().putFloat("textScale", v).apply(); }

    String clock() { return p.getString("clock", "system"); }
    void setClock(String v) { p.edit().putString("clock", v).apply(); }

    boolean statusBar() { return p.getBoolean("statusBar", true); }
    void setStatusBar(boolean v) { p.edit().putBoolean("statusBar", v).apply(); }

    boolean appIcons() { return p.getBoolean("appIcons", true); }
    void setAppIcons(boolean v) { p.edit().putBoolean("appIcons", v).apply(); }

    boolean realCovers() { return p.getBoolean("realCovers", true); }
    void setRealCovers(boolean v) { p.edit().putBoolean("realCovers", v).apply(); }

    /** 0 means no goal: Ink Home just shows time read. */
    int dailyGoal() { return p.getInt("goal", 0); }

    /** Package that opens books ("" = NeoReader if present, "ask" = always ask). */
    String bookReader() { return p.getString("readerBooks", ""); }
    void setBookReader(String v) { p.edit().putString("readerBooks", v).apply(); }

    /** Package that opens PDFs, DJVU and comics. */
    String pdfReader() { return p.getString("readerPdf", ""); }
    void setPdfReader(String v) { p.edit().putString("readerPdf", v).apply(); }

    /** Per-book reading log: "path|yyyyMMdd" -> minutes. */
    JSONObject bookLog() { return map("booklog"); }
    void setBookLog(JSONObject o) { p.edit().putString("booklog", o.toString()).apply(); }
    void setDailyGoal(int v) { p.edit().putInt("goal", v).apply(); }

    boolean weekStartsMonday() { return p.getBoolean("monday", true); }
    void setWeekStartsMonday(boolean v) { p.edit().putBoolean("monday", v).apply(); }

    boolean cleanOnHome() { return p.getBoolean("cleanOnHome", true); }
    void setCleanOnHome(boolean v) { p.edit().putBoolean("cleanOnHome", v).apply(); }

    boolean portraitLocked() { return p.getBoolean("portraitLocked", false); }
    void setPortraitLocked(boolean locked) { p.edit().putBoolean("portraitLocked", locked).apply(); }

    boolean pageKeysReversed() { return p.getBoolean("pageRev", false); }
    void setPageKeysReversed(boolean v) { p.edit().putBoolean("pageRev", v).apply(); }

    /** "off", "book" (current book, kept up to date) or "custom" (a picture the user chose). */
    String sleepMode() { return p.getString("sleepMode", "off"); }
    void setSleepMode(String v) { p.edit().putString("sleepMode", v).apply(); }

    /** Which mode last wrote the sleep picture file ("book" or "custom"). */
    String sleepFileMode() { return p.getString("sleepFile", ""); }
    String sleepSource() { return p.getString("sleepSource", ""); }
    boolean sleepFill() { return p.getBoolean("sleepFill", true); }
    String sleepImageSize() { return p.getString("sleepImageSize", ""); }
    void rememberSleepImage(File source, boolean fill, SleepSize size) {
        p.edit().putString("sleepSource", source.getAbsolutePath()).putBoolean("sleepFill", fill)
                .putString("sleepImageSize", size.key()).putString("sleepFile", "custom").apply();
    }
    void setSleepFileMode(String v) { p.edit().putString("sleepFile", v).apply(); }

    /** Long lists (settings, apps…): false = pages with chevron buttons (default), true = scroll. */
    boolean listScroll() { return p.getBoolean("listScroll", false); }
    void setListScroll(boolean v) { p.edit().putBoolean("listScroll", v).apply(); }

    boolean autoUpdate() { return p.getBoolean("autoUpdate", true); }
    void setAutoUpdate(boolean v) { p.edit().putBoolean("autoUpdate", v).apply(); }

    void setUpdateNotice(String message) { p.edit().putString("updateNotice", message).apply(); }
    String takeUpdateNotice() {
        String message = p.getString("updateNotice", null);
        if (message != null) p.edit().remove("updateNotice").apply();
        return message;
    }
    long lastUpdateCheck() { return p.getLong("updCheck", 0); }
    void setLastUpdateCheck(long v) { p.edit().putLong("updCheck", v).apply(); }

    boolean libraryGrid() { return p.getBoolean("libGrid", true); }
    void setLibraryGrid(boolean v) { p.edit().putBoolean("libGrid", v).apply(); }

    String librarySort() { return p.getString("libSort", "recent"); }
    void setLibrarySort(String v) { p.edit().putString("libSort", v).apply(); }

    String libraryType() { return p.getString("filter", "all"); }
    void setLibraryType(String v) { p.edit().putString("filter", v).apply(); }

    // ---------------------------------------------------------------- helpers

    private List<String> list(String key) {
        String s = p.getString(key, "");
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        out.addAll(Arrays.asList(s.split("\n")));
        out.removeIf(String::isEmpty);
        return out;
    }

    private void putList(String key, List<String> v) {
        p.edit().putString(key, String.join("\n", v)).apply();
    }

    private JSONObject map(String key) {
        try {
            return new JSONObject(p.getString(key, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void putInMap(String key, String k, Object v) {
        JSONObject o = map(key);
        try { o.put(k, v); } catch (Exception ignored) { }
        p.edit().putString(key, o.toString()).apply();
    }
}

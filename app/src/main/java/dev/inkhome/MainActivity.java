package dev.inkhome;

import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.paging.PageLayout;
import dev.inkhome.folio.paging.PagedList;
import dev.inkhome.folio.paging.PagedGrid;
import dev.inkhome.folio.paging.PagedFlow;
import dev.inkhome.folio.paging.ScrollingCells;
import java.util.function.IntFunction;
import dev.inkhome.folio.core.WindowClass;
import dev.inkhome.folio.widget.FolioSwitch;
import dev.inkhome.folio.widget.LevelBar;
import dev.inkhome.folio.widget.Segmented;
import dev.inkhome.folio.widget.Structure;
import dev.inkhome.folio.widget.Tile;
import dev.inkhome.folio.widget.Button;
import dev.inkhome.folio.DisplayProbe;
import dev.inkhome.folio.core.Capabilities;
import android.media.AudioManager;
import android.app.NotificationManager;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import dev.inkhome.folio.epd.RefreshController;
import dev.inkhome.folio.core.RefreshBudget.Change;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ink Home: a reading-first launcher for black-and-white e-ink.
 * Calm home with your current book, a paged library with real covers and progress,
 * reading time per day and per book, scrolling apps and settings, its own status bar,
 * and true full-screen refreshes.
 */
public class MainActivity extends Activity {

    private static final String TAG = "InkHome";
    static final String NEOREADER_PKG = "com.onyx.kreader";
    private static final String NEOREADER_CLS = "com.onyx.kreader.ui.ReaderHomeActivity";
    static final String BOOX_HOME = "com.onyx/com.onyx.StartupActivity";
    static final int PAD = 22;

    private enum Screen {
        HOME(new HomeScreen()), LIBRARY(new LibraryScreen()), STATS(new StatsScreen()),
        APPS(new AppsScreen()), SETTINGS(new SettingsScreen());

        final dev.inkhome.Screen view;

        Screen(dev.inkhome.Screen view) {
            this.view = view;
        }
    }

    Store store;
    Ink ink;
    private SheetHost sheets;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final ExecutorService thumbs = Executors.newSingleThreadExecutor();

    private LinearLayout statusBar;
    private FrameLayout content;
    private LinearLayout dock;
    private FrameLayout overlay;

    private Screen screen = Screen.HOME;
    String libTab = "reading";
    String libCollection = null;
    String libSearch = null;
    String sub = null;
    String statsTab = "overview";
    final Calendar statsMonth = Calendar.getInstance();
    final Map<String, Integer> pages = new HashMap<>();
    Runnable pageNext, pagePrev;
    private boolean panelOpen = false;

    List<Library.Book> books = new ArrayList<>();
    boolean booksLoaded = false, loading = false;
    long lastLoad = 0;
    private int dataSignature = 0;
    Stats stats;
    List<App> apps = new ArrayList<>();
    final Map<String, Drawable> iconCache = new HashMap<>();
    private boolean launchedSomething = false;
    private String statusSignature = "";
    String sleepSignature = "";
    RefreshController refreshController;

    static final class App {
        final String key, label, pkg;
        final ComponentName cn;
        final ResolveInfo ri;
        App(ResolveInfo ri, ComponentName cn, String label) {
            this.ri = ri;
            this.cn = cn;
            this.label = label;
            this.pkg = cn.getPackageName();
            this.key = cn.getPackageName() + "/" + cn.getClassName();
        }
    }

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // NeoReader tracks progress by file path, so it gets file:// links; other readers get content://.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        store = new Store(this);
        ink = new Ink(this, store.textScale(), store.uiScale());

        ActivityWindow.applyOrientation(this, store);
        restoreNavigation(b);

        FrameLayout shell = new FrameLayout(this);
        ActivityWindow.fitContent(this, shell);
        shell.setBackgroundColor(ink.PAPER);
        LinearLayout root = ink.col();
        statusBar = ink.row();
        root.addView(statusBar, new LinearLayout.LayoutParams(-1, -2));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(ink.hair(), new LinearLayout.LayoutParams(-1, ink.folio.rule));
        dock = ink.row();
        root.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));
        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        shell.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        // Hide the system bar before the first layout, so the screen height never changes under the user.
        applySystemBar();
        setContentView(shell);
        sheets = new SheetHost(this, shell, dock, () -> ink);

        refreshController = new RefreshController((ViewGroup) getWindow().getDecorView(), store::readerRefreshEvery);
        loadApps();
        if (!store.favoritesInitialised()) seedFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshController.resume();
        applySystemBar();
        // Apps installed while another app was open (e.g. from Aurora Store) send their
        // broadcast while Ink Home isn't listening, so refresh the list every time we return.
        loadApps();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        registerReceiver(systemEvents, f);
        IntentFilter pk = new IntentFilter();
        pk.addAction(Intent.ACTION_PACKAGE_ADDED);
        pk.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pk.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pk.addDataScheme("package");
        registerReceiver(packageEvents, pk);
        Notifs.setListener(n -> ui.post(() -> updateStatusBar(true)));
        if (libSearch == null) hideKeyboard();

        statusSignature = "";
        render();
        refreshData(launchedSomething ? 0 : 30_000);
        if (launchedSomething && store.cleanOnHome()) refreshController.cleanWhenIdle();
        if (store.autoUpdate() && System.currentTimeMillis() - store.lastUpdateCheck() > 12 * 3_600_000L) checkUpdates(false);
        launchedSomething = false;
        Updater.onInstallFailure = this::showUpdateNotice;
        showUpdateNotice();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (content == null) return;
        boolean menu = menuOpen;
        boolean panel = panelOpen;
        ink = new Ink(this, store.textScale(), store.uiScale());
        applySystemBar();
        render();
        if (menu) openMenu();
        else if (panel) openPanel();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", screen.name());
        state.putString("libraryTab", libTab);
        state.putString("collection", libCollection);
        state.putString("search", libSearch);
        state.putString("subpage", sub);
        state.putString("statsTab", statsTab);
        state.putLong("statsMonth", statsMonth.getTimeInMillis());
        Bundle positions = new Bundle();
        for (Map.Entry<String, Integer> entry : pages.entrySet()) {
            positions.putInt(entry.getKey(), entry.getValue());
        }
        state.putBundle("pages", positions);
    }

    private void restoreNavigation(Bundle state) {
        if (state == null) return;
        screen = Screen.valueOf(state.getString("screen", Screen.HOME.name()));
        libTab = state.getString("libraryTab", libTab);
        libCollection = state.getString("collection");
        libSearch = state.getString("search");
        sub = state.getString("subpage");
        statsTab = state.getString("statsTab", statsTab);
        statsMonth.setTimeInMillis(state.getLong("statsMonth", statsMonth.getTimeInMillis()));
        Bundle positions = state.getBundle("pages");
        if (positions != null) {
            for (String key : positions.keySet()) pages.put(key, positions.getInt(key));
        }
    }

    private void showUpdateNotice() {
        String message = store.takeUpdateNotice();
        if (message != null) toast(message);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Updater.onInstallFailure = null;
        refreshController.pause();
        try { unregisterReceiver(systemEvents); } catch (Exception ignored) { }
        try { unregisterReceiver(packageEvents); } catch (Exception ignored) { }
        Notifs.setListener(null);
    }

    @Override
    protected void onDestroy() {
        sheets.close();
        refreshController.close();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (sheets.dismiss()) return;
        if (panelOpen) { closePanel(); return; }
        // Home on Home clears ghosting; otherwise it returns to the home page.
        if (screen == Screen.HOME && hasWindowFocus()) cleanScreen();
        else go(Screen.HOME);
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) closePanel();
        else if (libSearch != null) { libSearch = null; hideKeyboard(); render(); }
        else if (libCollection != null) { libCollection = null; render(); }
        else if (sub != null) { sub = null; render(); }
        else if (screen != Screen.HOME) go(Screen.HOME);
    }

    /** Page keys are handled here, before any list can swallow them. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean next = code == KeyEvent.KEYCODE_PAGE_DOWN || code == KeyEvent.KEYCODE_VOLUME_DOWN;
        boolean prev = code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_VOLUME_UP;
        if (store.pageKeysReversed()) { boolean t = next; next = prev; prev = t; }
        if ((next && pageNext != null) || (prev && pagePrev != null)) {
            // Boox's firmware swallows the key-down of page keys, so act on key-up.
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) (next ? pageNext : pagePrev).run();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private final BroadcastReceiver systemEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateStatusBar(false);
            Calendar now = Calendar.getInstance();
            if (Intent.ACTION_TIME_TICK.equals(i.getAction()) &&
                    ((booksLoaded && hasFileAccess() != lastScanHadAccess)
                            || (now.get(Calendar.HOUR_OF_DAY) == 0 && now.get(Calendar.MINUTE) == 0)))
                refreshData(0);
        }
    };

    private final BroadcastReceiver packageEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); render(); }
    };

    // ================================================================== data

    long lastFullScan = 0;
    private boolean lastScanHadAccess;

    void refreshData(long maxAgeMs) {
        if (loading) return;
        final boolean access = hasFileAccess();
        final boolean accessChanged = booksLoaded && access != lastScanHadAccess;
        if (booksLoaded && !accessChanged && !(access && books.isEmpty())
                && System.currentTimeMillis() - lastLoad < maxAgeMs) return;
        loading = true;
        // A full storage walk at most every 10 minutes (or on request); in between, only
        // reading progress is refreshed, which keeps coming back from a book instant.
        final boolean full = !booksLoaded || accessChanged || books.isEmpty()
                || System.currentTimeMillis() - lastFullScan > 10 * 60_000L;
        final List<File> known = new ArrayList<>();
        if (!full) for (Library.Book b : books) known.add(b.file);
        if (full && access) lastFullScan = System.currentTimeMillis();
        io.execute(() -> {
            List<Library.Book> found = access ? Library.load(this, store, full ? null : known) : new ArrayList<>();
            Stats st = Stats.refresh(this, store);
            ui.post(() -> {
                if (hasFileAccess() != access) {
                    loading = false;
                    refreshData(0);
                    return;
                }
                books = found;
                stats = st;
                booksLoaded = true;
                lastScanHadAccess = access;
                lastLoad = System.currentTimeMillis();
                rerenderIfChanged();
                extractCovers();
            });
        });
    }

    /** Pulls covers and EPUB titles in the background, current books first, then redraws once. */
    void extractCovers() {
        if (!store.realCovers()) { loading = false; return; }
        List<Library.Book> todo = new ArrayList<>();
        for (Library.Book b : reading()) if (Covers.pending(this, b)) todo.add(b);
        for (Library.Book b : books) if (!todo.contains(b) && Covers.pending(this, b)) todo.add(b);
        if (todo.isEmpty()) { loading = false; return; }
        io.execute(() -> {
            long start = System.currentTimeMillis();
            for (Library.Book b : todo) {
                Covers.extract(this, store, b);
                String meta = store.meta(b.key());
                if (meta != null) {
                    String[] parts = meta.split("\u0001", -1);
                    if (!parts[0].isEmpty() && b.title.equals(Library.titleFromName(b.file.getName()))) b.title = parts[0];
                    if (parts.length > 1 && !parts[1].isEmpty() && b.author.isEmpty()) b.author = parts[1];
                }
                if (System.currentTimeMillis() - start > 60_000) break;
            }
            ui.post(() -> {
                loading = false;
                Covers.flush();
                dataSignature = 0;
                rerenderIfChanged();
            });
        });
    }

    private void rerenderIfChanged() {
        int sig = 17;
        for (Library.Book b : books) {
            sig = sig * 31 + b.path.hashCode();
            sig = sig * 31 + Float.floatToIntBits(b.progress);
            sig = sig * 31 + Long.hashCode(b.lastRead);
            sig = sig * 31 + (b.finished ? 1 : 0);
            sig = sig * 31 + b.title.hashCode();
        }
        if (stats != null) sig = sig * 31 + stats.today() + 7 * stats.minutes.size() + 13 * stats.total();
        updateSleepPicture(false);
        if (sig == dataSignature) return;
        dataSignature = sig;
        // Never redraw under the user's typing; every redraw also costs an e-ink refresh.
        if (libSearch != null || panelOpen || sub != null) return;
        if (screen == Screen.HOME || screen == Screen.LIBRARY || screen == Screen.STATS) render();
        else renderDock();
    }

    List<Library.Book> reading() {
        List<Library.Book> out = new ArrayList<>();
        for (Library.Book b : books) {
            if (!b.started() || b.finished) continue;
            if (store.dismissedAt(b.path) >= b.lastRead) continue;
            out.add(b);
        }
        out.sort((a, b) -> Long.compare(b.lastRead, a.lastRead));
        return out;
    }

    private List<Library.Book> newest() {
        List<Library.Book> out = new ArrayList<>(books);
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    private int bookMinutes(Library.Book b) { return stats == null ? 0 : stats.forBook(b.path); }

    /** "42%" / "1 h 10 min read" / "Not started": the most useful one-line status for a book. */
    private String bookStatus(Library.Book b) {
        if (b.finished) return "Finished";
        int pc = b.percent();
        int mins = bookMinutes(b);
        if (pc >= 0 && mins > 0) return pc + "%  ·  " + Stats.hm(mins) + " read";
        if (pc >= 0) return pc + "%";
        if (mins > 0) return Stats.hm(mins) + " read";
        return b.started() ? "Started" : "Not started";
    }

    // ================================================================== navigation

    private void go(Screen s) {
        if (s != screen) {
            pages.clear();
            libSearch = null;
            libCollection = null;
            sub = null;
        }
        screen = s;
        render();
    }

    boolean coverPage;

    void render() {
        coverPage = false;
        closePanelSilently();
        content.removeAllViews();
        content.setBackground(null);
        pageNext = pagePrev = null;
        screen.view.build(this);
        pageChanged();
        renderDock();
        updateStatusBar(true);
    }

    private boolean menuOpen = false;

    /**
     * Readblio-style bottom bar: settings on the left (home while in settings), today's reading
     * time in the middle, and a chevron on the right that opens the menu.
     */
    private void renderDock() {
        dock.removeAllViews();
        dock.setPadding(ink.dp(8), 0, ink.dp(8), 0);
        int dockHeight = ink.dp(52) - ink.folio.rule;
        boolean inSettings = screen == Screen.SETTINGS;
        ImageView left = ink.icon(inSettings ? "home" : "settings", 24);
        left.setPadding(ink.dp(12), ink.dp(12), ink.dp(12), ink.dp(12));
        left.setBackground(ink.pressable());
        left.setContentDescription(inSettings ? "Home" : "Settings");
        left.setOnClickListener(v -> go(inSettings ? Screen.HOME : Screen.SETTINGS));
        dock.addView(left, new LinearLayout.LayoutParams(ink.dp(52), dockHeight));

        int today = stats == null ? 0 : stats.today();
        int goal = store.dailyGoal();
        String label = goal > 0 ? "Today " + today + " / " + goal + " min" : "Today " + Stats.hm(today);
        TextView mid = ink.center(ink.oneLine(ink.text(label, TypeScale.BODY, ink.TEXT)));
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(ink.pressable());
        mid.setContentDescription(label + ". Open Stats");
        mid.setOnClickListener(v -> go(Screen.STATS));
        dock.addView(mid, new LinearLayout.LayoutParams(0, dockHeight, 1f));

        ImageView right = ink.icon(menuOpen ? "down" : "up", 24);
        right.setPadding(ink.dp(12), ink.dp(12), ink.dp(12), ink.dp(12));
        right.setBackground(ink.pressable());
        right.setContentDescription(menuOpen ? "Close menu" : "Open menu");
        right.setOnClickListener(v -> { if (menuOpen) closePanel(); else openMenu(); });
        dock.addView(right, new LinearLayout.LayoutParams(ink.dp(52), dockHeight));
    }

    /** The chevron menu: every destination and the screen refresh, as a sheet above the bar. */
    private void openMenu() {
        closePanelSilently();
        refreshController.changed(overlay, Change.OVERLAY);
        panelOpen = true;
        menuOpen = true;
        overlay.setVisibility(View.VISIBLE);
        overlay.setOnClickListener(v -> closePanel());

        LinearLayout sheet = ink.col();
        sheet.setBackgroundColor(ink.PAPER);
        sheet.setClickable(true);
        sheet.setPadding(ink.dp(12), ink.dp(14), ink.dp(12), ink.dp(10));
        Object[][] items = {
                {"home", "Home", (Runnable) () -> go(Screen.HOME), Screen.HOME},
                {"library", "Library", (Runnable) () -> go(Screen.LIBRARY), Screen.LIBRARY},
                {"stats", "Stats", (Runnable) () -> go(Screen.STATS), Screen.STATS},
                {"apps", "Apps", (Runnable) () -> go(Screen.APPS), Screen.APPS},
                {"search", "Search", (Runnable) () -> { go(Screen.LIBRARY); libSearch = ""; render(); }, null},
                {"clean", "Clean screen", (Runnable) () -> { closePanel(); ui.postDelayed(this::cleanScreen, 120); }, null},
                {"wifi", "Wi-Fi", (Runnable) this::openWifi, null},
                {"tune", "Control panel", (Runnable) () -> { closePanel(); openPanel(); }, null},
        };
        LinearLayout line = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 4 == 0) {
                line = ink.row();
                sheet.addView(line, new LinearLayout.LayoutParams(-1, -2));
            }
            Object[] it = items[i];
            boolean on = it[3] == screen;
            LinearLayout cell = ink.col();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, ink.dp(12), 0, ink.dp(12));
            cell.setBackground(on ? ink.shapes.stroke(ink.PAPER, ink.TEXT) : ink.pressable());
            cell.setSelected(on);
            Icons d = it[0].equals("wifi") ? new Icons("wifi", wifi().connected ? wifi().level : -1) : new Icons((String) it[0]);
            cell.addView(ink.icon(d, 26));
            TextView t = ink.center(ink.text((String) it[1], TypeScale.META, ink.TEXT));
            t.setPadding(0, ink.dp(8), 0, 0);
            cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            Runnable r = (Runnable) it[2];
            cell.setOnClickListener(v -> r.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(ink.dp(3), ink.dp(3), ink.dp(3), ink.dp(3));
            line.addView(cell, lp);
        }
        LinearLayout wrap = ink.col();
        wrap.addView(ink.hair());
        wrap.addView(sheet);
        int width = ink.folio.windowClass() == WindowClass.EXPANDED ? Math.min(content.getWidth(), ink.dp(480)) : -1;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, -2, Gravity.END | Gravity.BOTTOM);
        // The dock stays visible and handles its own taps while the menu is open.
        FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) overlay.getLayoutParams();
        bounds.bottomMargin = dock.getHeight() + ink.folio.rule;
        overlay.setLayoutParams(bounds);
        overlay.addView(ink.scroller(wrap), params);
        renderDock();
    }

    /** Standard page header: back button (optional), title, faint count, then right-side actions. */
    LinearLayout header(String title, String count, Runnable back, View... actions) {
        LinearLayout row = (LinearLayout) new Structure(ink.folio).appBar(title, count, back);
        row.setPadding(ink.dp(PAD), 0, ink.dp(PAD), 0);
        for (View action : actions) row.addView(action);
        return row;
    }

    LinearLayout tabs(String[] labels, int selected, java.util.function.IntConsumer select) {
        return (LinearLayout) new Structure(ink.folio).tabs(labels, selected, select);
    }

    LinearLayout page() {
        LinearLayout col = ink.col();
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return col;
    }

    // ================================================================== status bar & quick panel

    void applySystemBar() {
        getWindow().getDecorView(); // makes sure the window exists, even before setContentView
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        if (store.statusBar()) {
            c.hide(WindowInsets.Type.statusBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            c.show(WindowInsets.Type.statusBars());
        }
    }

    private void updateStatusBar(boolean force) {
        if (!store.statusBar()) {
            if (statusBar.getChildCount() > 0) statusBar.removeAllViews();
            return;
        }
        Battery bat = battery();
        Wifi wifi = wifi();
        int notes = Notifs.count();
        String time = timeText(new Date());
        String sig = time + bat.percent + bat.charging + wifi.level + wifi.connected + notes;
        if (!force && sig.equals(statusSignature)) return;
        statusSignature = sig;

        statusBar.removeAllViews();
        statusBar.addView(new Structure(ink.folio).statusStrip(time, Math.max(0, notes),
                wifi.connected ? wifi.level : -1, bat.percent, bat.charging, this::togglePanel),
                new LinearLayout.LayoutParams(-1, -2));
    }

    private void togglePanel() { if (panelOpen) closePanel(); else openPanel(); }

    private void closePanelSilently() {
        if (panelOpen) refreshController.changed(content, Change.OVERLAY);
        panelOpen = false;
        overlay.removeAllViews();
        overlay.setVisibility(View.GONE);
        if (menuOpen) {
            FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) overlay.getLayoutParams();
            bounds.bottomMargin = 0;
            overlay.setLayoutParams(bounds);
        }
        menuOpen = false;
    }

    private void closePanel() {
        boolean wasMenu = menuOpen;
        closePanelSilently();
        if (wasMenu) renderDock();
        updateStatusBar(true);
    }

    /** Control panel for the capabilities available to the launcher. */
    private void openPanel() {
        refreshController.changed(overlay, Change.OVERLAY);
        panelOpen = true;
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        overlay.setOnClickListener(v -> closePanel());

        LinearLayout p = ink.col();
        p.setBackgroundColor(ink.PAPER);
        p.setClickable(true);
        p.setPadding(ink.dp(PAD), ink.dp(18), ink.dp(PAD), ink.dp(8));

        Battery bat = battery();
        Wifi wifi = wifi();
        Date now = new Date();

        LinearLayout top = ink.row();
        top.setGravity(Gravity.BOTTOM);
        LinearLayout left = ink.col();
        left.addView(ink.text(timeText(now), TypeScale.DISPLAY, ink.TEXT));
        TextView date = ink.muted(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), 15);
        date.setPadding(0, ink.dp(6), 0, 0);
        left.addView(date);
        top.addView(left, ink.weight(1));
        LinearLayout right = ink.col();
        right.setGravity(Gravity.END);
        right.addView(ink.text(bat.percent + "%", TypeScale.FIGURE, ink.TEXT));
        TextView bs = ink.muted(bat.charging ? (bat.percent >= 100 ? "Charged" : "Charging") : "On battery", 14);
        bs.setPadding(0, ink.dp(6), 0, 0);
        right.addView(bs);
        top.addView(right);
        p.addView(top);
        p.addView(ink.progress(bat.percent / 100f, 4), ink.m(-1, -2, 0, 14, 0, 16));

        String wifiState = !wifi.enabled ? "Off" : !wifi.connected ? "Not connected"
                : wifi.name != null ? wifi.name
                : new String[]{"Very weak", "Weak", "Fair", "Good", "Strong"}[Math.max(0, Math.min(4, wifi.level))];
        int notes = Notifs.count();
        Capabilities caps = DisplayProbe.capabilities(this);
        List<View> tiles = new ArrayList<>();
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            tiles.add(panelTile("wifi", "Wi-Fi", wifiState, wifi.connected, this::openWifi));
        }
        if (caps.bluetooth) {
            tiles.add(panelTile("bluetooth", "Bluetooth", "Device settings", false,
                    () -> panelSettings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        }
        tiles.add(panelTile("clean", "Clean screen", "Full refresh", false, () -> {
            closePanel();
            ui.post(this::cleanScreen);
        }));
        if (caps.rotation) {
            boolean locked = store.portraitLocked();
            tiles.add(panelTile("rotate", "Rotation", locked ? "Portrait" : "Follow system", locked, () -> {
                store.setPortraitLocked(!locked);
                ActivityWindow.applyOrientation(this, store);
                openPanel();
            }));
        }
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null) {
            int filter = notifications.getCurrentInterruptionFilter();
            boolean quiet = filter == NotificationManager.INTERRUPTION_FILTER_NONE
                    || filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    || filter == NotificationManager.INTERRUPTION_FILTER_ALARMS;
            tiles.add(panelTile("quiet", "Quiet", quiet ? "On" : "Off", quiet, () -> {
                closePanel();
                if (!start(new Intent("android.settings.ZEN_MODE_SETTINGS"))) panelSettings(Settings.ACTION_SOUND_SETTINGS);
            }));
        }
        if (caps.frontlight) {
            tiles.add(panelTile("light", "Light", "Display settings", false,
                    () -> panelSettings(Settings.ACTION_DISPLAY_SETTINGS)));
        }
        for (int i = 0; i < tiles.size(); i += 2) {
            addTiles(p, tiles.get(i), i + 1 < tiles.size() ? tiles.get(i + 1) : new View(this));
        }
        if (caps.speaker) addPanelVolume(p);

        List<Notifs.Item> list = Notifs.items();
        LinearLayout nh = ink.row();
        String caption = notes < 0 ? "Notifications" : "Notifications · " + notes;
        nh.addView(ink.text(caption, TypeScale.LABEL, ink.TEXT), ink.weight(1));
        if (notes < 0) {
            nh.addView(Button.create(ink.folio, "Allow access", Button.Style.TEXT, v -> openNotifications()));
        }
        p.addView(nh, ink.m(-1, -2, 0, 8, 0, 0));
        if (!list.isEmpty()) {
            TextView clear = ink.text("Clear all", TypeScale.META, ink.TEXT2);
            clear.setPadding(ink.dp(10), 0, 0, ink.dp(8));
            clear.setOnClickListener(v -> {
                List<Notifs.Item> removed = Notifs.stageClear();
                closePanel();
                if (!removed.isEmpty()) sheets.notice("Notifications cleared",
                        () -> Notifs.finishClear(removed, false), () -> Notifs.finishClear(removed, true));
            });
            nh.addView(clear);
            LinearLayout card = ink.col();
            int shown = Math.min(3, list.size());
            for (int i = 0; i < shown; i++) {
                Notifs.Item it = list.get(i);
                LinearLayout r = ink.col();
                r.setPadding(ink.dp(14), ink.dp(10), ink.dp(14), ink.dp(10));
                r.setBackground(i < shown - 1 ? ink.listRow(14) : ink.pressable());
                LinearLayout top1 = ink.row();
                top1.addView(ink.oneLine(ink.faint(it.app, 12)), ink.weight(1));
                top1.addView(ink.faint(Library.ago(it.when), 12));
                r.addView(top1);
                TextView t = ink.oneLine(ink.medium(it.title, 15));
                t.setPadding(0, ink.dp(4), 0, 0);
                r.addView(t);
                if (!it.text.isEmpty()) {
                    TextView b2 = ink.lines(ink.muted(it.text, 13), 2);
                    b2.setPadding(0, ink.dp(3), 0, 0);
                    r.addView(b2);
                }
                r.setOnClickListener(v -> {
                    closePanel();
                    try {
                        if (it.open != null) it.open.send();
                    } catch (Exception ignored) { }
                    Notifs.dismiss(it.key);
                    launchedSomething = true;
                });
                card.addView(r);
            }
            if (list.size() > shown) {
                TextView more = ink.muted("+ " + (list.size() - shown) + " more", 13);
                more.setPadding(ink.dp(14), ink.dp(8), ink.dp(14), ink.dp(10));
                more.setOnClickListener(v -> openNotifications());
                card.addView(more);
            }
            p.addView(card);
        }

        p.addView(ink.hair());
        p.addView(Button.create(ink.folio, "Settings", Button.Style.TEXT, v -> go(Screen.SETTINGS)));
        LinearLayout wrap = ink.col();
        wrap.addView(p);
        wrap.addView(ink.hair());
        int width = ink.folio.windowClass() == WindowClass.EXPANDED
                ? Math.min(content.getWidth(), ink.dp(560)) : -1;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.END);
        lp.topMargin = statusBar.getHeight();
        lp.bottomMargin = ink.dp(24);
        overlay.addView(ink.scroller(wrap), lp);
    }

    private void panelSettings(String action) {
        closePanel();
        if (!start(new Intent(action))) toast("Device settings unavailable");
    }

    private void addTiles(LinearLayout parent, View first, View second) {
        LinearLayout row = ink.row();
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -1, 1);
        a.rightMargin = ink.dp(4);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -1, 1);
        b.leftMargin = ink.dp(4);
        row.addView(first, a);
        row.addView(second, b);
        parent.addView(row, ink.m(-1, -2, 0, 0, 0, 8));
    }

    private View panelTile(String glyph, String title, String state, boolean active, Runnable click) {
        return new Tile(ink.folio, glyph, title, state, active, v -> click.run());
    }

    private void addPanelVolume(LinearLayout panel) {
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio == null || audio.isVolumeFixed()) return;
        int stream = AudioManager.STREAM_MUSIC;
        int max = audio.getStreamMaxVolume(stream);
        if (max <= 0) return;
        // A bounded number of stops keeps unusually fine-grained volume scales usable.
        int stops = Math.min(10, max);
        String[] values = new String[stops + 1];
        for (int i = 0; i <= stops; i++) values[i] = Math.round(i * 100f / stops) + "%";
        int selected = Math.round(stops * audio.getStreamVolume(stream) / (float) max);
        panel.addView(ink.text("Volume", TypeScale.LABEL, ink.TEXT));
        panel.addView(new LevelBar(ink.folio, "Volume", values, selected, index -> {
            try {
                audio.setStreamVolume(stream, Math.round(index * max / (float) stops), 0);
            } catch (SecurityException denied) {
                panelSettings(Settings.ACTION_SOUND_SETTINGS);
            }
        }));
    }

    private void openWifi() {
        closePanel();
        if (!start(new Intent(Settings.Panel.ACTION_WIFI))) start(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    // Older Onyx firmware exposes its notification panel through this service name.
    @android.annotation.SuppressLint("WrongConstant")
    private void openNotifications() {
        closePanel();
        if (Notifs.count() < 0) { start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return; }
        try {
            Object sbm = getSystemService("statusbar");
            sbm.getClass().getMethod("expandNotificationsPanel").invoke(sbm);
        } catch (Throwable t) {
            toast("Swipe down from the top edge to see notifications");
        }
    }

    static final class Battery { int percent; boolean charging; }
    static final class Wifi { boolean enabled, connected; int level = -1; String name; }

    private Battery battery() {
        Battery b = new Battery();
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            b.percent = scale > 0 ? level * 100 / scale : 0;
            int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            b.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        }
        return b;
    }

    @SuppressWarnings("deprecation")
    private Wifi wifi() {
        Wifi w = new Wifi();
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (wm == null) return w;
        w.enabled = wm.isWifiEnabled();
        if (cm != null) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            w.connected = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        if (w.enabled && w.connected) {
            android.net.wifi.WifiInfo info = wm.getConnectionInfo();
            w.level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
            // The network name needs location access; without it Android reports "<unknown ssid>".
            String ssid = info.getSSID();
            if (ssid != null && !ssid.contains("unknown ssid")) w.name = ssid.replaceAll("^\"|\"$", "");
        } else if (w.enabled) {
            w.level = 0;
        }
        return w;
    }

    private String freeStorage() {
        return Library.humanSize(new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes());
    }

    private String timeText(Date d) {
        String c = store.clock();
        if (c.equals("24")) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        if (c.equals("12")) return new SimpleDateFormat("h:mm", Locale.getDefault()).format(d);
        return android.text.format.DateFormat.getTimeFormat(this).format(d);
    }

    // ================================================================== home

    private Bitmap wallpaperCache;
    private String wallpaperKey = "";

    /** Home background: the chosen picture in greyscale, faded toward white so text stays crisp. */
    void applyWallpaper() {
        String path = store.wallpaper();
        if (path.isEmpty() || !new File(path).exists()) { content.setBackground(null); return; }
        int w = content.getWidth(), h = content.getHeight();
        if (w == 0 || h == 0) { content.post(this::applyWallpaper); return; }
        String key = path + store.wallpaperFade() + w + "x" + h;
        if (key.equals(wallpaperKey) && wallpaperCache != null) {
            content.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), wallpaperCache));
            return;
        }
        float fade = store.wallpaperFade();
        thumbs.execute(() -> {
            Bitmap pic = SleepScreen.thumb(new File(path), w, h);
            if (pic == null) return;
            Canvas cv = new Canvas(pic);
            Paint white = new Paint();
            white.setColor(Color.WHITE);
            white.setAlpha(Math.round(fade * 255));
            cv.drawRect(0, 0, w, h, white);
            ui.post(() -> {
                wallpaperCache = pic;
                wallpaperKey = key;
                if (screen == Screen.HOME) content.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), pic));
            });
        });
    }

    /** Readblio-style: one big cover and the title, with the reading app underneath. */
    void homeCover(LinearLayout col, List<Library.Book> reading) {
        Library.Book b = !reading.isEmpty() ? reading.get(0) : (books.isEmpty() ? null : newest().get(0));
        if (b == null) {
            col.addView(emptyHero());
            return;
        }
        // With other books to list, the cover is a little smaller so a few of them fit below.
        boolean compact = books.size() > 1;
        int cw = compact ? 136 : 168, chh = compact ? 195 : 240;
        LinearLayout hero = ink.row();
        hero.setGravity(Gravity.TOP);
        hero.setBackground(ink.pressable());
        hero.addView(ink.cover(Covers.get(this, b, store.realCovers())), new LinearLayout.LayoutParams(ink.dp(cw), ink.dp(chh)));

        LinearLayout info = ink.col();
        info.setPadding(ink.dp(20), ink.dp(2), 0, 0);
        if (reading.isEmpty()) {
            TextView n = ink.faint("Newest in your library", 13);
            n.setPadding(0, 0, 0, ink.dp(8));
            info.addView(n);
        }
        info.addView(ink.lines(ink.text(b.title, TypeScale.TITLE, ink.TEXT), compact ? 3 : 4));
        if (!b.author.isEmpty()) {
            TextView a = ink.oneLine(ink.muted(b.author, 16));
            a.setPadding(0, ink.dp(8), 0, 0);
            info.addView(a);
        }
        info.addView(ink.vflex());

        LinearLayout app = ink.row();
        ResolveInfo reader = readerFor(b);
        if (reader != null || readerPkgFor(b).equals(Store.BUILT_IN)) {
            info.addView(app);
            app.addView(reader != null ? appIcon(reader.loadIcon(getPackageManager()), 20) : ink.icon("book", 20));
            String rlabel = reader != null ? String.valueOf(reader.loadLabel(getPackageManager())) : "Ink Home reader";
            TextView rn = ink.oneLine(ink.medium(rlabel, 15));
            rn.setPadding(ink.dp(8), 0, 0, 0);
            app.addView(rn, ink.weight(1));
        }
        if (b.progress >= 0 || b.finished) info.addView(ink.progress(b.finished ? 1f : b.progress, 4), ink.m(-1, -2, 0, 14, 0, 0));
        TextView st = ink.oneLine(ink.muted(bookStatus(b) + (b.lastRead > 0 ? "  ·  " + Library.ago(b.lastRead) : ""), 14));
        st.setPadding(0, ink.dp(8), 0, 0);
        info.addView(st);
        hero.addView(info, new LinearLayout.LayoutParams(0, ink.dp(chh), 1f));
        hero.setOnClickListener(v -> openBook(b));
        hero.setOnLongClickListener(v -> { bookMenu(b); return true; });
        col.addView(hero);

        // Every other book, the ones being read first, whichever app they're read in.
        List<Library.Book> rest = new ArrayList<>();
        for (Library.Book x : reading) if (x != b) rest.add(x);
        for (Library.Book x : newest()) if (x != b && !rest.contains(x)) rest.add(x);
        if (!rest.isEmpty()) col.addView(homeBookList(rest), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    /** Compact rows of books that fill whatever space is left, with a link to the full library. */
    private View homeBookList(List<Library.Book> list) {
        LinearLayout head = ink.row();
        head.addView(ink.heading("Your books"), ink.weight(1));
        View listView = cells("home", head, list.size(), 0, 46, i -> homeBookRow(list.get(i)));
        listView.setTag("list");
        return listView;
    }

    private View homeBookRow(Library.Book bk) {
        {
            {
                LinearLayout r = ink.row();
                r.setBackground(ink.listRow(40));
                r.addView(ink.cover(Covers.get(this, bk, store.realCovers())), ink.m(ink.dp(28), ink.dp(40), 0, 0, 12, 0));
                LinearLayout t = ink.col();
                t.addView(ink.oneLine(ink.text(bk.title, TypeScale.BODY_LG, ink.TEXT)));
                String meta = (bk.author.isEmpty() ? bk.format() : bk.author) + "  ·  " + bookStatus(bk);
                TextView m = ink.oneLine(ink.muted(meta, 12));
                m.setPadding(0, ink.dp(3), 0, 0);
                t.addView(m);
                r.addView(t, ink.weight(1));
                if (bk.progress >= 0 && !bk.finished) r.addView(ink.progress(bk.progress, 3), ink.m(ink.dp(44), -2, 10, 0, 0, 0));
                r.setOnClickListener(v -> openBook(bk));
                r.setOnLongClickListener(v -> { bookMenu(bk); return true; });
                return r;
            }
        }
    }

    /** Same layout as a book, with an empty cover outline, for when there's nothing to show yet. */
    private View emptyHero() {
        LinearLayout hero = ink.row();
        hero.setGravity(Gravity.TOP);
        LinearLayout cover = ink.col();
        cover.setGravity(Gravity.CENTER);
        cover.setBackground(ink.stroke(0xFFF4F4F4, 4, 0xFFB5B5B5));
        ImageView icon = ink.icon("book", 40);
        icon.setAlpha(0.4f);
        cover.addView(icon);
        hero.addView(cover, new LinearLayout.LayoutParams(ink.dp(168), ink.dp(240)));
        LinearLayout info = ink.col();
        info.setPadding(ink.dp(20), ink.dp(2), 0, 0);
        info.addView(ink.lines(ink.text(booksLoaded ? "No book yet" : "Opening your library…", TypeScale.TITLE, ink.TEXT), 3));
        TextView sub = ink.muted(booksLoaded ? "Copy EPUB, PDF, MOBI or CBZ files over USB or BooxDrop and they'll appear here." : "Finding books on this tablet.", 15);
        sub.setLineSpacing(0, 1.2f);
        sub.setPadding(0, ink.dp(10), 0, 0);
        info.addView(sub);
        hero.addView(info, new LinearLayout.LayoutParams(0, ink.dp(240), 1f));
        hero.setBackground(ink.pressable());
        hero.setOnClickListener(v -> go(Screen.LIBRARY));
        return hero;
    }

    private View miniBook(Library.Book b, int w) {
        LinearLayout c = ink.col();
        c.setPadding(ink.dp(2), ink.dp(2), ink.dp(10), ink.dp(4));
        c.setBackground(ink.pressable());
        c.addView(ink.cover(Covers.get(this, b, store.realCovers())), new LinearLayout.LayoutParams(ink.dp(w), Math.round(ink.dp(w) * 1.43f)));
        if (b.progress >= 0) c.addView(ink.progress(b.progress, 3), ink.m(ink.dp(w), -2, 0, 6, 0, 0));
        TextView t = ink.lines(ink.text(b.title, TypeScale.META, ink.TEXT), 2);
        t.setPadding(0, ink.dp(6), 0, 0);
        c.addView(t, new LinearLayout.LayoutParams(ink.dp(w + 8), -2));
        c.setOnClickListener(v -> openBook(b));
        c.setOnLongClickListener(v -> { bookMenu(b); return true; });
        return c;
    }

    void homeShelf(LinearLayout col, List<Library.Book> reading) {
        List<Library.Book> shelf = new ArrayList<>(reading);
        for (Library.Book b : newest()) {
            if (shelf.size() >= 6) break;
            if (!shelf.contains(b) && !b.finished) shelf.add(b);
        }
        col.addView(ink.heading(reading.isEmpty() ? "Your books" : "Reading"));
        if (shelf.isEmpty()) {
            col.addView(note(booksLoaded ? "No books yet" : "Opening your library…", "Copy books to the tablet over USB or BooxDrop.", null));
            return;
        }
        int rows = ink.scale <= 1.16f ? 2 : 1;
        for (int r = 0; r < rows; r++) {
            LinearLayout line = ink.row();
            line.setGravity(Gravity.TOP);
            for (int i = 0; i < 3; i++) {
                int idx = r * 3 + i;
                if (idx < shelf.size()) line.addView(miniBook(shelf.get(idx), 104), ink.weight(1));
                else line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
            col.addView(line, ink.m(-1, -2, 0, 0, 0, 14));
        }
    }

    void homeMinimal(LinearLayout col, List<Library.Book> reading) {
        Date now = new Date();
        TextView clock = ink.center(ink.text(timeText(now), TypeScale.DISPLAY, ink.TEXT));
        col.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        TextView date = ink.center(ink.muted(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), 17));
        date.setPadding(0, ink.dp(10), 0, ink.dp(30));
        col.addView(date, new LinearLayout.LayoutParams(-1, -2));
        if (reading.isEmpty()) return;
        Library.Book b = reading.get(0);
        LinearLayout c = ink.col();
        c.setPadding(ink.dp(18), ink.dp(16), ink.dp(18), ink.dp(16));
        c.setBackground(ink.card(12, true));
        c.addView(ink.faint("Continue reading", 13));
        TextView t = ink.lines(ink.text(b.title, TypeScale.TITLE, ink.TEXT), 2);
        t.setPadding(0, ink.dp(8), 0, ink.dp(8));
        c.addView(t);
        c.addView(ink.muted(bookStatus(b), 14));
        if (b.progress >= 0) c.addView(ink.progress(b.progress, 4), ink.m(-1, -2, 0, 10, 0, 0));
        c.setOnClickListener(v -> openBook(b));
        col.addView(c);
    }

    void homeFavorites(LinearLayout col) {
        List<App> favs = favoriteApps();
        col.addView(ink.heading("Favorites"), ink.m(-1, -2, 0, 10, 0, 0));
        col.addView(ink.hair());
        if (favs.isEmpty()) {
            TextView h = ink.muted("Long-press an app in Apps to add it here.", 14);
            h.setPadding(0, ink.dp(14), 0, ink.dp(10));
            col.addView(h);
            return;
        }
        boolean icons = store.appIcons();
        int max = ink.scale > 1.16f ? 4 : 8;
        LinearLayout line = null;
        for (int i = 0; i < Math.min(max, favs.size()); i++) {
            if (i % 4 == 0) {
                line = ink.row();
                line.setGravity(Gravity.TOP);
                col.addView(line, ink.m(-1, -2, 0, 10, 0, 0));
            }
            App a = favs.get(i);
            LinearLayout cell = ink.col();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(ink.dp(2), ink.dp(6), ink.dp(2), ink.dp(6));
            cell.setBackground(ink.pressable());
            if (icons) cell.addView(appIcon(a, 36));
            TextView t = ink.center(ink.oneLine(ink.text(displayLabel(a), TypeScale.APP_NAME, ink.TEXT)));
            t.setPadding(0, icons ? ink.dp(8) : ink.dp(12), 0, icons ? 0 : ink.dp(12));
            cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            cell.setOnClickListener(v -> launch(a));
            cell.setOnLongClickListener(v -> { appMenu(a); return true; });
            line.addView(cell, ink.weight(1));
        }
        if (line != null) while (line.getChildCount() < 4) line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
    }

    /** Bordered card with a title and a muted explanation. */
    View note(String title, String body, Runnable click) {
        LinearLayout c = ink.col();
        c.setPadding(ink.dp(18), ink.dp(16), ink.dp(18), ink.dp(16));
        c.setBackground(ink.card(12, click != null));
        c.addView(ink.medium(title, 18));
        TextView s = ink.muted(body, 14);
        s.setPadding(0, ink.dp(8), 0, 0);
        s.setLineSpacing(0, 1.2f);
        c.addView(s);
        if (click != null) c.setOnClickListener(v -> click.run());
        return c;
    }

    // ================================================================== library (paged)

    List<Library.Book> filterBooks(boolean searching) {
        List<Library.Book> out = new ArrayList<>();
        String type = store.libraryType();
        List<String> inCollection = libCollection != null ? store.collections().get(libCollection) : null;
        String q = searching && libSearch != null ? libSearch.trim().toLowerCase(Locale.getDefault()) : "";
        for (Library.Book b : books) {
            if (searching) {
                if (q.isEmpty()) continue;
                if (b.title.toLowerCase(Locale.getDefault()).contains(q) || b.author.toLowerCase(Locale.getDefault()).contains(q)
                        || b.file.getName().toLowerCase(Locale.getDefault()).contains(q)) out.add(b);
                continue;
            }
            if (type.equals("book") && b.kind != Library.Kind.BOOK) continue;
            if (type.equals("comic") && b.kind != Library.Kind.COMIC) continue;
            if (type.equals("pdf") && b.kind != Library.Kind.PDF) continue;
            if (inCollection != null) {
                if (!inCollection.contains(b.path)) continue;
            } else if (libTab.equals("reading")) {
                if (!b.started() || b.finished || store.dismissedAt(b.path) >= b.lastRead) continue;
            } else if (libTab.equals("finished")) {
                if (!b.finished) continue;
            }
            out.add(b);
        }
        switch (searching ? "title" : store.librarySort()) {
            case "title": out.sort((a, b) -> a.title.compareToIgnoreCase(b.title)); break;
            case "author": out.sort((a, b) -> {
                if (a.author.isEmpty() != b.author.isEmpty()) return a.author.isEmpty() ? 1 : -1;
                int c = a.author.compareToIgnoreCase(b.author);
                return c != 0 ? c : a.title.compareToIgnoreCase(b.title);
            }); break;
            case "added": out.sort((a, b) -> Long.compare(b.modified, a.modified)); break;
            case "progress": out.sort((a, b) -> Float.compare(b.finished ? 2 : b.progress, a.finished ? 2 : a.progress)); break;
            default: out.sort((a, b) -> a.lastRead != b.lastRead ? Long.compare(b.lastRead, a.lastRead) : Long.compare(b.modified, a.modified));
        }
        return out;
    }

    View bookPages(List<Library.Book> list, boolean grid) {
        if (grid) {
            return cells("lib", null, list.size(), 96, 208, i -> {
                Library.Book b = list.get(i);
                LinearLayout cell = ink.col();
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(ink.dp(4), ink.dp(14), ink.dp(4), 0);
                cell.setBackground(ink.pressable());
                cell.addView(ink.cover(Covers.get(this, b, store.realCovers())), new LinearLayout.LayoutParams(ink.dp(88), ink.dp(126)));
                View bar = b.progress >= 0 || b.finished ? ink.progress(b.finished ? 1f : b.progress, 3) : new View(this);
                cell.addView(bar, ink.m(ink.dp(88), ink.dp(3), 0, 6, 0, 0));
                TextView t = ink.center(ink.lines(ink.text(b.title, TypeScale.META, ink.TEXT), 2));
                t.setPadding(ink.dp(4), ink.dp(7), ink.dp(4), 0);
                cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
                cell.setOnClickListener(v -> openBook(b));
                cell.setOnLongClickListener(v -> { bookMenu(b); return true; });
                return cell;
            });
        }
        return cells("lib", null, list.size(), 0, 88, i -> {
            Library.Book b = list.get(i);
            LinearLayout r = ink.row();
            r.setBackground(ink.listRow(62));
            r.addView(ink.cover(Covers.get(this, b, store.realCovers())), new LinearLayout.LayoutParams(ink.dp(46), ink.dp(66)));
            LinearLayout info = ink.col();
            info.setPadding(ink.dp(16), 0, 0, 0);
            info.addView(ink.oneLine(ink.text(b.title, TypeScale.BODY_LG, ink.TEXT)));
            String meta = (b.author.isEmpty() ? b.format() : b.author) + "  ·  " + bookStatus(b);
            TextView a = ink.oneLine(ink.muted(meta, 13));
            a.setPadding(0, ink.dp(6), 0, 0);
            info.addView(a);
            if (b.progress >= 0 && !b.finished) info.addView(ink.progress(b.progress, 3), ink.m(ink.dp(120), -2, 0, 8, 0, 0));
            r.addView(info, ink.weight(1));
            r.setOnClickListener(v -> openBook(b));
            r.setOnLongClickListener(v -> { bookMenu(b); return true; });
            return r;
        });
    }

    View collectionsList() {
        Map<String, List<String>> cols = store.collections();
        List<String> names = new ArrayList<>(cols.keySet());
        Set<String> existing = new HashSet<>();
        for (Library.Book b : books) existing.add(b.path);
        return cells("lib", null, names.size() + 1, 0, 60, i -> {
            LinearLayout r = ink.row();
            r.setBackground(ink.listRow(0));
            if (i == names.size()) {
                r.addView(ink.icon("plus", 18));
                TextView t = ink.medium("New collection", 16);
                t.setPadding(ink.dp(12), 0, 0, 0);
                r.addView(t);
                r.setOnClickListener(v -> askText("New collection", "", name -> {
                    Map<String, List<String>> m = store.collections();
                    if (!m.containsKey(name)) m.put(name, new ArrayList<>());
                    store.setCollections(m);
                    render();
                }));
                return r;
            }
            String name = names.get(i);
            int n = 0;
            for (String p : cols.get(name)) if (existing.contains(p)) n++;
            r.addView(ink.oneLine(ink.medium(name, 16)), ink.weight(1));
            r.addView(ink.faint(String.valueOf(n), 15));
            r.addView(ink.icon("chevron", 14), ink.m(ink.dp(14), ink.dp(14), 10, 0, 0, 0));
            r.setOnClickListener(v -> { libCollection = name; pages.put("lib", 0); render(); });
            r.setOnLongClickListener(v -> {
                choose(name, new String[]{"Rename", "Delete collection"}, -1, w -> {
                    Map<String, List<String>> m = store.collections();
                    if (w == 0) askText("Rename collection", name, nn -> {
                        Map<String, List<String>> out = new LinkedHashMap<>();
                        for (Map.Entry<String, List<String>> e : m.entrySet()) out.put(e.getKey().equals(name) ? nn : e.getKey(), e.getValue());
                        store.setCollections(out);
                        render();
                    });
                    else {
                        m.remove(name);
                        store.setCollections(m);
                        render();
                        sheets.notice("Collection deleted", () -> {
                            store.setCollections(cols);
                            render();
                        }, null);
                    }
                });
                return true;
            });
            return r;
        });
    }

    void chooseSort() {
        String[] keys = {"recent", "title", "author", "added", "progress"};
        choose("Sort by", new String[]{"Recently read", "Title", "Author", "Recently added", "Progress"},
                Arrays.asList(keys).indexOf(store.librarySort()), w -> { store.setLibrarySort(keys[w]); pages.put("lib", 0); render(); });
    }

    void chooseType() {
        String[] keys = {"all", "book", "comic", "pdf"};
        choose("Show", new String[]{"All types", "Books (EPUB, MOBI, FB2, TXT…)", "Comics (CBZ, CBR)", "PDFs and DJVU"},
                Arrays.asList(keys).indexOf(store.libraryType()), w -> { store.setLibraryType(keys[w]); pages.put("lib", 0); render(); });
    }

    private void bookMenu(Library.Book b) {
        List<String> opts = new ArrayList<>();
        opts.add("Open");
        opts.add("Open with…");
        opts.add("Add to collection…");
        if (libCollection != null) opts.add("Remove from this collection");
        opts.add(b.finished ? "Mark as not finished" : "Mark as finished");
        if (b.started() && !b.finished) opts.add("Remove from Reading");
        opts.add("Details");
        choose(b.title, opts.toArray(new String[0]), -1, w -> {
            String o = opts.get(w);
            switch (o) {
                case "Open": openBook(b); break;
                case "Open with…": openWith(b); break;
                case "Add to collection…": addToCollection(b); break;
                case "Remove from this collection": {
                    Map<String, List<String>> m = store.collections();
                    List<String> l = m.get(libCollection);
                    if (l == null || !l.contains(b.path)) break;
                    final String collection = libCollection;
                    final int position = l.indexOf(b.path);
                    l.remove(position);
                    store.setCollections(m);
                    render();
                    sheets.notice("Removed from collection", () -> {
                        Map<String, List<String>> current = store.collections();
                        List<String> members = current.get(collection);
                        if (members != null && !members.contains(b.path)) {
                            members.add(Math.min(position, members.size()), b.path);
                            store.setCollections(current);
                        }
                        render();
                    }, null);
                    break;
                }
                case "Remove from Reading": {
                    long previous = store.dismissedAt(b.path);
                    store.dismiss(b.path);
                    render();
                    sheets.notice("Removed from Reading", () -> {
                        store.setDismissedAt(b.path, previous);
                        render();
                    }, null);
                    break;
                }
                case "Details": {
                    String msg = (b.author.isEmpty() ? "" : b.author + "\n\n") + bookStatus(b)
                            + (b.pages.isEmpty() ? "" : "\nPage " + b.pages)
                            + (b.lastRead > 0 ? "\nLast read " + Library.ago(b.lastRead) : "")
                            + "\n\n" + b.format() + "  ·  " + Library.humanSize(b.size) + "\n" + b.path;
                    sheets.message(b.title, msg, "Book actions", () -> bookMenu(b));
                    break;
                }
                default:
                    b.finished = !b.finished;
                    store.setFinished(b.path, b.finished);
                    dataSignature = 0;
                    render();
            }
        });
    }

    private void addToCollection(Library.Book b) {
        Map<String, List<String>> m = store.collections();
        List<String> names = new ArrayList<>(m.keySet());
        names.add("New collection…");
        choose("Add to collection", names.toArray(new String[0]), -1, w -> {
            if (w == names.size() - 1) {
                askText("New collection", "", name -> {
                    Map<String, List<String>> mm = store.collections();
                    List<String> l = mm.containsKey(name) ? mm.get(name) : new ArrayList<>();
                    if (!l.contains(b.path)) l.add(b.path);
                    mm.put(name, l);
                    store.setCollections(mm);
                    toast("Added to " + name);
                });
            } else {
                List<String> l = m.get(names.get(w));
                if (!l.contains(b.path)) l.add(b.path);
                store.setCollections(m);
                toast("Added to " + names.get(w));
            }
        });
    }

    // ================================================================== opening books

    /** EPUB and TXT open in the built-in reader by default; other formats need another app. */
    private static boolean builtInCanOpen(Library.Book b) { return b.ext.equals("epub") || b.ext.equals("txt"); }

    /**
     * The reader chosen for this kind of book: Store.BUILT_IN, an app package, or "ask".
     * Books default to the built-in reader; PDFs and comics default to NeoReader.
     */
    private String readerPkgFor(Library.Book b) {
        boolean bookKind = b.kind == Library.Kind.BOOK;
        String p = bookKind ? store.bookReader() : store.pdfReader();
        if (bookKind && (p.isEmpty() || p.equals(Store.BUILT_IN))) {
            if (builtInCanOpen(b)) return Store.BUILT_IN;
            p = "";
        }
        if (p.isEmpty()) return installed(NEOREADER_PKG) ? NEOREADER_PKG : "ask";
        if (!p.equals("ask") && !installed(p)) return installed(NEOREADER_PKG) ? NEOREADER_PKG : "ask";
        return p;
    }

    private ResolveInfo readerFor(Library.Book b) {
        String pkg = readerPkgFor(b);
        if (pkg.equals("ask") || pkg.equals(Store.BUILT_IN)) return null;
        Intent i = viewIntent(b, pkg);
        List<ResolveInfo> r = getPackageManager().queryIntentActivities(i, 0);
        if (!r.isEmpty()) return r.get(0);
        try {
            return getPackageManager().resolveActivity(getPackageManager().getLaunchIntentForPackage(pkg), 0);
        } catch (Exception e) {
            return null;
        }
    }

    private Intent viewIntent(Library.Book b, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        if (NEOREADER_PKG.equals(pkg)) {
            i.setDataAndType(Uri.fromFile(b.file), Library.mime(b.ext));
            i.setComponent(new ComponentName(NEOREADER_PKG, NEOREADER_CLS));
        } else {
            i.setDataAndType(Files.uriFor(b.file), Library.mime(b.ext));
            if (pkg != null && !pkg.equals("ask")) i.setPackage(pkg);
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    void openBook(Library.Book b) {
        store.markOpened(b.path);
        String pkg = readerPkgFor(b);
        if (pkg.equals(Store.BUILT_IN) && start(ReaderActivity.intent(this, b.file))) return;
        if (!pkg.equals("ask") && !pkg.equals(Store.BUILT_IN)) {
            Intent intent = viewIntent(b, pkg);
            // KOReader can read the URI after its receiving activity finishes.
            if ("org.koreader.launcher".equals(pkg)) {
                grantUriPermission(pkg, intent.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (start(intent)) return;
        }
        openWith(b);
    }

    private void openWith(Library.Book b) {
        Intent view = viewIntent(b, null);
        List<ResolveInfo> candidates = getPackageManager().queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY);
        List<String> labels = new ArrayList<>();
        List<Intent> intents = new ArrayList<>();
        if (builtInCanOpen(b)) {
            labels.add("Ink Home reader");
            intents.add(ReaderActivity.intent(this, b.file));
        }
        for (ResolveInfo ri : candidates) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            Intent explicit = NEOREADER_PKG.equals(pkg) ? viewIntent(b, pkg) :
                    new Intent(view).setComponent(new ComponentName(pkg, ri.activityInfo.name));
            labels.add(String.valueOf(ri.loadLabel(getPackageManager())));
            intents.add(explicit);
        }
        if (intents.isEmpty()) { toast("No app can open " + b.format() + " files"); return; }
        choose("Open with", labels.toArray(new String[0]), -1, w -> {
            Intent intent = intents.get(w);
            if (intent.getComponent() != null && "org.koreader.launcher".equals(intent.getComponent().getPackageName())) {
                grantUriPermission("org.koreader.launcher", intent.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (start(intent)) store.markOpened(b.path);
            else toast("Couldn't open " + labels.get(w));
        });
    }

    /** Apps that can open this kind of file. */
    private List<ResolveInfo> readersFor(String ext) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://" + Files.AUTHORITY + "/x." + ext), Library.mime(ext));
        List<ResolveInfo> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : getPackageManager().queryIntentActivities(i, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName()) || !seen.add(p)) continue;
            out.add(ri);
        }
        return out;
    }

    void chooseReader(boolean booksKind) {
        List<ResolveInfo> readers = readersFor(booksKind ? "epub" : "pdf");
        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        if (booksKind) {
            labels.add("Ink Home reader");
            pkgs.add(Store.BUILT_IN);
        }
        for (ResolveInfo ri : readers) {
            labels.add(String.valueOf(ri.loadLabel(getPackageManager())));
            pkgs.add(ri.activityInfo.packageName);
        }
        labels.add("Ask every time");
        pkgs.add("ask");
        String cur = booksKind ? store.bookReader() : store.pdfReader();
        if (cur.isEmpty()) cur = booksKind ? Store.BUILT_IN : NEOREADER_PKG;
        choose(booksKind ? "Open books with" : "Open PDFs and comics with", labels.toArray(new String[0]), pkgs.indexOf(cur), w -> {
            if (booksKind) store.setBookReader(pkgs.get(w)); else store.setPdfReader(pkgs.get(w));
            render();
        });
    }

    String readerName(String pkg) {
        if (pkg.equals(Store.BUILT_IN)) return "Ink Home reader";
        if (pkg.isEmpty()) pkg = NEOREADER_PKG;
        if (pkg.equals("ask")) return "Ask every time";
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return "NeoReader";
        }
    }

    // ================================================================== reading stats

    /** Two equal-height cards side by side. */
    View metricRow(View a, View b) {
        LinearLayout r = ink.row();
        r.setGravity(Gravity.TOP);
        r.addView(a, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, -1, 1f);
        pb.leftMargin = ink.dp(10);
        r.addView(b, pb);
        return r;
    }

    View metric(String label, String value, String sub) {
        LinearLayout t = ink.col();
        t.setPadding(ink.dp(16), ink.dp(14), ink.dp(12), ink.dp(14));
        t.setBackground(ink.card(12, false));
        t.addView(ink.faint(label, 13));
        TextView v = ink.oneLine(ink.text(value, TypeScale.TITLE_SM, ink.TEXT));
        v.setPadding(0, ink.dp(8), 0, 0);
        t.addView(v);
        if (sub != null) {
            TextView s = ink.muted(sub, 12);
            s.setPadding(0, ink.dp(5), 0, 0);
            t.addView(s);
        }
        return t;
    }

    /** Seven bars: black when the day met the goal (or any reading without a goal), outlined otherwise. */
    final class WeekChart extends View {
        private final Stats s;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        WeekChart(Context c, Stats s) {
            super(c);
            this.s = s;
        }

        @Override protected void onDraw(Canvas c) {
            Calendar[] week = s.week();
            int max = Math.max(1, s.goal);
            for (Calendar d : week) max = Math.max(max, s.on(d));
            float w = getWidth(), h = getHeight();
            float labelH = ink.dp(22), valueH = ink.dp(16);
            float top = valueH, bottom = h - labelH;
            float slot = w / 7f, barW = slot * 0.42f, r = ink.dp(3);
            Calendar today = Calendar.getInstance();
            SimpleDateFormat dn = new SimpleDateFormat("EEE", Locale.getDefault());
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < 7; i++) {
                int m = s.on(week[i]);
                float x = slot * i + slot / 2f;
                float bh = (bottom - top) * m / (float) max;
                boolean isToday = week[i].get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && week[i].get(Calendar.YEAR) == today.get(Calendar.YEAR);
                boolean strong = s.hasGoal() ? m >= s.goal : m > 0;
                if (m > 0) {
                    p.setColor(strong ? ink.TEXT : 0xFF9A9A9A);
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(x - barW / 2f, bottom - Math.max(bh, ink.dp(4)), x + barW / 2f, bottom, r, r, p);
                    p.setColor(ink.TEXT2);
                    p.setTextSize(ink.dp(11));
                    p.setTypeface(ink.REGULAR);
                    c.drawText(String.valueOf(m), x, bottom - Math.max(bh, ink.dp(4)) - ink.dp(5), p);
                } else {
                    p.setColor(0xFFD6D6D6);
                    c.drawRoundRect(x - barW / 2f, bottom - ink.dp(2), x + barW / 2f, bottom, 1, 1, p);
                }
                p.setColor(isToday ? ink.TEXT : ink.TEXT3);
                p.setTextSize(ink.dp(12));
                p.setTypeface(isToday ? ink.MEDIUM : ink.REGULAR);
                c.drawText(dn.format(week[i].getTime()), x, h - ink.dp(4), p);
            }
            if (s.hasGoal()) {
                float gy = bottom - (bottom - top) * s.goal / (float) max;
                Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
                dash.setColor(ink.TEXT3);
                dash.setStyle(Paint.Style.STROKE);
                dash.setStrokeWidth(Math.max(1, ink.dp(1)));
                dash.setPathEffect(new DashPathEffect(new float[]{ink.dp(4), ink.dp(4)}, 0));
                c.drawLine(0, gy, w, gy, dash);
            }
        }
    }

    void chooseGoal() {
        int[] goals = {0, 10, 15, 20, 30, 45, 60, 90, 120};
        String[] labels = new String[goals.length];
        int cur = 0;
        for (int i = 0; i < goals.length; i++) {
            labels[i] = goals[i] == 0 ? "No goal, just show time read" : goals[i] + " minutes a day";
            if (goals[i] == store.dailyGoal()) cur = i;
        }
        choose("Daily goal", labels, cur, w -> {
            store.setDailyGoal(goals[w]);
            io.execute(() -> {
                Stats st = Stats.refresh(this, store);
                ui.post(() -> { stats = st; render(); });
            });
        });
    }

    // ================================================================== apps (scrolling)

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            out.add(new App(ri, new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), String.valueOf(ri.loadLabel(pm))));
        }
        out.sort((a, b) -> displayLabel(a).compareToIgnoreCase(displayLabel(b)));
        apps = out;
        iconCache.clear();
    }

    private void seedFavorites() {
        List<String> want = new ArrayList<>();
        String[] defaults = {BOOX_HOME, "org.chromium.chrome/com.google.android.apps.chrome.Main",
                "com.onyx.dict/com.onyx.dict.main.ui.DictMainActivity", "com.android.documentsui/com.android.documentsui.LauncherActivity"};
        for (String d : defaults) if (findApp(d) != null) want.add(d);
        store.setFavorites(want);
    }

    App findApp(String key) {
        for (App a : apps) if (a.key.equals(key)) return a;
        return null;
    }

    private List<App> favoriteApps() {
        List<App> out = new ArrayList<>();
        for (String k : store.favorites()) {
            App a = findApp(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    String displayLabel(App a) { return a.label; }

    ImageView appIcon(App a, int sizeDp) {
        Drawable d = iconCache.get(a.key);
        if (d == null) {
            d = a.ri.loadIcon(getPackageManager());
            iconCache.put(a.key, d);
        }
        return appIcon(d, sizeDp);
    }

    private ImageView appIcon(Drawable d, int sizeDp) {
        ImageView iv = ink.icon(d, sizeDp);
        if (ink.folio.displayClass != dev.inkhome.folio.core.DisplayClass.MONO_EINK) return iv;
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0);
        iv.setColorFilter(new ColorMatrixColorFilter(m));
        return iv;
    }

    void appMenu(App a) {
        boolean fav = store.isFavorite(a.key);
        boolean reads = store.readingApps().contains(a.pkg);
        List<String> opts = new ArrayList<>();
        opts.add("Open");
        opts.add(fav ? "Remove from favorites" : "Add to favorites");
        if (fav) opts.add("Move to first favorite");
        opts.add(reads ? "Don't count as reading" : "Count time as reading");
        opts.add("Hide from Apps");
        opts.add("App info");
        choose(displayLabel(a), opts.toArray(new String[0]), -1, w -> {
            switch (opts.get(w)) {
                case "Open": launch(a); break;
                case "Add to favorites": case "Remove from favorites": store.toggleFavorite(a.key); render(); break;
                case "Move to first favorite": {
                    List<String> l = store.favorites();
                    l.remove(a.key);
                    l.add(0, a.key);
                    store.setFavorites(l);
                    render();
                    break;
                }
                case "Count time as reading": case "Don't count as reading":
                    if (a.pkg.equals(NEOREADER_PKG)) { toast("NeoReader always counts"); break; }
                    store.setReadingApp(a.pkg, !reads);
                    render();
                    break;
                case "Hide from Apps": {
                    store.setHidden(a.key, true);
                    List<String> f = store.favorites();
                    f.remove(a.key);
                    store.setFavorites(f);
                    render();
                    break;
                }
                case "App info": start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); break;
            }
        });
    }

    void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!start(i)) toast("Couldn't open " + a.label);
    }

    // ================================================================== settings (scrolling, grouped)

    static int settingIndex(int[] values, int value) {
        int closest = 0;
        for (int i = 1; i < values.length; i++)
            if (Math.abs(values[i] - value) < Math.abs(values[closest] - value)) closest = i;
        return closest;
    }

    void refreshReadingSettings() {
        io.execute(() -> {
            Stats updated = Stats.refresh(this, store);
            ui.post(() -> { stats = updated; render(); });
        });
    }

    final class Rows {
        final List<View> views = new ArrayList<>();
        final boolean compact = ink.folio.windowClass() == WindowClass.COMPACT;

        void add(String label, String value, Runnable click) {
            LinearLayout row = ink.row();
            LinearLayout text = compact ? ink.col() : ink.row();
            text.addView(ink.body(label), compact ? new LinearLayout.LayoutParams(-1, -2) : ink.weight(1));
            if (value != null && !value.isEmpty())
                text.addView(ink.lines(ink.muted(value, 15), 2), compact ? new LinearLayout.LayoutParams(-1, -2) : ink.weight(1));
            row.addView(text, ink.weight(1));
            row.addView(ink.icon("chevron", 13), ink.m(ink.dp(13), ink.dp(13), 10, 0, 0, 0));
            row.setOnClickListener(v -> click.run());
            append(row);
        }

        void control(String label, View widget, boolean small) {
            boolean stacked = compact && !small;
            LinearLayout row = stacked ? ink.col() : ink.row();
            row.addView(ink.body(label), stacked ? new LinearLayout.LayoutParams(-1, -2) : ink.weight(1));
            LinearLayout.LayoutParams params;
            if (stacked) params = ink.m(-1, -2, 0, 6, 0, 0);
            else if (widget instanceof Segmented || widget instanceof LevelBar) {
                params = ink.weight(1);
                params.leftMargin = ink.dp(8);
            } else params = ink.m(-2, -2, 8, 0, 0, 0);
            row.addView(widget, params);
            if (widget instanceof FolioSwitch) {
                row.setOnClickListener(v -> widget.performClick());
                row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            append(row);
        }

        void append(LinearLayout row) {
            row.setPadding(ink.dp(12), ink.dp(6), ink.dp(12), ink.dp(6));
            row.setMinimumHeight(ink.dp(52));
            views.add(item(row));
        }

        void toggle(String label, boolean checked, java.util.function.Consumer<Boolean> change) {
            control(label, new FolioSwitch(ink.folio, label, checked, change), true);
        }

        void segments(String label, String[] choices, int selected, java.util.function.IntConsumer change) {
            control(label, new Segmented(ink.folio, label, choices, Math.max(0, selected), change), false);
        }

        void scale(String label, int[] values, float current, java.util.function.IntConsumer change) {
            String[] labels = new String[values.length];
            for (int i = 0; i < values.length; i++) labels[i] = values[i] + "%";
            int percent = Math.round(current * 100);
            control(label + " · " + percent + "%", new LevelBar(ink.folio, label, labels, settingIndex(values, percent),
                    i -> change.accept(values[i])), false);
        }
    }

    interface GroupBuilder { void build(Rows rows); }

    /** A Linear-style settings group: grey heading, then rows inside one rounded card. */
    void group(LinearLayout body, String title, GroupBuilder build) {
        // Heading + card form one section, which pages keep together whenever it fits.
        LinearLayout section = keep(ink.col());
        body.addView(section, ink.m(-1, -2, 0, body.getChildCount() == 0 ? 0 : 22, 0, 0));
        section.addView(ink.heading(title));
        LinearLayout card = ink.col();
        card.setBackground(ink.card(12, false));
        Rows rows = new Rows();
        build.build(rows);
        for (int i = 0; i < rows.views.size(); i++) {
            View row = rows.views.get(i);
            row.setBackground(i < rows.views.size() - 1 ? ink.listRow(16) : ink.pressable());
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        section.addView(card);
    }

    // ================================================================== sleep screen

    void updateSleepPicture(boolean force) {
        if (!store.sleepMode().equals("book") || !hasFileAccess()) return;
        List<Library.Book> r = reading();
        Library.Book b = r.isEmpty() ? null : r.get(0);
        String sig = b == null ? "none" : b.key() + b.title + b.author + Covers.pending(this, b) + store.realCovers();
        if (!force && sig.equals(sleepSignature)) return;
        sleepSignature = sig;
        boolean real = store.realCovers();
        io.execute(() -> { if (SleepScreen.writeBook(this, b, real)) store.setSleepFileMode("book"); });
    }

    interface FilePick { void on(File f); }

    /** Thumbnail grid of the pictures on the tablet. The selected one gets a dark frame. */
    void pictureGrid(LinearLayout body, String selected, FilePick pick) {
        body.addView(ink.heading("Choose a picture"), ink.m(-1, -2, 0, 24, 0, 0));
        List<File> pics = SleepScreen.pictures();
        if (pics.isEmpty()) {
            body.addView(note("No pictures found", "Copy JPG or PNG files into the Pictures folder over USB, then come back here.", null));
            return;
        }
        LinearLayout line = null;
        for (int i = 0; i < Math.min(60, pics.size()); i++) {
            if (i % 3 == 0) {
                line = ink.row();
                line.setGravity(Gravity.TOP);
                body.addView(item(line), ink.m(-1, -2, 0, 0, 0, 10));
            }
            File pic = pics.get(i);
            boolean on = pic.getAbsolutePath().equals(selected);
            LinearLayout cell = ink.col();
            cell.setPadding(ink.dp(4), ink.dp(4), ink.dp(4), ink.dp(4));
            cell.setBackground(on ? ink.stroke(ink.PAPER, 8, ink.TEXT) : ink.pressable());
            ImageView th = new ImageView(this);
            th.setScaleType(ImageView.ScaleType.FIT_XY);
            th.setBackgroundColor(ink.FILL);
            cell.addView(th, new LinearLayout.LayoutParams(-1, ink.dp(140)));
            TextView name = ink.oneLine(ink.text(pic.getName(), TypeScale.META, on ? ink.TEXT : ink.TEXT3));
            name.setPadding(0, ink.dp(5), 0, 0);
            cell.addView(name);
            thumbs.execute(() -> {
                Bitmap bm = SleepScreen.thumb(pic, 180, 240);
                ui.post(() -> { if (bm != null) th.setImageBitmap(bm); });
            });
            cell.setOnClickListener(v -> pick.on(pic));
            line.addView(cell, ink.weight(1));
        }
        while (line != null && line.getChildCount() < 3) line.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
    }

    // ================================================================== updates

    Updater.Release update;
    private boolean checkingUpdates = false;

    /** Looks for a newer release on GitHub. Quiet unless asked, or unless there is something new. */
    void checkUpdates(boolean manual) {
        if (checkingUpdates) return;
        if (!wifi().connected) { if (manual) toast("Connect to Wi-Fi to check for updates"); return; }
        checkingUpdates = true;
        if (manual) toast("Checking for updates…");
        io.execute(() -> {
            Updater.Release r = null;
            Exception err = null;
            try { r = Updater.check(this); } catch (Exception e) { err = e; }
            final Updater.Release found = r;
            final Exception failed = err;
            ui.post(() -> {
                checkingUpdates = false;
                if (failed == null) store.setLastUpdateCheck(System.currentTimeMillis());
                update = found;
                if (found != null) {
                    sheets.message("Ink Home " + found.version + " is available",
                            found.notes.isEmpty() ? "Install the new version?" : found.notes.trim(),
                            "Install", this::installUpdate);
                } else if (manual) {
                    toast(failed != null ? "Couldn't reach GitHub. Try again later." : "Ink Home is up to date");
                }
                if (screen == Screen.SETTINGS && sub == null) render();
            });
        });
    }

    void installUpdate() {
        Updater.Release r = update;
        if (r == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast("Allow Ink Home to install updates, then tap Install again");
            start(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        toast("Downloading " + r.version + "…");
        io.execute(() -> {
            try {
                File apk = Updater.download(this, r);
                Updater.install(this, apk);
            } catch (SecurityException e) {
                ui.post(() -> toast("The download didn't match its checksum, so it wasn't installed"));
            } catch (Exception e) {
                Log.w(TAG, "Update failed", e);
                ui.post(() -> toast("Update failed. Try again later."));
            }
        });
    }

    boolean isDefaultHome() {
        ResolveInfo ri = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    void openHomeSettings() {
        if (start(new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        if (start(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;
        start(new Intent(Settings.ACTION_SETTINGS));
    }

    // ================================================================== paging & scrolling

    interface Jumper { void to(View item); }
    Jumper jumper;

    static <T extends View> T item(T v) { return PagedFlow.item(v); }
    static <T extends View> T keep(T v) { return PagedFlow.group(v); }

    View scrollable(LinearLayout body) {
        if (store.listScroll()) {
            ScrollView sv = ink.scroller(body);
            bindScrollKeys(sv);
            jumper = v -> sv.scrollTo(0, PagedFlow.topIn(v, body));
            return sv;
        }
        String key = "list:" + screen + ":" + sub;
        PagedFlow flow = new PagedFlow(ink.folio, body, pages.getOrDefault(key, 0),
                page -> pages.put(key, page), this::pageChanged);
        jumper = flow::jumpTo;
        return bindPageKeys(flow);
    }

    private View bindPageKeys(PageLayout layout) {
        pageNext = layout::next;
        pagePrev = layout::previous;
        return layout;
    }

    /** Page keys jump a screen at a time in scrolling lists. */
    private void bindScrollKeys(ScrollView sv) {
        pageNext = () -> scrollPage(sv, 1);
        pagePrev = () -> scrollPage(sv, -1);
    }

    private void scrollPage(ScrollView sv, int direction) {
        if (!sv.canScrollVertically(direction)) return;
        pageChanged();
        sv.scrollBy(0, direction * (int) (sv.getHeight() * 0.9f));
    }

    private View cells(String key, LinearLayout header, int count, int minWidth, int rowUnits,
                       IntFunction<View> builder) {
        int height = Math.round(ink.dp(rowUnits) * Math.max(1f, ink.textOnly));
        if (store.listScroll()) {
            ScrollingCells scroll = new ScrollingCells(ink.folio, count, height, ink.dp(minWidth),
                    builder, this::pageChanged);
            pageNext = () -> scroll.turn(1);
            pagePrev = () -> scroll.turn(-1);
            if (header == null) return scroll;
            LinearLayout wrap = ink.col();
            wrap.addView(header, new LinearLayout.LayoutParams(-1, ink.dp(40)));
            wrap.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            return wrap;
        }
        int page = pages.getOrDefault(key, 0);
        if (minWidth > 0) return bindPageKeys(new PagedGrid(ink.folio, count, height,
                ink.dp(minWidth), builder, page, p -> pages.put(key, p), this::pageChanged));
        return bindPageKeys(new PagedList(ink.folio, header, count, height, builder,
                page, p -> pages.put(key, p), this::pageChanged));
    }

    // ================================================================== e-ink

    private void cleanScreen() {
        refreshController.clean(true);
    }

    private void pageChanged() {
        refreshController.changed(content, coverPage ? Change.IMAGE : Change.PAGE);
    }

    // ================================================================== helpers

    boolean hasFileAccess() { return Environment.isExternalStorageManager(); }

    void requestFileAccess() {
        if (!start(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))))
            start(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
    }

    interface Pick { void on(int which); }
    private interface TextPick { void on(String text); }

    void choose(String title, String[] labels, int checked, Pick pick) {
        sheets.choose(title, labels, checked, pick::on);
    }

    private void askText(String title, String initial, TextPick pick) {
        sheets.prompt(title, initial, pick::on);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (sheets != null) sheets.touched(event);
        return super.dispatchTouchEvent(event);
    }

    void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    boolean start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            launchedSomething = true;
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Can't start " + i, e);
            return false;
        }
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    void toast(String s) {
        sheets.notice(s, null, null);
    }
}

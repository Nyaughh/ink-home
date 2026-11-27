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
import dev.inkhome.folio.widget.Stepper;

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
import android.graphics.Typeface;
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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
    private static final String NEOREADER_PKG = "com.onyx.kreader";
    private static final String NEOREADER_CLS = "com.onyx.kreader.ui.ReaderHomeActivity";
    private static final String BOOX_HOME = "com.onyx/com.onyx.StartupActivity";
    private static final int PAD = 22;

    private enum Screen { HOME, LIBRARY, STATS, APPS, SETTINGS }

    private Store store;
    private Ink ink;
    private SheetHost sheets;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbs = Executors.newSingleThreadExecutor();

    private LinearLayout statusBar;
    private FrameLayout content;
    private LinearLayout dock;
    private FrameLayout overlay;

    private Screen screen = Screen.HOME;
    private String libTab = "reading";
    private String libCollection = null;
    private String libSearch = null;
    private String sub = null;
    private String statsTab = "overview";
    private final Calendar statsMonth = Calendar.getInstance();
    private final Map<String, Integer> pages = new HashMap<>();
    private Runnable pageNext, pagePrev;
    private boolean panelOpen = false;

    private List<Library.Book> books = new ArrayList<>();
    private boolean booksLoaded = false, loading = false;
    private long lastLoad = 0;
    private int dataSignature = 0;
    private Stats stats;
    private List<App> apps = new ArrayList<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private boolean launchedSomething = false;
    private String statusSignature = "";
    private String sleepSignature = "";
    private RefreshController refreshController;

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

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(ink.PAPER);
        LinearLayout root = ink.col();
        statusBar = ink.row();
        root.addView(statusBar, new LinearLayout.LayoutParams(-1, -2));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(ink.hair());
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
            if (Intent.ACTION_TIME_TICK.equals(i.getAction()) && now.get(Calendar.HOUR_OF_DAY) == 0 && now.get(Calendar.MINUTE) == 0)
                refreshData(0);
        }
    };

    private final BroadcastReceiver packageEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); render(); }
    };

    // ================================================================== data

    private long lastFullScan = 0;

    private void refreshData(long maxAgeMs) {
        if (loading) return;
        if (booksLoaded && System.currentTimeMillis() - lastLoad < maxAgeMs) return;
        loading = true;
        final boolean access = hasFileAccess();
        // A full storage walk at most every 10 minutes (or on request); in between, only
        // reading progress is refreshed, which keeps coming back from a book instant.
        final boolean full = !booksLoaded || System.currentTimeMillis() - lastFullScan > 10 * 60_000L;
        final List<File> known = new ArrayList<>();
        if (!full) for (Library.Book b : books) known.add(b.file);
        if (full) lastFullScan = System.currentTimeMillis();
        io.execute(() -> {
            List<Library.Book> found = access ? Library.load(this, store, full ? null : known) : new ArrayList<>();
            Stats st = Stats.refresh(this, store);
            ui.post(() -> {
                books = found;
                stats = st;
                booksLoaded = true;
                lastLoad = System.currentTimeMillis();
                rerenderIfChanged();
                extractCovers();
            });
        });
    }

    /** Pulls covers and EPUB titles in the background, current books first, then redraws once. */
    private void extractCovers() {
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

    private List<Library.Book> reading() {
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

    private boolean coverPage;

    private void render() {
        coverPage = false;
        closePanelSilently();
        content.removeAllViews();
        content.setBackground(null);
        pageNext = pagePrev = null;
        switch (screen) {
            case HOME: renderHome(); break;
            case LIBRARY: renderLibrary(); break;
            case STATS: renderStats(); break;
            case APPS: renderApps(); break;
            case SETTINGS: renderSettings(); break;
        }
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
        dock.setPadding(ink.dp(10), 0, ink.dp(10), 0);
        boolean inSettings = screen == Screen.SETTINGS;
        ImageView left = ink.icon(inSettings ? "home" : "settings", 24);
        left.setPadding(ink.dp(12), ink.dp(12), ink.dp(12), ink.dp(12));
        left.setBackground(ink.pressable());
        left.setOnClickListener(v -> go(inSettings ? Screen.HOME : Screen.SETTINGS));
        dock.addView(left, new LinearLayout.LayoutParams(ink.dp(52), ink.dp(52)));

        int today = stats == null ? 0 : stats.today();
        int goal = store.dailyGoal();
        String label = goal > 0 ? "Today " + today + " / " + goal + " min" : "Today " + Stats.hm(today);
        TextView mid = ink.center(ink.oneLine(ink.text(label, TypeScale.BODY, ink.TEXT)));
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(ink.pressable());
        mid.setOnClickListener(v -> go(Screen.STATS));
        dock.addView(mid, new LinearLayout.LayoutParams(0, ink.dp(52), 1f));

        ImageView right = ink.icon(menuOpen ? "down" : "up", 24);
        right.setPadding(ink.dp(12), ink.dp(12), ink.dp(12), ink.dp(12));
        right.setBackground(ink.pressable());
        right.setOnClickListener(v -> { if (menuOpen) closePanel(); else openMenu(); });
        dock.addView(right, new LinearLayout.LayoutParams(ink.dp(52), ink.dp(52)));
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
                {"stats", "Reading", (Runnable) () -> go(Screen.STATS), Screen.STATS},
                {"apps", "Apps", (Runnable) () -> go(Screen.APPS), Screen.APPS},
                {"search", "Search", (Runnable) () -> { go(Screen.LIBRARY); libSearch = ""; render(); }, null},
                {"clean", "Refresh", (Runnable) () -> { closePanel(); ui.postDelayed(this::cleanScreen, 120); }, null},
                {"wifi", "Wi-Fi", (Runnable) this::openWifi, null},
                {"bell", "Quick panel", (Runnable) () -> { closePanelSilently(); openPanel(); }, null},
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
            cell.setBackground(on ? ink.stroke(ink.FILL, 10, ink.LINE) : ink.pressable());
            Icons d = it[0].equals("wifi") ? new Icons("wifi", wifi().connected ? wifi().level : -1) : new Icons((String) it[0]);
            cell.addView(ink.icon(d, 26));
            TextView t = ink.center(ink.oneLine(ink.text((String) it[1], TypeScale.META, ink.TEXT)));
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
        overlay.addView(wrap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        // Keep the bar itself visible and tappable under the sheet.
        ((FrameLayout.LayoutParams) wrap.getLayoutParams()).bottomMargin = dock.getHeight() + 1;
        renderDock();
    }

    /** Standard page header: back button (optional), title, faint count, then right-side actions. */
    private LinearLayout header(String title, String count, Runnable back, View... actions) {
        LinearLayout h = ink.row();
        h.setPadding(ink.dp(PAD), ink.dp(16), ink.dp(PAD), ink.dp(14));
        if (back != null) {
            ImageView b = ink.icon("back", 24);
            b.setPadding(0, 0, ink.dp(4), 0);
            b.setBackground(ink.pressable());
            b.setOnClickListener(v -> back.run());
            h.addView(b, ink.m(ink.dp(32), ink.dp(32), 0, 0, 6, 0));
        }
        h.addView(ink.oneLine(ink.title(title)));
        if (count != null) {
            TextView c = ink.faint(count, 16);
            c.setPadding(ink.dp(10), ink.dp(4), 0, 0);
            h.addView(c);
        }
        h.addView(ink.flex());
        for (View a : actions) {
            ViewGroup.LayoutParams own = a.getLayoutParams();
            h.addView(a, ink.m(own != null ? own.width : -2, own != null ? own.height : -2, 8, 0, 0, 0));
        }
        return h;
    }

    private LinearLayout page() {
        LinearLayout col = ink.col();
        content.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return col;
    }

    // ================================================================== status bar & quick panel

    private void applySystemBar() {
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
        statusBar.setPadding(ink.dp(16), 0, ink.dp(6), 0);
        statusBar.setBackground(ink.listRow(0));
        statusBar.setMinimumHeight(ink.dp(32));
        statusBar.setOnClickListener(v -> togglePanel());

        statusBar.addView(ink.medium(time, 15));
        statusBar.addView(ink.flex());
        if (notes > 0) {
            statusBar.addView(ink.icon("bell", 16));
            TextView n = ink.medium(String.valueOf(notes), 13);
            n.setPadding(ink.dp(3), 0, ink.dp(14), 0);
            statusBar.addView(n);
        }
        statusBar.addView(ink.icon(new Icons("wifi", wifi.connected ? wifi.level : -1), 18));
        TextView pct = ink.medium(bat.percent + "%", 14);
        pct.setPadding(ink.dp(14), 0, ink.dp(5), 0);
        statusBar.addView(pct);
        statusBar.addView(ink.icon(new Icons("battery", bat.percent), 22));
        if (bat.charging) statusBar.addView(ink.medium("+", 13));
        ImageView clean = ink.icon("clean", 20);
        clean.setPadding(ink.dp(12), ink.dp(6), ink.dp(10), ink.dp(6));
        clean.setBackground(ink.pressable());
        clean.setOnClickListener(v -> cleanScreen());
        statusBar.addView(clean, new LinearLayout.LayoutParams(ink.dp(44), ink.dp(32)));
    }

    private void togglePanel() { if (panelOpen) closePanel(); else openPanel(); }

    private void closePanelSilently() {
        if (panelOpen) refreshController.changed(content, Change.OVERLAY);
        panelOpen = false;
        overlay.removeAllViews();
        overlay.setVisibility(View.GONE);
        menuOpen = false;
    }

    private void closePanel() {
        boolean wasMenu = menuOpen;
        closePanelSilently();
        if (wasMenu) renderDock();
        updateStatusBar(true);
    }

    /** Quick panel: only what this tablet's hardware has (no frontlight, no Bluetooth). */
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
        String noteState = notes < 0 ? "Allow access" : notes == 0 ? "None" : notes + " new";
        int todayMin = stats == null ? 0 : stats.today();

        addTiles(p, tile(new Icons("wifi", wifi.connected ? wifi.level : -1), "Wi-Fi", wifiState, this::openWifi),
                tile(new Icons("bell"), "Notifications", noteState, this::openNotifications));
        addTiles(p, tile(new Icons("clean"), "Clean screen", "Full refresh", () -> { closePanel(); ui.postDelayed(this::cleanScreen, 120); }),
                tile(new Icons("book"), "Read today", Stats.hm(todayMin), () -> go(Screen.STATS)));
        addTiles(p, tile(new Icons("storage"), "Storage", freeStorage() + " free", () -> start(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))),
                tile(new Icons("settings"), "Device", "Android settings", () -> start(new Intent(Settings.ACTION_SETTINGS))));

        List<Notifs.Item> list = Notifs.items();
        if (!list.isEmpty()) {
            LinearLayout nh = ink.row();
            nh.addView(ink.heading("Notifications"), ink.weight(1));
            TextView clear = ink.text("Clear all", TypeScale.META, ink.TEXT2);
            clear.setPadding(ink.dp(10), 0, 0, ink.dp(8));
            clear.setOnClickListener(v -> {
                List<Notifs.Item> removed = Notifs.stageClear();
                closePanel();
                if (!removed.isEmpty()) sheets.notice("Notifications cleared",
                        () -> Notifs.finishClear(removed, false), () -> Notifs.finishClear(removed, true));
            });
            nh.addView(clear);
            p.addView(nh, ink.m(-1, -2, 0, 8, 0, 0));
            LinearLayout card = ink.col();
            card.setBackground(ink.card(10, false));
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

        TextView done = ink.center(ink.medium("Done", 15));
        done.setPadding(0, ink.dp(12), 0, ink.dp(8));
        done.setBackground(ink.pressable());
        done.setOnClickListener(v -> closePanel());
        p.addView(done, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout wrap = ink.col();
        wrap.addView(p);
        wrap.addView(ink.hair());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.topMargin = statusBar.getHeight();
        overlay.addView(wrap, lp);
    }

    private void addTiles(LinearLayout parent, View a, View b) {
        LinearLayout r = ink.row();
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, ink.dp(68), 1f);
        pa.rightMargin = ink.dp(5);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, ink.dp(68), 1f);
        pb.leftMargin = ink.dp(5);
        r.addView(a, pa);
        r.addView(b, pb);
        parent.addView(r, ink.m(-1, -2, 0, 0, 0, 10));
    }

    private View tile(Drawable icon, String title, String state, Runnable click) {
        LinearLayout t = ink.row();
        t.setPadding(ink.dp(14), 0, ink.dp(10), 0);
        t.setBackground(ink.card(10, true));
        t.addView(ink.icon(icon, 24));
        LinearLayout txt = ink.col();
        txt.setPadding(ink.dp(12), 0, 0, 0);
        txt.addView(ink.oneLine(ink.medium(title, 15)));
        TextView s = ink.oneLine(ink.muted(state, 13));
        s.setPadding(0, ink.dp(4), 0, 0);
        txt.addView(s);
        t.addView(txt, ink.weight(1));
        t.setOnClickListener(v -> click.run());
        return t;
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

    private void renderHome() {
        LinearLayout col = page();
        col.setPadding(ink.dp(PAD), ink.dp(20), ink.dp(PAD), ink.dp(16));
        applyWallpaper();
        String layout = store.homeLayout();
        List<Library.Book> reading = reading();

        if (!hasFileAccess()) {
            col.addView(note("Show your books", "Let Ink Home read your book files for covers, titles and progress.", this::requestFileAccess));
        } else if (layout.equals("minimal")) {
            homeMinimal(col, reading);
        } else if (layout.equals("shelf")) {
            coverPage = !books.isEmpty();
            homeShelf(col, reading);
        } else {
            coverPage = !books.isEmpty();
            homeCover(col, reading);
        }
        // The book list (if any) takes the free space; otherwise a spacer pushes favorites down.
        if (col.findViewWithTag("list") == null) col.addView(ink.vflex());
        homeFavorites(col);
    }

    private Bitmap wallpaperCache;
    private String wallpaperKey = "";

    /** Home background: the chosen picture in greyscale, faded toward white so text stays crisp. */
    private void applyWallpaper() {
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
    private void homeCover(LinearLayout col, List<Library.Book> reading) {
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

    private void homeShelf(LinearLayout col, List<Library.Book> reading) {
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

    private void homeMinimal(LinearLayout col, List<Library.Book> reading) {
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

    private void homeFavorites(LinearLayout col) {
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
    private View note(String title, String body, Runnable click) {
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

    private void renderLibrary() {
        LinearLayout col = page();
        if (libSearch != null) { renderSearch(col); return; }

        ImageView search = ink.iconButton("search", () -> { libSearch = ""; pages.put("lib", 0); render(); });
        ImageView view = ink.iconButton(store.libraryGrid() ? "list" : "grid", () -> { store.setLibraryGrid(!store.libraryGrid()); pages.put("lib", 0); render(); });
        Runnable back = libCollection != null ? () -> { libCollection = null; render(); } : null;
        col.addView(header(libCollection != null ? libCollection : "Library", booksLoaded ? String.valueOf(books.size()) : null, back, search, view));

        LinearLayout body = ink.col();
        body.setPadding(ink.dp(PAD), 0, ink.dp(PAD), 0);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (libCollection == null) {
            String[] keys = {"reading", "all", "finished", "collections"};
            body.addView(ink.segmented(new String[]{"Reading", "All", "Finished", "Collections"}, Arrays.asList(keys).indexOf(libTab),
                    i -> { libTab = keys[i]; pages.put("lib", 0); render(); }));
        }
        if (!hasFileAccess()) {
            body.addView(note("Allow file access", "Ink Home needs it to list your books.", this::requestFileAccess), ink.m(-1, -2, 0, 16, 0, 0));
            return;
        }
        if (libCollection == null && libTab.equals("collections")) {
            body.addView(collectionsList(), ink.m(-1, 0, 0, 12, 0, 0));
            ((LinearLayout.LayoutParams) body.getChildAt(body.getChildCount() - 1).getLayoutParams()).weight = 1;
            return;
        }

        LinearLayout pills = ink.row();
        pills.setPadding(0, ink.dp(12), 0, ink.dp(10));
        Map<String, String> sorts = new LinkedHashMap<>();
        sorts.put("recent", "Recent"); sorts.put("title", "Title"); sorts.put("author", "Author"); sorts.put("added", "Added"); sorts.put("progress", "Progress");
        TextView sort = ink.pill("Sort: " + sorts.get(store.librarySort()), false);
        sort.setOnClickListener(v -> chooseSort());
        pills.addView(sort);
        Map<String, String> types = new LinkedHashMap<>();
        types.put("all", "All types"); types.put("book", "Books"); types.put("comic", "Comics"); types.put("pdf", "PDFs");
        TextView type = ink.pill(types.get(store.libraryType()), !store.libraryType().equals("all"));
        type.setOnClickListener(v -> chooseType());
        pills.addView(type, ink.m(-2, -2, 8, 0, 0, 0));
        body.addView(pills);
        body.addView(ink.hair());

        List<Library.Book> list = filterBooks(false);
        if (list.isEmpty()) {
            String msg;
            if (!booksLoaded) msg = "Looking for books…";
            else if (libCollection != null) msg = "This collection is empty. Long-press a book and choose Add to collection.";
            else if (libTab.equals("reading")) msg = "Books you open show up here, with your progress and time read.";
            else if (libTab.equals("finished")) msg = "Finished books show up here. Long-press a book to mark it finished.";
            else msg = "No books found. Copy EPUB, PDF, MOBI, FB2, DJVU, CBZ or CBR files to the tablet.";
            TextView t = ink.muted(msg, 15);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, ink.dp(18), 0, 0);
            body.addView(t);
            return;
        }
        coverPage = store.libraryGrid();
        body.addView(bookPages(list, store.libraryGrid()), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void renderSearch(LinearLayout col) {
        LinearLayout head = ink.row();
        head.setPadding(ink.dp(PAD), ink.dp(16), ink.dp(PAD), ink.dp(12));
        EditText q = new EditText(this);
        q.setText(libSearch);
        q.setHint("Search titles and authors");
        q.setTextColor(ink.TEXT);
        q.setHintTextColor(ink.TEXT3);
        q.setTextSize(17 * ink.scale);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setBackground(ink.stroke(ink.PAPER, 9, ink.LINE));
        q.setPadding(ink.dp(14), ink.dp(10), ink.dp(14), ink.dp(10));
        head.addView(q, ink.weight(1));
        TextView cancel = ink.medium("Cancel", 15);
        cancel.setPadding(ink.dp(16), ink.dp(10), 0, ink.dp(10));
        cancel.setOnClickListener(v -> { libSearch = null; hideKeyboard(); render(); });
        head.addView(cancel);
        col.addView(head);
        LinearLayout results = ink.col();
        results.setPadding(ink.dp(PAD), 0, ink.dp(PAD), 0);
        col.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));
        Runnable refresh = () -> {
            results.removeAllViews();
            results.addView(ink.hair());
            List<Library.Book> list = filterBooks(true);
            if (list.isEmpty() && !libSearch.trim().isEmpty()) {
                TextView t = ink.muted("No matches.", 15);
                t.setPadding(0, ink.dp(16), 0, 0);
                results.addView(t);
            } else {
                results.addView(bookPages(list, false), new LinearLayout.LayoutParams(-1, 0, 1f));
            }
        };
        Runnable filterAfterTyping = () -> {
            if (q.isAttachedToWindow() && libSearch != null) refresh.run();
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                refreshController.fast(q);
                libSearch = s.toString();
                pages.put("lib", 0);
                ui.removeCallbacks(filterAfterTyping);
                ui.postDelayed(filterAfterTyping, 250);
            }
        });
        refresh.run();
        q.requestFocus();
        q.setSelection(q.getText().length());
        ui.postDelayed(() -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(q, 0);
        }, 150);
    }

    private List<Library.Book> filterBooks(boolean searching) {
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

    private View bookPages(List<Library.Book> list, boolean grid) {
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

    private View collectionsList() {
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

    private void chooseSort() {
        String[] keys = {"recent", "title", "author", "added", "progress"};
        choose("Sort by", new String[]{"Recently read", "Title", "Author", "Recently added", "Progress"},
                Arrays.asList(keys).indexOf(store.librarySort()), w -> { store.setLibrarySort(keys[w]); pages.put("lib", 0); render(); });
    }

    private void chooseType() {
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

    private void openBook(Library.Book b) {
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

    private void chooseReader(boolean booksKind) {
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

    private String readerName(String pkg) {
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

    private void renderStats() {
        LinearLayout col = page();
        TextView goal = ink.pill(store.dailyGoal() > 0 ? "Goal " + store.dailyGoal() + " min" : "Set a goal", false);
        goal.setOnClickListener(v -> chooseGoal());
        col.addView(header("Reading", null, null, goal));
        LinearLayout tabs = ink.segmented(new String[]{"Overview", "Calendar"}, statsTab.equals("calendar") ? 1 : 0,
                i -> { statsTab = i == 1 ? "calendar" : "overview"; render(); });
        col.addView(tabs, ink.m(-1, -2, PAD, 0, PAD, 0));

        Stats s = stats != null ? stats : Stats.refresh(this, store);
        LinearLayout body = ink.col();
        body.setPadding(ink.dp(PAD), ink.dp(18), ink.dp(PAD), ink.dp(16));
        if (!Stats.hasAccess(this)) {
            body.addView(note("Turn on reading time", "Ink Home counts the minutes your reading apps are on screen. Tap, then switch Ink Home on.",
                    () -> start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))), ink.m(-1, -2, 0, 0, 0, 16));
        }
        if (statsTab.equals("calendar")) {
            renderCalendar(body, s);
            col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            renderOverview(body, s);
                        col.addView(scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
        }
    }

    private void renderOverview(LinearLayout col, Stats s) {
        int today = s.today();
        col.addView(ink.faint("Today", 14));
        TextView big = ink.text(Stats.hm(today), TypeScale.FIGURE, ink.TEXT);
        big.setPadding(0, ink.dp(8), 0, 0);
        col.addView(big);
        if (s.hasGoal()) {
            col.addView(ink.progress(today / (float) s.goal, 5), ink.m(-1, -2, 0, 14, 0, 0));
            TextView g = ink.muted(today >= s.goal ? "Goal met" : (s.goal - today) + " min to your " + s.goal + " min goal", 14);
            g.setPadding(0, ink.dp(8), 0, 0);
            col.addView(g);
        }

        int best = s.bestStreak();
        col.addView(metricRow(metric("Streak", s.currentStreak() + (s.currentStreak() == 1 ? " day" : " days"), best > 0 ? "Best " + best : null),
                metric("This week", Stats.hm(s.weekTotal()), null)), ink.m(-1, -2, 0, 22, 0, 0));
        col.addView(metricRow(metric("This month", Stats.hm(s.monthTotal(Calendar.getInstance())), null),
                metric("All time", Stats.hm(s.total()), null)), ink.m(-1, -2, 0, 10, 0, 0));

        LinearLayout weekSec = keep(ink.col());
        col.addView(weekSec, ink.m(-1, -2, 0, 24, 0, 0));
        weekSec.addView(ink.heading("This week"));
        LinearLayout chartCard = ink.col();
        chartCard.setPadding(ink.dp(14), ink.dp(14), ink.dp(14), ink.dp(10));
        chartCard.setBackground(ink.card(12, false));
        chartCard.addView(new WeekChart(this, s), new LinearLayout.LayoutParams(-1, ink.dp(140)));
        weekSec.addView(chartCard);

        List<Library.Book> byTime = new ArrayList<>();
        for (Library.Book b : books) if (s.forBook(b.path) > 0) byTime.add(b);
        byTime.sort((a, b) -> Integer.compare(s.forBook(b.path), s.forBook(a.path)));
        LinearLayout mostSec = keep(ink.col());
        col.addView(mostSec, ink.m(-1, -2, 0, 24, 0, 0));
        mostSec.addView(ink.heading("Most read"));
        LinearLayout list = ink.col();
        list.setBackground(ink.card(12, false));
        if (byTime.isEmpty()) {
            TextView t = ink.muted("Open a book from Ink Home and its reading time shows up here.", 14);
            t.setPadding(ink.dp(16), ink.dp(14), ink.dp(16), ink.dp(14));
            list.addView(t);
        }
        for (int i = 0; i < Math.min(5, byTime.size()); i++) {
            Library.Book b = byTime.get(i);
            LinearLayout r = ink.row();
            r.setPadding(ink.dp(16), 0, ink.dp(16), 0);
            r.setBackground(i < Math.min(5, byTime.size()) - 1 ? ink.listRow(16) : ink.pressable());
            r.addView(ink.oneLine(ink.body(b.title)), ink.weight(1));
            r.addView(ink.muted(Stats.hm(s.forBook(b.path)), 14), ink.m(-2, -2, 12, 0, 0, 0));
            r.setOnClickListener(v -> openBook(b));
            list.addView(item(r), new LinearLayout.LayoutParams(-1, ink.dp(50)));
        }
        mostSec.addView(list);

        int year = Calendar.getInstance().get(Calendar.YEAR), finished = 0;
        for (Library.Book b : books) {
            if (!b.finished) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(b.lastRead > 0 ? b.lastRead : b.modified);
            if (c.get(Calendar.YEAR) == year) finished++;
        }
        col.addView(metricRow(metric("Finished in " + year, String.valueOf(finished), null),
                metric("In progress", String.valueOf(reading().size()), null)), ink.m(-1, -2, 0, 24, 0, 0));
    }

    /** Two equal-height cards side by side. */
    private View metricRow(View a, View b) {
        LinearLayout r = ink.row();
        r.setGravity(Gravity.TOP);
        r.addView(a, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, -1, 1f);
        pb.leftMargin = ink.dp(10);
        r.addView(b, pb);
        return r;
    }

    private View metric(String label, String value, String sub) {
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

    private void renderCalendar(LinearLayout col, Stats s) {
        LinearLayout head = ink.row();
        head.addView(ink.medium(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(statsMonth.getTime()), 18), ink.weight(1));
        ImageView prev = ink.iconButton("back", () -> { statsMonth.add(Calendar.MONTH, -1); render(); });
        ImageView next = ink.iconButton("chevron", () -> { statsMonth.add(Calendar.MONTH, 1); render(); });
        head.addView(prev);
        head.addView(next, ink.m(-2, -2, 8, 0, 0, 0));
        col.addView(head);
        pagePrev = () -> { statsMonth.add(Calendar.MONTH, -1); render(); };
        pageNext = () -> { statsMonth.add(Calendar.MONTH, 1); render(); };

        LinearLayout names = ink.row();
        Calendar d = Calendar.getInstance();
        int first = s.mondayFirst ? Calendar.MONDAY : Calendar.SUNDAY;
        SimpleDateFormat dn = new SimpleDateFormat("EEEEE", Locale.getDefault());
        d.set(Calendar.DAY_OF_WEEK, first);
        for (int i = 0; i < 7; i++) {
            names.addView(ink.center(ink.faint(dn.format(d.getTime()), 13)), ink.weight(1));
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        col.addView(names, ink.m(-1, -2, 0, 18, 0, 8));

        Calendar day = (Calendar) statsMonth.clone();
        day.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (day.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
        int days = day.getActualMaximum(Calendar.DAY_OF_MONTH);
        int max = 1;
        for (int i = 1; i <= days; i++) {
            Calendar cd = (Calendar) day.clone();
            cd.set(Calendar.DAY_OF_MONTH, i);
            max = Math.max(max, s.on(cd));
        }
        int full = s.hasGoal() ? s.goal : Math.max(30, max);
        Calendar today = Calendar.getInstance();
        LinearLayout line = null;
        for (int i = 0; i < offset + days; i++) {
            if (i % 7 == 0) {
                line = ink.row();
                col.addView(line, ink.m(-1, ink.dp(48), 0, 0, 0, 5));
            }
            if (i < offset) { line.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f)); continue; }
            int dom = i - offset + 1;
            Calendar cd = (Calendar) day.clone();
            cd.set(Calendar.DAY_OF_MONTH, dom);
            int m = s.on(cd);
            boolean isToday = cd.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cd.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            // Heat map: light grey for a little reading, darker as you approach the goal (or 30 min), black past it.
            float level = m == 0 ? 0 : Math.min(1f, m / (float) full);
            int fill = m == 0 ? Color.WHITE : level >= 1f ? ink.TEXT : level >= 0.5f ? 0xFF8C8C8C : 0xFFDADADA;
            boolean dark = fill == ink.TEXT || fill == 0xFF8C8C8C;
            LinearLayout c = ink.col();
            c.setGravity(Gravity.CENTER);
            c.setBackground(m > 0 ? ink.solid(fill, 8) : isToday ? ink.stroke(ink.PAPER, 8, ink.TEXT) : null);
            TextView num = ink.center(ink.text(String.valueOf(dom), TypeScale.META, dark ? Color.WHITE : ink.TEXT));
            c.addView(num, new LinearLayout.LayoutParams(-1, -2));
            if (m > 0) {
                TextView mins = ink.center(ink.text(String.valueOf(m), TypeScale.CHART, dark ? Color.WHITE : ink.TEXT2));
                mins.setPadding(0, ink.dp(3), 0, 0);
                c.addView(mins, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.setMargins(ink.dp(3), 0, ink.dp(3), 0);
            line.addView(c, p);
        }
        while (line != null && line.getChildCount() < 7) line.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f));

        int readDays = s.readingDaysInMonth(statsMonth);
        TextView sum = ink.medium(Stats.hm(s.monthTotal(statsMonth)) + " read on " + readDays + (readDays == 1 ? " day" : " days"), 15);
        sum.setPadding(0, ink.dp(18), 0, ink.dp(6));
        col.addView(sum);
        col.addView(ink.faint(s.hasGoal() ? "Darker days are closer to your goal. Black means you met it." : "Darker days mean more reading. The small number is minutes.", 13));
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

    private void chooseGoal() {
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

    private App findApp(String key) {
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

    private String displayLabel(App a) { return a.label; }

    private ImageView appIcon(App a, int sizeDp) {
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

    private void renderApps() {
        if (sub != null) { renderAppPicker(); return; }
        LinearLayout col = page();
        Set<String> hidden = store.hidden();
        List<App> visible = new ArrayList<>();
        for (App a : apps) if (!hidden.contains(a.key)) visible.add(a);
        View[] actions = hidden.isEmpty() ? new View[0] : new View[]{ink.pill("Hidden " + hidden.size(), false)};
        if (actions.length > 0) actions[0].setOnClickListener(v -> { sub = "hidden"; render(); });
        col.addView(header("Apps", String.valueOf(visible.size()), null, actions));

        LinearLayout list = ink.col();
        list.setPadding(ink.dp(PAD), 0, ink.dp(8), ink.dp(16));
        Map<Character, View> firstRow = new LinkedHashMap<>();
        Set<String> readingApps = store.readingApps();
        list.addView(ink.hair());
        for (App a : visible) {
            LinearLayout r = ink.row();
            r.setBackground(ink.listRow(store.appIcons() ? 44 : 0));
            if (store.appIcons()) r.addView(appIcon(a, 28), ink.m(ink.dp(28), ink.dp(28), 0, 0, 16, 0));
            r.addView(ink.oneLine(ink.body(displayLabel(a))), ink.weight(1));
            if (readingApps.contains(a.pkg)) r.addView(ink.faint("Reading", 12), ink.m(-2, -2, 0, 0, 10, 0));
            if (store.isFavorite(a.key)) r.addView(ink.icon("star", 14), ink.m(ink.dp(14), ink.dp(14), 0, 0, 8, 0));
            r.setOnClickListener(v -> launch(a));
            r.setOnLongClickListener(v -> { appMenu(a); return true; });
            list.addView(item(r), new LinearLayout.LayoutParams(-1, ink.dp(54)));
            String l = displayLabel(a);
            char ch = l.isEmpty() ? '#' : Character.toUpperCase(l.charAt(0));
            if (!Character.isLetter(ch)) ch = '#';
            if (!firstRow.containsKey(ch)) firstRow.put(ch, r);
        }
        TextView hint = ink.faint("Long-press an app to add it to favorites, hide it, or count it as reading.", 13);
        hint.setPadding(0, ink.dp(16), ink.dp(14), 0);
        list.addView(hint);

        View lister = scrollable(list);
        Jumper jump = jumper;
        LinearLayout body = ink.row();
        body.setGravity(Gravity.TOP);
        body.addView(lister, new LinearLayout.LayoutParams(0, -1, 1f));
        if (firstRow.size() > 3) {
            LinearLayout az = ink.col();
            az.setPadding(0, 0, ink.dp(6), ink.dp(8));
            for (Map.Entry<Character, View> e : firstRow.entrySet()) {
                TextView t = ink.center(ink.text(String.valueOf(e.getKey()), TypeScale.LABEL, ink.TEXT3));
                t.setGravity(Gravity.CENTER);
                t.setBackground(ink.pressable());
                t.setOnClickListener(v -> jump.to(e.getValue()));
                az.addView(t, new LinearLayout.LayoutParams(ink.dp(28), 0, 1f));
            }
            body.addView(az, new LinearLayout.LayoutParams(ink.dp(34), -1));
        }
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    /** Hidden apps (tap to show again) or the reading-apps picker. */
    private void renderAppPicker() {
        LinearLayout col = page();
        boolean hiddenMode = "hidden".equals(sub);
        col.addView(header(hiddenMode ? "Hidden apps" : "Reading apps", null, () -> { sub = null; render(); }));
        LinearLayout list = ink.col();
        list.setPadding(ink.dp(PAD), 0, ink.dp(PAD), ink.dp(16));
        TextView hint = ink.muted(hiddenMode ? "Tap an app to show it in Apps again." : "Time in these apps counts as reading. Your book reader always counts.", 14);
        hint.setPadding(0, 0, 0, ink.dp(12));
        list.addView(hint);
        list.addView(ink.hair());
        Set<String> hidden = store.hidden();
        Set<String> reading = store.readingApps();
        int shown = 0;
        for (App a : apps) {
            if (hiddenMode && !hidden.contains(a.key)) continue;
            shown++;
            LinearLayout r = ink.row();
            r.setBackground(ink.listRow(store.appIcons() ? 44 : 0));
            if (store.appIcons()) r.addView(appIcon(a, 28), ink.m(ink.dp(28), ink.dp(28), 0, 0, 16, 0));
            r.addView(ink.oneLine(ink.body(displayLabel(a))), ink.weight(1));
            if (!hiddenMode) {
                boolean on = reading.contains(a.pkg);
                r.addView(ink.pill(on ? "Counts" : "Off", on));
                r.setOnClickListener(v -> {
                    if (a.pkg.equals(NEOREADER_PKG) || a.pkg.equals(store.bookReader()) || a.pkg.equals(store.pdfReader())) {
                        toast("Your book reader always counts");
                        return;
                    }
                    store.setReadingApp(a.pkg, !on);
                    render();
                });
            } else {
                r.setOnClickListener(v -> { store.setHidden(a.key, false); render(); });
            }
            list.addView(item(r), new LinearLayout.LayoutParams(-1, ink.dp(58)));
        }
        if (shown == 0) {
            TextView t = ink.muted("No hidden apps.", 15);
            t.setPadding(0, ink.dp(16), 0, 0);
            list.addView(t);
        }
                col.addView(scrollable(list), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void appMenu(App a) {
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

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!start(i)) toast("Couldn't open " + a.label);
    }

    // ================================================================== settings (scrolling, grouped)

    private static int settingIndex(int[] values, int value) {
        int closest = 0;
        for (int i = 1; i < values.length; i++)
            if (Math.abs(values[i] - value) < Math.abs(values[closest] - value)) closest = i;
        return closest;
    }

    private void refreshReadingSettings() {
        io.execute(() -> {
            Stats updated = Stats.refresh(this, store);
            ui.post(() -> { stats = updated; render(); });
        });
    }

    private final class Rows {
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

    private void renderSettings() {
        if ("hidden".equals(sub) || "reading".equals(sub)) { renderAppPicker(); return; }
        if ("sleep".equals(sub)) { renderSleep(); return; }
        if ("wallpaper".equals(sub)) { renderWallpaper(); return; }
        LinearLayout col = page();
        col.addView(header("Settings", null, null));
        LinearLayout body = ink.col();
        body.setPadding(ink.dp(PAD), 0, ink.dp(PAD), ink.dp(20));

        group(body, "Appearance", g -> {
            String[] layouts = {"hero", "shelf", "minimal"};
            g.segments("Home layout", new String[]{"Cover", "Shelf", "Minimal"},
                    Arrays.asList(layouts).indexOf(store.homeLayout()), i -> { store.setHomeLayout(layouts[i]); render(); });
            g.scale("Text size", new int[]{90, 100, 115, 130}, store.textScale(), value -> {
                store.setTextScale(value / 100f);
                ink = new Ink(this, store.textScale(), store.uiScale());
                render();
            });
            g.scale("Interface size", new int[]{100, 115, 130, 145}, store.uiScale(), value -> {
                store.setUiScale(value / 100f);
                ink = new Ink(this, store.textScale(), store.uiScale());
                iconCache.clear();
                render();
            });
            g.segments("Status bar", new String[]{"Ink Home", "System"}, store.statusBar() ? 0 : 1, i -> {
                store.setStatusBar(i == 0);
                applySystemBar();
                render();
            });
            String[] clocks = {"system", "12", "24"};
            g.segments("Clock", new String[]{"Auto", "12-hour", "24-hour"}, Arrays.asList(clocks).indexOf(store.clock()),
                    i -> { store.setClock(clocks[i]); render(); });
        });

        group(body, "Display", g -> {
            g.segments("Long lists", new String[]{"Pages", "Scroll"}, store.listScroll() ? 1 : 0,
                    i -> { store.setListScroll(i == 1); render(); });
            int[] intervals = {0, 1, 4, 8, 16};
            String refreshLabel = "Full refresh in the reader";
            g.control(refreshLabel, new Stepper(ink.folio, refreshLabel,
                    new String[]{"Off", "1 page", "4 pages", "8 pages", "16 pages"},
                    settingIndex(intervals, store.readerRefreshEvery()),
                    i -> store.setReaderRefreshEvery(intervals[i])), false);
            g.toggle("App icons", store.appIcons(), value -> { store.setAppIcons(value); render(); });
            g.toggle("Show original book covers", store.realCovers(), value -> {
                store.setRealCovers(value);
                Covers.flush();
                if (value) extractCovers();
                updateSleepPicture(false);
                render();
            });
            String sleep = store.sleepMode().equals("book") ? "Current book" : store.sleepMode().equals("custom") ? "Your picture" : "Off";
            g.add("Wallpaper", store.wallpaper().isEmpty() ? "None" : new File(store.wallpaper()).getName(), () -> { sub = "wallpaper"; render(); });
            g.add("Sleep screen", sleep, () -> { sub = "sleep"; render(); });
        });

        group(body, "Reading", g -> {
            g.add("Open books with", readerName(store.bookReader().isEmpty() ? Store.BUILT_IN : store.bookReader()), () -> chooseReader(true));
            g.add("Open PDFs and comics with", readerName(store.pdfReader()), () -> chooseReader(false));
            int[] goals = {0, 10, 15, 20, 30, 45, 60, 90, 120};
            String[] labels = new String[goals.length];
            for (int i = 0; i < goals.length; i++) labels[i] = goals[i] == 0 ? "Off" : goals[i] + " min";
            g.control("Daily goal", new Stepper(ink.folio, "Daily goal", labels, settingIndex(goals, store.dailyGoal()), i -> {
                store.setDailyGoal(goals[i]);
                refreshReadingSettings();
            }), false);
            g.segments("Week starts on", new String[]{"Sunday", "Monday"}, store.weekStartsMonday() ? 1 : 0, i -> {
                store.setWeekStartsMonday(i == 1);
                refreshReadingSettings();
            });
            int n = store.readingApps().size();
            g.add("Apps that count as reading", String.valueOf(n), () -> { sub = "reading"; render(); });
        });

        group(body, "Controls", g -> {
            g.toggle("Clean screen after reading", store.cleanOnHome(), value -> { store.setCleanOnHome(value); render(); });
            g.segments("Page buttons", new String[]{"Normal", "Reversed"}, store.pageKeysReversed() ? 1 : 0,
                    i -> { store.setPageKeysReversed(i == 1); render(); });
        });

        group(body, "Library", g -> {
            g.add("Look for new books", booksLoaded ? books.size() + " books" : "…", () -> { lastLoad = 0; lastFullScan = 0; refreshData(0); toast("Looking for books…"); });
            g.add("File access", hasFileAccess() ? "Allowed" : "Off", this::requestFileAccess);
            g.add("Reading time", Stats.hasAccess(this) ? "Allowed" : "Off", () -> start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
            g.add("Notification count", Notifs.count() >= 0 ? "Allowed" : "Off", () -> start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        });

        group(body, "Updates", g -> {
            g.add(update != null ? "Install version " + update.version : "Check for updates", update != null ? "Ready" : "Version " + Updater.installedVersion(this),
                    () -> { if (update != null) installUpdate(); else checkUpdates(true); });
            g.toggle("Check automatically", store.autoUpdate(), value -> { store.setAutoUpdate(value); render(); });
        });

        group(body, "System", g -> {
            g.add("Hidden apps", String.valueOf(store.hidden().size()), () -> { sub = "hidden"; render(); });
            g.add("Default home app", isDefaultHome() ? "Ink Home" : "Choose", this::openHomeSettings);
            g.add("Onyx Launcher", "", () -> { App a = findApp(BOOX_HOME); if (a != null) launch(a); });
            g.add("Android settings", "", () -> start(new Intent(Settings.ACTION_SETTINGS)));
        });

        TextView about = ink.center(ink.faint("Ink Home " + Updater.installedVersion(this), 13));
        about.setPadding(0, ink.dp(8), 0, 0);
        if (BuildConfig.DEBUG) {
            about.setOnClickListener(new View.OnClickListener() {
                private int taps;
                private long previous;
                @Override public void onClick(View view) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (now - previous > 2000) taps = 0;
                    previous = now;
                    if (++taps < 5) return;
                    taps = 0;
                    dev.inkhome.lab.InkLab.open(MainActivity.this, store.uiScale(), store.textScale(),
                            books.isEmpty() ? null : Covers.get(MainActivity.this, books.get(0), store.realCovers()));
                }
            });
        }
        body.addView(about, new LinearLayout.LayoutParams(-1, -2));

                col.addView(scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private interface GroupBuilder { void build(Rows rows); }

    /** A Linear-style settings group: grey heading, then rows inside one rounded card. */
    private void group(LinearLayout body, String title, GroupBuilder build) {
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

    private void updateSleepPicture(boolean force) {
        if (!store.sleepMode().equals("book") || !hasFileAccess()) return;
        List<Library.Book> r = reading();
        Library.Book b = r.isEmpty() ? null : r.get(0);
        String sig = b == null ? "none" : b.key() + b.title + b.author + Covers.pending(this, b) + store.realCovers();
        if (!force && sig.equals(sleepSignature)) return;
        sleepSignature = sig;
        boolean real = store.realCovers();
        io.execute(() -> { if (SleepScreen.writeBook(this, b, real)) store.setSleepFileMode("book"); });
    }

    private void renderSleep() {
        LinearLayout col = page();
        col.addView(header("Sleep screen", null, () -> { sub = null; render(); }));
        LinearLayout body = ink.col();
        body.setPadding(ink.dp(PAD), 0, ink.dp(PAD), ink.dp(20));
        String mode = store.sleepMode();
        String[] keys = {"off", "book", "custom"};
        body.addView(item(new Segmented(ink.folio, "Sleep screen", new String[]{"Off", "Book", "Picture"}, Arrays.asList(keys).indexOf(mode), i -> {
            store.setSleepMode(keys[i]);
            if (keys[i].equals("book")) { sleepSignature = ""; updateSleepPicture(true); ui.postDelayed(this::render, 900); }
            else render();
        })));

        if (!mode.equals("off")) {
            LinearLayout prev = ink.row();
            prev.setGravity(Gravity.TOP);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setPadding(1, 1, 1, 1);
            iv.setBackgroundColor(0xFF9A9A9A);
            File f = SleepScreen.file();
            boolean ready = f.exists() && mode.equals(store.sleepFileMode());
            if (ready) thumbs.execute(() -> {
                Bitmap bm = SleepScreen.thumb(f, 228, 308);
                ui.post(() -> { if (bm != null) iv.setImageBitmap(bm); });
            });
            prev.addView(iv, new LinearLayout.LayoutParams(ink.dp(114), ink.dp(154)));
            LinearLayout how = ink.col();
            how.setPadding(ink.dp(18), 0, 0, 0);
            how.addView(ink.medium(ready ? "Picture ready" : mode.equals("custom") ? "Pick a picture below" : "Making picture…", 16));
            // Boox copies the picture when it's set, so Ink Home can't apply or refresh it itself.
            TextView t = ink.muted("To use it: in Boox, open Storage › Pictures › Ink Home, long-press “Ink Home sleep” and choose Set as screensaver. "
                    + "Boox keeps its own copy, so set it again whenever you make a new picture.", 14);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, ink.dp(8), 0, 0);
            how.addView(t);
            if (ready) {
                TextView open = ink.pill("Open Boox", false);
                open.setOnClickListener(v -> { App a = findApp(BOOX_HOME); if (a != null) launch(a); });
                LinearLayout or = ink.row();
                or.addView(open);
                how.addView(or, ink.m(-1, -2, 0, 12, 0, 0));
            }
            prev.addView(how, ink.weight(1));
            body.addView(prev, ink.m(-1, -2, 0, 20, 0, 0));
        } else {
            TextView t = ink.muted("Ink Home can make your sleep screen the book you're reading, or any picture on the tablet.", 14);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, ink.dp(18), 0, 0);
            body.addView(t);
        }

        if (mode.equals("custom")) {
            pictureGrid(body, null, pic -> choose("Use this picture", new String[]{"Fill the screen (crop edges)", "Show the whole picture"}, -1, w -> {
                toast("Making sleep picture…");
                io.execute(() -> {
                    boolean ok = SleepScreen.writeImage(this, pic, w == 0);
                    if (ok) store.setSleepFileMode("custom");
                    ui.post(() -> { toast(ok ? "Sleep picture updated" : "Couldn't read that picture"); render(); });
                });
            }));
        }
                col.addView(scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private interface FilePick { void on(File f); }

    /** Thumbnail grid of the pictures on the tablet. The selected one gets a dark frame. */
    private void pictureGrid(LinearLayout body, String selected, FilePick pick) {
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

    /** Home wallpaper: none or a picture, plus how faded it is behind the home screen. */
    private void renderWallpaper() {
        LinearLayout col = page();
        col.addView(header("Wallpaper", null, () -> { sub = null; render(); }));
        LinearLayout body = ink.col();
        body.setPadding(ink.dp(PAD), 0, ink.dp(PAD), ink.dp(20));
        boolean has = !store.wallpaper().isEmpty();
        TextView intro = ink.muted("A picture behind your home screen, in greyscale and faded so text stays sharp.", 14);
        intro.setLineSpacing(0, 1.2f);
        body.addView(intro);
        if (has) {
            float[] fades = {0.8f, 0.65f, 0.45f};
            int cur = 1;
            for (int i = 0; i < fades.length; i++) if (Math.abs(fades[i] - store.wallpaperFade()) < 0.01f) cur = i;
            String[] levels = {"Subtle", "Medium", "Strong"};
            LinearLayout strength = keep(ink.col());
            strength.addView(ink.heading("Strength · " + levels[cur]));
            strength.addView(item(new LevelBar(ink.folio, "Strength", levels, cur,
                    i -> { store.setWallpaperFade(fades[i]); render(); })));
            body.addView(strength, ink.m(-1, -2, 0, 20, 0, 0));
            TextView remove = ink.pill("Remove wallpaper", false);
            remove.setOnClickListener(v -> { store.setWallpaper(""); render(); });
            LinearLayout r = ink.row();
            r.addView(remove);
            body.addView(r, ink.m(-1, -2, 0, 14, 0, 0));
        }
        pictureGrid(body, store.wallpaper(), pic -> { store.setWallpaper(pic.getAbsolutePath()); toast("Wallpaper set"); render(); });
        col.addView(scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    // ================================================================== updates

    private Updater.Release update;
    private boolean checkingUpdates = false;

    /** Looks for a newer release on GitHub. Quiet unless asked, or unless there is something new. */
    private void checkUpdates(boolean manual) {
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

    private void installUpdate() {
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

    private boolean isDefaultHome() {
        ResolveInfo ri = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void openHomeSettings() {
        if (start(new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        if (start(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) return;
        start(new Intent(Settings.ACTION_SETTINGS));
    }

    // ================================================================== paging & scrolling

    private interface Jumper { void to(View item); }
    private Jumper jumper;

    private static <T extends View> T item(T v) { return PagedFlow.item(v); }
    private static <T extends View> T keep(T v) { return PagedFlow.group(v); }

    private View scrollable(LinearLayout body) {
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

    private boolean hasFileAccess() { return Environment.isExternalStorageManager(); }

    private void requestFileAccess() {
        if (!start(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))))
            start(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
    }

    private interface Pick { void on(int which); }
    private interface TextPick { void on(String text); }

    private void choose(String title, String[] labels, int checked, Pick pick) {
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

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    private boolean start(Intent i) {
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

    private void toast(String s) {
        sheets.notice(s, null, null);
    }
}

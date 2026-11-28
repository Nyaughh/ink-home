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
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ActivityManager;
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
import java.util.LinkedHashSet;
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

    enum Screen {
        HOME(new HomeScreen()), LIBRARY(new LibraryScreen()), STATS(new StatsScreen()),
        APPS(new AppsScreen()), SETTINGS(new SettingsScreen());

        final dev.inkhome.Screen view;

        Screen(dev.inkhome.Screen view) {
            this.view = view;
        }
    }

    Store store;
    Ink ink;
    HomeWidgetHost homeWidgets;
    HomeEditor homeEditor;
    SheetHost sheets;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final ExecutorService thumbs = Executors.newSingleThreadExecutor();

    private LinearLayout statusBar;
    private FrameLayout content;
    private LinearLayout dock;
    private FrameLayout overlay;

    private Screen screen = Screen.HOME;
    String libTab = "reading";
    String libraryDetail;
    String libCollection = null;
    String libSearch = null;
    String sub = null;
    String statsTab = "overview";
    final Calendar statsMonth = Calendar.getInstance();
    final Map<String, Integer> pages = new HashMap<>();
    Runnable pageNext, pagePrev;
    private boolean panelOpen = false;
    static final String READER_PANEL_TASK = "dev.inkhome.readerPanelTask";
    private int readerPanelTask = -1;

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

    private FirstRunAccess firstRunAccess;

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // NeoReader tracks progress by file path, so it gets file:// links; other readers get content://.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        store = new Store(this);
        ink = new Ink(this, store.textScale(), store.uiScale());

        homeWidgets = new HomeWidgetHost(this);
        homeEditor = new HomeEditor(this);
        ActivityWindow.applyOrientation(this, store);
        restoreNavigation(b);
        if (b == null && DotService.ACTION_SETTINGS.equals(getIntent().getAction())) {
            screen = Screen.SETTINGS;
            sub = SettingsScreen.DOT;
        }

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
        firstRunAccess = new FirstRunAccess(this, sheets);

        refreshController = new RefreshController((ViewGroup) getWindow().getDecorView(), store::readerRefreshEvery);
        loadApps();
        if (!store.favoritesInitialised()) seedFavorites();
        if (b == null) handleShellAction(getIntent());
        else readerPanelTask = b.getInt(READER_PANEL_TASK, -1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        homeWidgets.start();
    }

    @Override
    protected void onStop() {
        homeWidgets.stop();
        super.onStop();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        homeWidgets.result(request, result, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DotService.resume(this);
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
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio != null) audio.registerAudioDeviceCallback(panelAudioDevices, ui);
        IntentFilter pk = new IntentFilter();
        pk.addAction(Intent.ACTION_PACKAGE_ADDED);
        pk.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pk.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pk.addDataScheme("package");
        registerReceiver(packageEvents, pk);
        Notifs.setListener(n -> ui.post(() -> updateStatusBar(true)));
        if (libSearch == null) hideKeyboard();

        statusSignature = "";
        int returnTask = readerPanelTask;
        render();
        if (returnTask >= 0) {
            readerPanelTask = returnTask;
            openPanel();
        }
        refreshData(launchedSomething ? 0 : 30_000);
        if (launchedSomething && store.cleanOnHome()) refreshController.cleanWhenIdle();
        if (store.autoUpdate() && System.currentTimeMillis() - store.lastUpdateCheck() > 12 * 3_600_000L) checkUpdates(false);
        launchedSomething = false;
        Updater.onInstallFailure = this::showUpdateNotice;
        showUpdateNotice();
        if (screen == Screen.HOME) firstRunAccess.resume();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (content == null) return;
        if (ink.folio.night != dev.inkhome.folio.DisplayProbe.night(this)) {
            // Recreate the persistent shell surfaces as well as the current screen.
            recreate();
            return;
        }
        boolean menu = menuOpen;
        boolean panel = panelOpen;
        int returnTask = readerPanelTask;
        ink = new Ink(this, store.textScale(), store.uiScale());
        updateSleepPicture(false);
        applySystemBar();
        render();
        if (menu) openMenu();
        else if (panel) { readerPanelTask = returnTask; openPanel(); }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(READER_PANEL_TASK, readerPanelTask);
        state.putString("screen", screen.name());
        state.putString("libraryTab", libTab);
        state.putString("libraryDetail", libraryDetail);
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
        libraryDetail = state.getString("libraryDetail");
        libCollection = state.getString("collection");
        libSearch = state.getString("search");
        sub = state.getString("subpage");
        if (screen == Screen.SETTINGS && SettingsScreen.HOME.equals(sub)) sub = null;
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
        AudioManager audio = getSystemService(AudioManager.class);
        if (audio != null) audio.unregisterAudioDeviceCallback(panelAudioDevices);
        try { unregisterReceiver(systemEvents); } catch (Exception ignored) { }
        try { unregisterReceiver(packageEvents); } catch (Exception ignored) { }
        Notifs.setListener(null);
    }

    @Override
    protected void onDestroy() {
        homeEditor.discard();
        sheets.close();
        refreshController.close();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readerPanelTask = -1;
        if (handleShellAction(intent)) return;
        if (DotService.ACTION_SETTINGS.equals(intent.getAction())) {
            sheets.dismiss();
            openSettingsPage(SettingsScreen.DOT);
            return;
        }
        if (sheets.dismiss()) return;
        if (panelOpen) { closePanel(); return; }
        // Home on Home clears ghosting; otherwise it returns to the home page.
        if (screen == Screen.HOME && hasWindowFocus()) cleanScreen();
        else go(Screen.HOME);
    }

    void shellAccessSheet(boolean enabled) {
        sheets.message(getString(R.string.launcher_ink_shell), getString(R.string.ink_shell_description),
                getString(enabled ? R.string.launcher_turn_off_ink_shell : R.string.launcher_open_android_accessibility_settings), () -> {
                    if (enabled) {
                        ShellService.disable();
                        openSettingsPage(SettingsScreen.SHELL);
                    } else start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                });
    }

    private boolean handleShellAction(Intent intent) {
        if (ShellService.PANEL.equals(intent.getAction())) {
            readerPanelTask = intent.getIntExtra(READER_PANEL_TASK, -1);
            sheets.dismiss();
            content.post(() -> { if (!panelOpen || menuOpen) { closePanelSilently(); openPanel(); } });
            return true;
        }
        if (ShellService.SWITCHER.equals(intent.getAction())) {
            sheets.dismiss();
            pages.clear();
            libSearch = null;
            libraryDetail = null;
            libCollection = null;
            if (homeEditor.active()) homeEditor.discard();
            screen = Screen.APPS;
            sub = "switcher";
            render();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) dismissPanel();
        else if (homeEditor.active()) homeEditor.back();
        else if (libSearch != null) { libSearch = null; hideKeyboard(); render(); }
        else if (libraryDetail != null && screen == Screen.LIBRARY) { libraryDetail = null; render(); }
        else if (libCollection != null) { libCollection = null; render(); }
        else if (screen == Screen.SETTINGS && SetupScreen.FROM_HOME.equals(sub)) go(Screen.HOME);
        else if (sub != null) { sub = screen == Screen.SETTINGS ? SettingsScreen.parent(sub) : null; render(); }
        else if (screen != Screen.HOME) go(Screen.HOME);
    }

    /** Page keys are handled here, before any list can swallow them. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        Controls.Action action = store.controls().keyAction(e.getKeyCode(), false);
        Runnable run = controlHandler(action);
        if (run == null) return super.dispatchKeyEvent(e);
        // Boox can deliver a key-up without a matching key-down.
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) run.run();
        return true;
    }

    Runnable controlHandler(Controls.Action action) {
        switch (action) {
            case NEXT_PAGE: return pageNext;
            case PREV_PAGE: return pagePrev;
            case MENU: return () -> { if (menuOpen) closePanel(); else openMenu(); };
            case TOGGLE_PANEL: return this::togglePanel;
            case STRONG_CLEAN: return this::cleanScreen;
            default: return null;
        }
    }

    private final AudioDeviceCallback panelAudioDevices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { updatePanel(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { updatePanel(); }
        private void updatePanel() {
            if (panelOpen && !menuOpen) openPanel();
        }
    };

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
        if (!panelOpen && screen == Screen.APPS && ((AppsScreen) screen.view).showingRecent(this)) {
            render();
            return;
        }
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

    List<Library.Book> newest() {
        List<Library.Book> out = new ArrayList<>(books);
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    private int bookMinutes(Library.Book b) { return stats == null ? 0 : stats.forBook(b.path); }

    /** "42%" / "1 h 10 min read" / "Not started": the most useful one-line status for a book. */
    String bookStatus(Library.Book b) {
        if (b.finished) return getString(R.string.launcher_finished);
        int pc = b.percent();
        int mins = bookMinutes(b);
        if (pc >= 0 && mins > 0) return getString(R.string.launcher_percent_duration_read, pc, Stats.hm(this, mins));
        if (pc >= 0) return getString(R.string.launcher_percent, pc);
        if (mins > 0) return getString(R.string.launcher_duration_read, Stats.hm(this, mins));
        return getString(b.started() ? R.string.launcher_started : R.string.launcher_not_started);
    }

    // ================================================================== navigation

    void editHome() {
        editHome(null);
    }

    void editHome(String selectedKey) {
        sub = null;
        libSearch = null;
        screen = Screen.HOME;
        homeEditor.open(selectedKey);
    }

    void go(Screen s) {
        if (s != Screen.HOME && homeEditor.active()) homeEditor.discard();
        if (s != screen) {
            pages.clear();
            libSearch = null;
            libraryDetail = null;
            libCollection = null;
            sub = null;
        }
        screen = s;
        render();
    }

    void openHomeSetup() {
        pages.clear();
        sub = SetupScreen.FROM_HOME;
        screen = Screen.SETTINGS;
        render();
    }

    boolean coverPage;
    boolean homeSetupShown;

    private String renderedDestination;

    void render() {
        String destination = screen + "/" + sub + "/" + libTab + "/" + libCollection + "/" + (libSearch != null);
        if (renderedDestination != null && !destination.equals(renderedDestination) && store.subtleMotion() && !homeEditor.active()) {
            android.transition.TransitionManager.beginDelayedTransition(content,
                    new android.transition.Fade().setDuration(120));
        }
        renderedDestination = destination;
        coverPage = false;
        readerPanelTask = -1;
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
        if (homeEditor.active() && screen == Screen.HOME) {
            dock.addView(homeEditor.toolbar(), new LinearLayout.LayoutParams(-1, dockHeight));
            return;
        }
        boolean inSettings = screen == Screen.SETTINGS;
        ImageView left = ink.icon(inSettings ? "home" : "settings", 24);
        left.setPadding(ink.dp(12), ink.dp(12), ink.dp(12), ink.dp(12));
        left.setBackground(ink.pressable());
        left.setContentDescription(getString(inSettings ? R.string.launcher_home : R.string.launcher_settings));
        left.setOnClickListener(v -> go(inSettings ? Screen.HOME : Screen.SETTINGS));
        dock.addView(left, new LinearLayout.LayoutParams(ink.dp(52), dockHeight));

        int today = stats == null ? 0 : stats.today();
        int goal = store.dailyGoal();
        String label = goal > 0 ? getString(R.string.launcher_today_goal, today, goal) : getString(R.string.launcher_today_duration, Stats.hm(this, today));
        TextView mid = ink.center(ink.oneLine(ink.text(label, TypeScale.BODY, ink.TEXT)));
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(ink.pressable());
        mid.setContentDescription(getString(R.string.launcher_open_stats_description, label));
        mid.setOnClickListener(v -> go(Screen.STATS));
        dock.addView(mid, new LinearLayout.LayoutParams(0, dockHeight, 1f));

        ImageView right = ink.icon(menuOpen ? "down" : "up", 24);
        right.setPadding(ink.dp(12), ink.dp(12), ink.dp(12), ink.dp(12));
        right.setBackground(ink.pressable());
        right.setContentDescription(getString(menuOpen ? R.string.launcher_close_menu : R.string.launcher_open_menu));
        right.setOnClickListener(v -> { if (menuOpen) closePanel(); else openMenu(); });
        dock.addView(right, new LinearLayout.LayoutParams(ink.dp(52), dockHeight));
    }

    /** The chevron menu: every destination and the screen refresh, as a sheet above the bar. */
    private void openMenu() {
        readerPanelTask = -1;
        closePanelSilently();
        refreshController.changed(overlay, Change.OVERLAY);
        panelOpen = true;
        menuOpen = true;
        showOverlay();
        overlay.setOnClickListener(v -> closePanel());

        LinearLayout sheet = ink.col();
        sheet.setBackgroundColor(ink.PAPER);
        sheet.setClickable(true);
        sheet.setPadding(ink.dp(12), ink.dp(14), ink.dp(12), ink.dp(10));
        Object[][] items = {
                {"home", getString(R.string.launcher_home), (Runnable) () -> go(Screen.HOME), Screen.HOME},
                {"library", getString(R.string.launcher_library), (Runnable) () -> go(Screen.LIBRARY), Screen.LIBRARY},
                {"stats", getString(R.string.launcher_stats), (Runnable) () -> go(Screen.STATS), Screen.STATS},
                {"apps", getString(R.string.launcher_apps), (Runnable) () -> go(Screen.APPS), Screen.APPS},
                {"search", getString(R.string.launcher_search), (Runnable) () -> { go(Screen.LIBRARY); libSearch = ""; render(); }, null},
                {"clean", getString(R.string.launcher_clean_screen), (Runnable) () -> { closePanel(); ui.postDelayed(this::cleanScreen, 120); }, null},
                {"wifi", getString(R.string.launcher_wi_fi), (Runnable) this::openWifi, null},
                {"tune", getString(R.string.launcher_control_panel), (Runnable) () -> { closePanel(); openPanel(); }, null},
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
        col.setTransitionGroup(true);
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
        View strip = new Structure(ink.folio).statusStrip(time, Math.max(0, notes),
                wifi.connected ? wifi.level : -1, bat.percent, bat.charging, this::togglePanel,
                homeEditor.active() ? null : this::editHome);
        strip.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx = Math.abs(event.getRawX() - startX);
                        float dy = event.getRawY() - startY;
                        int slop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
                        if (dy > slop && dy > dx) {
                            if (!panelOpen) view.performClick();
                        } else if (Math.abs(dy) <= slop && dx <= slop) {
                            view.performClick();
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        statusBar.addView(strip, new LinearLayout.LayoutParams(-1, -2));
    }

    private void togglePanel() {
        if (panelOpen) dismissPanel(); else openPanel();
    }

    private void dismissPanel() {
        int returnTask = readerPanelTask;
        closePanel();
        if (returnTask < 0) return;
        ActivityManager manager = getSystemService(ActivityManager.class);
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
            try {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info.taskId == returnTask && new ComponentName(this, ReaderActivity.class).equals(info.baseActivity)) {
                    task.moveToFront();
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // The reader task may already have closed.
            }
        }
    }

    private void showOverlay() {
        overlay.animate().cancel();
        overlay.setVisibility(View.VISIBLE);
        if (store.subtleMotion()) {
            overlay.setAlpha(0);
            overlay.animate().alpha(1).setDuration(120).start();
        } else overlay.setAlpha(1);
    }

    private void closePanelSilently() {
        if (panelOpen) refreshController.changed(content, Change.OVERLAY);
        panelOpen = false;
        overlay.removeAllViews();
        overlay.animate().cancel();
        overlay.setAlpha(1);
        overlay.setVisibility(View.GONE);
        if (menuOpen) {
            FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) overlay.getLayoutParams();
            bounds.bottomMargin = 0;
            overlay.setLayoutParams(bounds);
        }
        menuOpen = false;
    }

    private void closePanel() {
        readerPanelTask = -1;
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
        showOverlay();
        overlay.setOnClickListener(v -> dismissPanel());

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
        TextView date = ink.muted(new SimpleDateFormat(getString(R.string.launcher_date_pattern), Locale.getDefault()).format(now), 15);
        date.setPadding(0, ink.dp(6), 0, 0);
        left.addView(date);
        top.addView(left, ink.weight(1));
        LinearLayout right = ink.col();
        right.setGravity(Gravity.END);
        right.addView(ink.text(getString(R.string.launcher_percent, bat.percent), TypeScale.FIGURE, ink.TEXT));
        TextView bs = ink.muted(bat.charging ? (bat.percent >= 100 ? getString(R.string.launcher_charged) : getString(R.string.launcher_charging)) : getString(R.string.launcher_on_battery), 14);
        bs.setPadding(0, ink.dp(6), 0, 0);
        right.addView(bs);
        top.addView(right);
        p.addView(top);
        p.addView(ink.progress(bat.percent / 100f, 4), ink.m(-1, -2, 0, 14, 0, 16));

        String wifiState = !wifi.enabled ? getString(R.string.launcher_off) : !wifi.connected ? getString(R.string.launcher_not_connected)
                : wifi.name != null ? wifi.name
                : getResources().getStringArray(R.array.launcher_wifi_signal_levels)[Math.max(0, Math.min(4, wifi.level))];
        int notes = Notifs.count();
        Capabilities caps = DisplayProbe.capabilities(this);
        List<View> tiles = new ArrayList<>();
        if (caps.wifi && (hasPanelSettings(Settings.Panel.ACTION_WIFI) || hasPanelSettings(Settings.ACTION_WIFI_SETTINGS))) {
            tiles.add(panelTile("wifi", getString(R.string.launcher_wi_fi), wifiState, wifi.connected, this::openWifi));
        }
        if (caps.bluetooth && hasPanelSettings(Settings.ACTION_BLUETOOTH_SETTINGS)) {
            tiles.add(panelTile("bluetooth", getString(R.string.launcher_bluetooth), getString(R.string.launcher_device_settings), false,
                    () -> panelSettings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        }
        if (ink.folio.displayClass.isEink()) tiles.add(panelTile("clean", getString(R.string.launcher_clean_screen), getString(R.string.service_ui_strong_clean), false, () -> {
            closePanel();
            ui.post(this::cleanScreen);
        }));
        if (caps.rotation) {
            boolean locked = store.portraitLocked();
            tiles.add(panelTile("rotate", getString(R.string.launcher_rotation), getString(locked ? R.string.launcher_portrait : R.string.launcher_follow_system), locked, () -> {
                store.setPortraitLocked(!locked);
                ActivityWindow.applyOrientation(this, store);
                openPanel();
            }));
        }
        boolean quiet = store.quiet() && Notifs.count() >= 0;
        tiles.add(panelTile("quiet", getString(R.string.launcher_quiet), notes < 0 ? getString(R.string.launcher_allow_access) : getString(quiet ? R.string.launcher_on : R.string.launcher_off), quiet,
                () -> { if (changeQuiet(!quiet)) openPanel(); }));
        if (DotService.available(this)) {
            boolean enabled = DotService.enabled(this) && DotService.permitted(this);
            tiles.add(panelTile("clean", getString(R.string.launcher_ink_dot), getString(enabled ? R.string.launcher_on : R.string.launcher_off), enabled,
                    () -> { DotService.setEnabled(this, !enabled); openPanel(); }));
        }
        if (caps.frontlight && (DisplayProbe.hasOnyxLightDialog(this) || hasPanelSettings(Settings.ACTION_DISPLAY_SETTINGS))) {
            tiles.add(panelTile("light", getString(caps.warmLight ? R.string.launcher_light_and_warmth : R.string.launcher_light), getString(R.string.launcher_display_settings), false,
                    this::openPanelLight));
        }
        for (int i = 0; i < tiles.size(); i += 2) {
            addTiles(p, tiles.get(i), i + 1 < tiles.size() ? tiles.get(i + 1) : new View(this));
        }
        if (caps.mediaVolume) addPanelVolume(p);

        List<Notifs.Item> list = Notifs.items();
        LinearLayout nh = ink.row();
        String caption = notes < 0 ? getString(R.string.launcher_notifications) : getString(R.string.launcher_notification_count, notes);
        nh.addView(ink.text(caption, TypeScale.LABEL, ink.TEXT), ink.weight(1));
        if (notes < 0) {
            nh.addView(Button.create(ink.folio, getString(R.string.launcher_allow_access), Button.Style.TEXT, v -> openNotifications()));
        }
        p.addView(nh, ink.m(-1, -2, 0, 8, 0, 0));
        if (!list.isEmpty()) {
            TextView clear = ink.text(getString(R.string.launcher_clear_all), TypeScale.META, ink.TEXT2);
            clear.setPadding(ink.dp(10), 0, 0, ink.dp(8));
            clear.setOnClickListener(v -> {
                List<Notifs.Item> removed = Notifs.stageClear();
                closePanel();
                if (!removed.isEmpty()) sheets.notice(getString(R.string.launcher_notifications_cleared),
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
                top1.addView(ink.faint(Library.ago(this, it.when), 12));
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
                TextView more = ink.muted(getString(R.string.launcher_notifications_more, list.size() - shown), 13);
                more.setPadding(ink.dp(14), ink.dp(8), ink.dp(14), ink.dp(10));
                more.setOnClickListener(v -> openNotifications());
                card.addView(more);
            }
            p.addView(card);
        }

        p.addView(ink.hair());
        LinearLayout shortcuts = ink.row();
        shortcuts.addView(Button.create(ink.folio, getString(R.string.launcher_settings), Button.Style.TEXT, v -> openSettingsPage(null)), ink.weight(1));
        shortcuts.addView(Button.create(ink.folio, getString(R.string.launcher_switcher), Button.Style.TEXT, v -> {
            pages.clear();
            libSearch = null;
            libraryDetail = null;
            libCollection = null;
            if (homeEditor.active()) homeEditor.discard();
            screen = Screen.APPS;
            sub = "switcher";
            render();
        }), ink.weight(1));
        p.addView(shortcuts);
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

    void openSettingsPage(String page) {
        if (SettingsScreen.HOME.equals(page)) { editHome(); return; }
        if (homeEditor.active()) homeEditor.discard();
        pages.clear();
        libSearch = null;
        libCollection = null;
        screen = Screen.SETTINGS;
        sub = page;
        render();
    }

    boolean changeQuiet(boolean enabled) {
        if (Notifs.setQuiet(enabled)) return true;
        if (!SetupScreen.notificationsAllowed(this)) panelSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        else toast(getString(R.string.launcher_notification_listener_is_unavailable));
        return false;
    }

    private boolean hasPanelSettings(String action) {
        return new Intent(action).resolveActivity(getPackageManager()) != null;
    }

    private void openPanelLight() {
        if (DisplayProbe.hasOnyxLightDialog(this)) {
            closePanel();
            try {
                sendBroadcast(new Intent("action.show.brightness.dialog").setPackage("com.android.systemui"));
                return;
            } catch (SecurityException ignored) { }
        }
        panelSettings(Settings.ACTION_DISPLAY_SETTINGS);
    }

    private void panelSettings(String action) {
        closePanel();
        if (!start(new Intent(action))) toast(getString(R.string.launcher_device_settings_unavailable));
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
        int min = audio.getStreamMinVolume(stream);
        int range = max - min;
        if (range <= 0) return;
        // A bounded number of stops keeps unusually fine-grained volume scales usable.
        int stops = Math.min(10, range);
        String[] values = new String[stops + 1];
        for (int i = 0; i <= stops; i++) values[i] = getString(R.string.launcher_percent, Math.round(i * 100f / stops));
        int selected = Math.round(stops * (audio.getStreamVolume(stream) - min) / (float) range);
        panel.addView(ink.text(getString(R.string.launcher_volume), TypeScale.LABEL, ink.TEXT));
        panel.addView(new LevelBar(ink.folio, getString(R.string.launcher_volume), values, selected, index -> {
            try {
                audio.setStreamVolume(stream, min + Math.round(index * range / (float) stops), 0);
            } catch (SecurityException denied) {
                panelSettings(Settings.ACTION_SOUND_SETTINGS);
            }
        }));
    }

    private void openWifi() {
        closePanel();
        if (!start(new Intent(Settings.Panel.ACTION_WIFI)))
            panelSettings(Settings.ACTION_WIFI_SETTINGS);
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
            toast(getString(R.string.launcher_open_notifications_hint));
        }
    }

    static final class Battery { int percent; boolean charging; }
    static final class Wifi { boolean enabled, connected; int level = -1; String name; }

    Battery battery() {
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
        return Library.humanSize(this, new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes());
    }

    String timeText(Date d) {
        String c = store.clock();
        if (c.equals("24")) return new SimpleDateFormat(getString(R.string.launcher_clock_24_pattern), Locale.getDefault()).format(d);
        if (c.equals("12")) return new SimpleDateFormat(getString(R.string.launcher_clock_12_pattern), Locale.getDefault()).format(d);
        return android.text.format.DateFormat.getTimeFormat(this).format(d);
    }

    // ================================================================== home

    private Bitmap wallpaperCache;
    private String wallpaperKey = "";

    private String requestedWallpaper = "";

    /** Fade the chosen picture toward the current paper colour for legible text. */
    void applyWallpaper() {
        String path = store.wallpaper();
        if (path.isEmpty() || !new File(path).exists()) {
            requestedWallpaper = "";
            content.setBackground(null);
            return;
        }
        int w = content.getWidth(), h = content.getHeight();
        if (w == 0 || h == 0) { content.post(this::applyWallpaper); return; }
        boolean mono = ink.folio.displayClass == dev.inkhome.folio.core.DisplayClass.MONO_EINK;
        int paper = ink.PAPER;
        float fade = store.wallpaperFade();
        String key = path + "|" + fade + "|" + w + "x" + h + "|" + mono + "|" + paper;
        requestedWallpaper = key;
        if (key.equals(wallpaperKey) && wallpaperCache != null) {
            content.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), wallpaperCache));
            return;
        }
        thumbs.execute(() -> {
            Bitmap pic = SleepScreen.thumb(new File(path), w, h, mono, paper);
            if (pic == null) return;
            Canvas cv = new Canvas(pic);
            Paint veil = new Paint();
            veil.setColor(paper);
            veil.setAlpha(Math.round(fade * 255));
            cv.drawRect(0, 0, w, h, veil);
            ui.post(() -> {
                if (!key.equals(requestedWallpaper) || isDestroyed()) return;
                wallpaperCache = pic;
                wallpaperKey = key;
                if (screen == Screen.HOME) content.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), pic));
            });
        });
    }

    View homeBookHero(Library.Book b, boolean newestInLibrary, int cw, boolean wrapDetails) {
        int chh = Math.round(cw * 10f / 7);
        LinearLayout hero = ink.row();
        hero.setGravity(Gravity.TOP);
        hero.setBackground(ink.pressable());
        hero.addView(ink.cover(Covers.get(this, b, store.realCovers())), new LinearLayout.LayoutParams(ink.dp(cw), ink.dp(chh)));

        LinearLayout info = ink.col();
        info.setPadding(ink.dp(20), ink.dp(2), 0, 0);
        if (newestInLibrary) {
            TextView n = ink.faint(getString(R.string.launcher_newest_in_your_library), 13);
            n.setPadding(0, 0, 0, ink.dp(8));
            info.addView(n);
        }
        info.addView(ink.lines(ink.text(b.title, TypeScale.TITLE, ink.TEXT), 4));
        if (!b.author.isEmpty()) {
            TextView a = ink.oneLine(ink.text(b.author, TypeScale.BYLINE, ink.TEXT2));
            a.setPadding(0, ink.dp(8), 0, 0);
            info.addView(a);
        }
        info.addView(ink.vflex());

        LinearLayout app = ink.row();
        ResolveInfo reader = readerFor(b);
        if (reader != null) {
            info.addView(app);
            app.addView(appIcon(reader.loadIcon(getPackageManager()), 20));
            String rlabel = String.valueOf(reader.loadLabel(getPackageManager()));
            TextView rn = ink.oneLine(ink.medium(rlabel, 15));
            rn.setPadding(ink.dp(8), 0, 0, 0);
            app.addView(rn, ink.weight(1));
        }
        if (b.progress >= 0 || b.finished) info.addView(ink.progress(b.finished ? 1f : b.progress, 4), ink.m(-1, -2, 0, 14, 0, 0));
        LinearLayout status = ink.row();
        status.setPadding(0, ink.dp(6), 0, 0);
        int percent = b.percent();
        String progress = b.finished ? getString(R.string.launcher_finished) : percent >= 0 ? getString(R.string.launcher_percent, percent) : getString(b.started() ? R.string.launcher_started : R.string.launcher_not_started);
        status.addView(ink.text(progress, TypeScale.META, ink.TEXT));
        int minutes = bookMinutes(b);
        String when = minutes > 0 ? getString(R.string.launcher_duration_read, Stats.hm(this, minutes)) : "";
        if (b.lastRead > 0) when = when.isEmpty() ? Library.ago(this, b.lastRead)
                : getString(R.string.launcher_metadata_pair, when, Library.ago(this, b.lastRead));
        TextView activity = ink.oneLine(ink.text(when, TypeScale.META, ink.TEXT2));
        activity.setGravity(Gravity.END);
        status.addView(activity, ink.weight(1));
        info.addView(status);
        if (wrapDetails) info.setMinimumHeight(ink.dp(chh));
        hero.addView(info, new LinearLayout.LayoutParams(0, wrapDetails ? -2 : ink.dp(chh), 1f));
        hero.setOnClickListener(v -> openBook(b));
        hero.setOnLongClickListener(v -> { bookMenu(b); return true; });
        return hero;
    }

    /** Page whole rows in the space between the hero and favorites. */
    View homeBookList(String key, List<Library.Book> list) {
        LinearLayout head = ink.row();
        head.addView(ink.text(getString(R.string.launcher_up_next), TypeScale.LABEL, ink.TEXT), ink.weight(1));
        View listView = cells(key, head, list.size(), 0, 52, i -> homeBookRow(list.get(i)));
        listView.setTag("list");
        return listView;
    }

    View homeBookRow(Library.Book bk) {
        LinearLayout r = ink.row();
        r.setBackground(ink.listRow(40));
        r.addView(ink.cover(Covers.get(this, bk, store.realCovers())), ink.m(ink.dp(30), ink.dp(43), 0, 0, 12, 0));
        LinearLayout t = ink.col();
        t.addView(ink.oneLine(ink.text(bk.title, TypeScale.BODY_LG, ink.TEXT)));
        String meta = getString(R.string.launcher_book_row_status, bk.author.isEmpty() ? bk.format() : bk.author, bookStatus(bk));
        TextView m = ink.oneLine(ink.muted(meta, 12));
        m.setPadding(0, ink.dp(3), 0, 0);
        t.addView(m);
        r.addView(t, ink.weight(1));
        if (bk.progress >= 0 && !bk.finished) r.addView(ink.progress(bk.progress, 3), ink.m(ink.dp(44), -2, 10, 0, 0, 0));
        r.setOnClickListener(v -> openBook(bk));
        r.setOnLongClickListener(v -> { bookMenu(bk); return true; });
        return r;
    }

    View emptyHero() {
        return dev.inkhome.folio.widget.EmptyState.create(ink.folio,
                booksLoaded ? getString(R.string.launcher_no_book_yet) : getString(R.string.launcher_opening_your_library),
                booksLoaded ? getString(R.string.launcher_empty_library_hint)
                        : getString(R.string.launcher_finding_books_on_this_tablet), getString(R.string.launcher_library), v -> go(Screen.LIBRARY));
    }

    void homeShelf(LinearLayout col, List<Library.Book> reading, String key) {
        List<Library.Book> shelf = new ArrayList<>(reading);
        Set<String> onShelf = new HashSet<>();
        for (Library.Book book : reading) onShelf.add(book.path);
        for (Library.Book book : newest()) {
            if (!book.finished && onShelf.add(book.path)) shelf.add(book);
        }
        if (shelf.isEmpty()) {
            col.addView(emptyHero());
            return;
        }
        View grid = bookGrid(key, shelf);
        grid.setTag("list");
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    void homeMinimal(LinearLayout col, List<Library.Book> reading) {
        if (reading.isEmpty()) return;
        Library.Book b = reading.get(0);
        LinearLayout c = ink.col();
        c.setPadding(ink.dp(18), ink.dp(16), ink.dp(18), ink.dp(16));
        c.setBackground(ink.pressable());
        TextView t = ink.lines(ink.text(b.title, TypeScale.TITLE, ink.TEXT), 2);
        t.setPadding(0, ink.dp(8), 0, ink.dp(8));
        c.addView(t);
        c.addView(ink.muted(bookStatus(b), 14));
        if (b.progress >= 0) c.addView(ink.progress(b.progress, 4), ink.m(-1, -2, 0, 10, 0, 0));
        c.setOnClickListener(v -> openBook(b));
        c.setOnLongClickListener(v -> { bookMenu(b); return true; });
        col.addView(c);
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
        if (grid) return bookGrid("lib", list);
        return cells("lib", null, list.size(), 0, 72, i -> {
            Library.Book b = list.get(i);
            LinearLayout row = ink.row();
            row.setBackground(ink.listRow(0));
            row.addView(ink.cover(Covers.get(this, b, store.realCovers())), ink.dp(40), ink.dp(57));
            LinearLayout info = ink.col();
            info.setPadding(ink.dp(14), 0, ink.dp(8), 0);
            info.addView(ink.oneLine(ink.text(b.title, TypeScale.BODY_LG, ink.TEXT)));
            int mins = bookMinutes(b);
            String meta = b.author.isEmpty() ? b.format() : b.author;
            if (mins > 0) meta = getString(R.string.launcher_metadata_read_time, meta, Stats.hm(this, mins));
            info.addView(ink.oneLine(ink.text(meta, TypeScale.META, ink.TEXT2)));
            if (b.finished || b.progress >= 0)
                info.addView(ink.progress(b.finished ? 1 : b.progress, 3), ink.m(ink.dp(150), -2, 0, 4, 0, 0));
            row.addView(info, ink.weight(1));
            String status = b.finished ? getString(R.string.launcher_finished) : b.percent() >= 0 ? getString(R.string.launcher_percent, b.percent()) : "";
            row.addView(ink.text(status, TypeScale.META, ink.TEXT));
            row.setOnClickListener(v -> openBook(b));
            row.setOnLongClickListener(v -> { bookMenu(b); return true; });
            return row;
        });
    }

    View bookGrid(String pageKey, List<Library.Book> list) {
        dev.inkhome.folio.core.WindowClass window = ink.folio.windowClass();
        float width = getResources().getConfiguration().screenWidthDp / ink.folio.uiScale;
        float available = width - 2 * window.margin;
        if ("lib".equals(pageKey) && libraryDetail != null && window == WindowClass.EXPANDED) {
            available = (available - window.gutter) * 0.6f;
        }
        int columns = Math.max(1, (int) ((available + window.gutter) / (96 + window.gutter)));
        float cellWidth = (available - (columns - 1) * window.gutter) / columns;
        int height = Math.round(cellWidth * 10 / 7) + 68;
        return cells(pageKey, null, list.size(), 96, height, i -> {
            Library.Book book = list.get(i);
            View cell = dev.inkhome.folio.widget.BookViews.cell(ink.folio,
                    ink.cover(Covers.get(this, book, store.realCovers())), book.title,
                    book.finished ? 100 : Math.max(0, book.percent()), () -> openBook(book));
            cell.setPadding(0, ink.dp(12), 0, 0);
            cell.setOnLongClickListener(v -> { bookMenu(book); return true; });
            return cell;
        });
    }

    void newCollection() {
        askText(getString(R.string.launcher_new_collection), "", name -> {
            Map<String, List<String>> collections = store.collections();
            if (!collections.containsKey(name)) collections.put(name, new ArrayList<>());
            store.setCollections(collections);
            render();
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
                TextView t = ink.medium(getString(R.string.launcher_new_collection), 16);
                t.setPadding(ink.dp(12), 0, 0, 0);
                r.addView(t);
                r.setOnClickListener(v -> newCollection());
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
                choose(name, new String[]{getString(R.string.launcher_rename), getString(R.string.launcher_delete_collection)}, -1, w -> {
                    Map<String, List<String>> m = store.collections();
                    if (w == 0) askText(getString(R.string.launcher_rename_collection), name, nn -> {
                        Map<String, List<String>> out = new LinkedHashMap<>();
                        for (Map.Entry<String, List<String>> e : m.entrySet()) out.put(e.getKey().equals(name) ? nn : e.getKey(), e.getValue());
                        store.setCollections(out);
                        render();
                    });
                    else {
                        m.remove(name);
                        store.setCollections(m);
                        render();
                        sheets.notice(getString(R.string.launcher_collection_deleted), () -> {
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

    void libraryOptions() {
        LinearLayout options = ink.col();
        options.setPadding(ink.dp(16), ink.dp(4), ink.dp(16), ink.dp(12));
        libraryChoices(options, getString(R.string.launcher_sort_by), getResources().getStringArray(R.array.library_sort_labels), LibraryScreen.SORT_KEYS,
                store.librarySort(), store::setLibrarySort);
        libraryChoices(options, getString(R.string.launcher_show), getResources().getStringArray(R.array.library_type_labels), LibraryScreen.TYPE_KEYS,
                store.libraryType(), store::setLibraryType);
        libraryChoices(options, getString(R.string.launcher_view), new String[]{getString(R.string.launcher_grid), getString(R.string.launcher_list)}, new String[]{"grid", "list"},
                store.libraryGrid() ? "grid" : "list", key -> store.setLibraryGrid(key.equals("grid")));
        sheets.custom(getString(R.string.launcher_sort_and_filter), options);
    }

    private void libraryChoices(LinearLayout options, String title, String[] labels,
                                String[] keys, String current, java.util.function.Consumer<String> select) {
        options.addView(new Structure(ink.folio).sectionHead(title, true, null));
        for (int i = 0; i < labels.length; i++) {
            String key = keys[i];
            boolean selected = key.equals(current);
            TextView choice = ink.body(getString(R.string.launcher_selected_choice, selected ? "●" : "○", labels[i]));
            choice.setSelected(selected);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setMinHeight(ink.dp(44));
            choice.setOnClickListener(v -> {
                sheets.dismiss();
                select.accept(key);
                pages.put("lib", 0);
                render();
            });
            options.addView(choice);
        }
    }

    void bookMenu(Library.Book b) {
        showBookSheet(b, false);
    }

    private void showBookSheet(Library.Book b, boolean details) {
        if (details && screen == Screen.LIBRARY && libSearch == null && ink.folio.windowClass() == WindowClass.EXPANDED) {
            libraryDetail = b.path;
            render();
            return;
        }
        sheets.custom(getString(details ? R.string.launcher_book_details : R.string.launcher_book_actions), bookInformation(b, details));
    }

    LinearLayout bookInformation(Library.Book b, boolean details) {
        LinearLayout body = ink.col();
        body.setPadding(ink.dp(16), ink.dp(12), ink.dp(16), ink.dp(8));
        LinearLayout heading = ink.row();
        int width = details ? 108 : 62;
        heading.addView(ink.cover(Covers.get(this, b, store.realCovers())), ink.dp(width), ink.dp(width * 10f / 7));
        LinearLayout labels = ink.col();
        labels.setPadding(ink.dp(16), 0, 0, 0);
        labels.addView(ink.lines(ink.text(b.title, TypeScale.TITLE_SM, ink.TEXT), 4));
        if (!b.author.isEmpty()) labels.addView(ink.text(b.author, TypeScale.BYLINE, ink.TEXT2));
        labels.addView(ink.text(bookStatus(b), TypeScale.META, ink.TEXT2));
        heading.addView(labels, ink.weight(1));
        body.addView(heading);
        if (details) {
            if (!b.pages.isEmpty()) body.addView(ink.body(getString(R.string.launcher_page, b.pages)));
            if (b.lastRead > 0) body.addView(ink.body(getString(R.string.launcher_last_read, Library.ago(this, b.lastRead))));
            body.addView(ink.body(getString(R.string.launcher_metadata_pair, b.format(), Library.humanSize(this, b.size))));
            TextView path = ink.text(b.path, TypeScale.META, ink.TEXT2);
            path.setTextIsSelectable(true);
            body.addView(path);
        }
        String open = getString(b.started() ? R.string.launcher_continue_reading : R.string.launcher_open_book);
        body.addView(dev.inkhome.folio.widget.Button.create(ink.folio, open,
                dev.inkhome.folio.widget.Button.Style.PRIMARY, v -> { sheets.dismiss(); openBook(b); }),
                ink.m(-1, -2, 0, 12, 0, 4));
        List<MenuAction> actions = new ArrayList<>();
        actions.add(MenuAction.OPEN_WITH);
        actions.add(MenuAction.ADD_TO_COLLECTION);
        if (libCollection != null) actions.add(MenuAction.REMOVE_FROM_THIS_COLLECTION);
        actions.add(b.finished ? MenuAction.MARK_AS_NOT_FINISHED : MenuAction.MARK_AS_FINISHED);
        if (b.started() && !b.finished) actions.add(MenuAction.REMOVE_FROM_READING);
        if (!details) actions.add(MenuAction.DETAILS);
        for (MenuAction action : actions) {
            body.addView(new Structure(ink.folio).listRow(null, getString(action.label), null, null, 44, false, () -> {
                sheets.dismiss();
                bookSheetAction(action, b);
            }));
        }
        return body;
    }

    private enum MenuAction {
        OPEN_WITH(R.string.launcher_action_open_with),
        ADD_TO_COLLECTION(R.string.launcher_action_add_to_collection),
        REMOVE_FROM_THIS_COLLECTION(R.string.launcher_action_remove_from_this_collection),
        MARK_AS_NOT_FINISHED(R.string.launcher_action_mark_as_not_finished),
        MARK_AS_FINISHED(R.string.launcher_action_mark_as_finished),
        REMOVE_FROM_READING(R.string.launcher_action_remove_from_reading),
        DETAILS(R.string.launcher_action_details),
        OPEN(R.string.launcher_action_open),
        REMOVE_FROM_FAVORITES(R.string.launcher_action_remove_from_favorites),
        ADD_TO_FAVORITES(R.string.launcher_action_add_to_favorites),
        MOVE_TO_FIRST_FAVORITE(R.string.launcher_action_move_to_first_favorite),
        EXCLUDE_READING_TIME(R.string.launcher_action_exclude_reading_time),
        COUNT_TIME_AS_READING(R.string.launcher_action_count_time_as_reading),
        SHOW_IN_APPS(R.string.launcher_action_show_in_apps),
        HIDE_FROM_APPS(R.string.launcher_action_hide_from_apps),
        APP_INFO(R.string.launcher_action_app_info);

        final int label;

        MenuAction(int label) { this.label = label; }
    }

    private void bookSheetAction(MenuAction action, Library.Book b) {
        switch (action) {
            case OPEN_WITH: openWith(b); break;
            case ADD_TO_COLLECTION: addToCollection(b); break;
            case REMOVE_FROM_THIS_COLLECTION: {
                Map<String, List<String>> m = store.collections();
                List<String> l = m.get(libCollection);
                if (l == null || !l.contains(b.path)) break;
                final String collection = libCollection;
                final int position = l.indexOf(b.path);
                l.remove(position);
                store.setCollections(m);
                render();
                sheets.notice(getString(R.string.launcher_removed_from_collection), () -> {
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
            case REMOVE_FROM_READING: {
                long previous = store.dismissedAt(b.path);
                store.dismiss(b.path);
                render();
                sheets.notice(getString(R.string.launcher_removed_from_reading), () -> {
                    store.setDismissedAt(b.path, previous);
                    render();
                }, null);
                break;
            }
            case DETAILS: showBookSheet(b, true); break;
            case MARK_AS_NOT_FINISHED:
            case MARK_AS_FINISHED:
                boolean previous = b.finished;
                setBookFinished(b, !previous);
                sheets.notice(getString(previous ? R.string.launcher_marked_as_not_finished : R.string.launcher_marked_as_finished),
                        () -> setBookFinished(b, previous), null);
        }
    }

    private void setBookFinished(Library.Book book, boolean finished) {
        book.finished = finished;
        store.setFinished(book.path, finished);
        dataSignature = 0;
        render();
    }

    private void addToCollection(Library.Book b) {
        Map<String, List<String>> m = store.collections();
        List<String> names = new ArrayList<>(m.keySet());
        names.add(getString(R.string.launcher_new_collection_ellipsis));
        choose(getString(R.string.launcher_add_to_collection), names.toArray(new String[0]), -1, w -> {
            if (w == names.size() - 1) {
                askText(getString(R.string.launcher_new_collection), "", name -> {
                    Map<String, List<String>> mm = store.collections();
                    List<String> l = mm.containsKey(name) ? mm.get(name) : new ArrayList<>();
                    if (!l.contains(b.path)) l.add(b.path);
                    mm.put(name, l);
                    store.setCollections(mm);
                    toast(getString(R.string.launcher_added_to, name));
                });
            } else {
                List<String> l = m.get(names.get(w));
                if (!l.contains(b.path)) l.add(b.path);
                store.setCollections(m);
                toast(getString(R.string.launcher_added_to, names.get(w)));
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
            labels.add(getString(R.string.launcher_ink_home_reader));
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
        if (intents.isEmpty()) { toast(getString(R.string.launcher_no_reader, b.format())); return; }
        choose(getString(R.string.launcher_open_with), labels.toArray(new String[0]), -1, w -> {
            Intent intent = intents.get(w);
            if (intent.getComponent() != null && "org.koreader.launcher".equals(intent.getComponent().getPackageName())) {
                grantUriPermission("org.koreader.launcher", intent.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (start(intent)) store.markOpened(b.path);
            else toast(getString(R.string.launcher_cannot_open, labels.get(w)));
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
            labels.add(getString(R.string.launcher_ink_home_reader));
            pkgs.add(Store.BUILT_IN);
        }
        for (ResolveInfo ri : readers) {
            labels.add(String.valueOf(ri.loadLabel(getPackageManager())));
            pkgs.add(ri.activityInfo.packageName);
        }
        labels.add(getString(R.string.launcher_ask_every_time));
        pkgs.add("ask");
        String cur = booksKind ? store.bookReader() : store.pdfReader();
        if (cur.isEmpty()) cur = booksKind ? Store.BUILT_IN : NEOREADER_PKG;
        choose(getString(booksKind ? R.string.launcher_open_books_with : R.string.launcher_open_pdfs_and_comics_with), labels.toArray(new String[0]), pkgs.indexOf(cur), w -> {
            if (booksKind) store.setBookReader(pkgs.get(w)); else store.setPdfReader(pkgs.get(w));
            render();
        });
    }

    String readerName(String pkg) {
        if (pkg.equals(Store.BUILT_IN)) return getString(R.string.launcher_ink_home_reader);
        if (pkg.isEmpty()) pkg = NEOREADER_PKG;
        if (pkg.equals("ask")) return getString(R.string.launcher_ask_every_time);
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return getString(R.string.launcher_neoreader);
        }
    }

    // ================================================================== reading stats

    /** Two equal-height cards side by side. */
    void chooseGoal() {
        int[] goals = {0, 10, 15, 20, 30, 45, 60, 90, 120};
        String[] labels = new String[goals.length];
        int cur = 0;
        for (int i = 0; i < goals.length; i++) {
            labels[i] = goals[i] == 0 ? getString(R.string.launcher_no_goal_just_show_time_read) : getString(R.string.launcher_minutes_a_day, goals[i]);
            if (goals[i] == store.dailyGoal()) cur = i;
        }
        choose(getString(R.string.launcher_daily_goal), labels, cur, w -> {
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
        String[] defaults = {"org.chromium.chrome/com.google.android.apps.chrome.Main",
                "com.android.documentsui/com.android.documentsui.LauncherActivity"};
        for (String d : defaults) if (findApp(d) != null) want.add(d);
        for (App app : apps) if (booxShellApp(app)) store.setHidden(app.key, true);
        store.setFavorites(want);
    }

    static boolean booxShellApp(App app) {
        return BOOX_HOME.equals(app.key) || "com.onyx.floatingbutton".equals(app.pkg);
    }

    App findApp(String key) {
        for (App a : apps) if (a.key.equals(key)) return a;
        return null;
    }

    List<App> favoriteApps() {
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

    View appGrid(int count, IntFunction<View> builder) {
        return cells("appsGrid", null, count, 96, 88, builder);
    }

    void searchApps(String initial, java.util.function.Consumer<String> result) {
        sheets.prompt(getString(R.string.launcher_search_apps), initial, result);
    }

    void appMenu(App a) {
        boolean fav = store.isFavorite(a.key);
        boolean reads = store.readingApps().contains(a.pkg);
        List<MenuAction> opts = new ArrayList<>();
        opts.add(MenuAction.OPEN);
        opts.add(fav ? MenuAction.REMOVE_FROM_FAVORITES : MenuAction.ADD_TO_FAVORITES);
        if (fav) opts.add(MenuAction.MOVE_TO_FIRST_FAVORITE);
        opts.add(reads ? MenuAction.EXCLUDE_READING_TIME : MenuAction.COUNT_TIME_AS_READING);
        opts.add(store.hidden().contains(a.key) ? MenuAction.SHOW_IN_APPS : MenuAction.HIDE_FROM_APPS);
        opts.add(MenuAction.APP_INFO);
        String[] labels = new String[opts.size()];
        for (int i = 0; i < opts.size(); i++) labels[i] = getString(opts.get(i).label);
        choose(displayLabel(a), labels, -1, w -> {
            switch (opts.get(w)) {
                case OPEN: launch(a); break;
                case ADD_TO_FAVORITES: case REMOVE_FROM_FAVORITES: store.toggleFavorite(a.key); render(); break;
                case MOVE_TO_FIRST_FAVORITE: {
                    List<String> l = store.favorites();
                    l.remove(a.key);
                    l.add(0, a.key);
                    store.setFavorites(l);
                    render();
                    break;
                }
                case COUNT_TIME_AS_READING: case EXCLUDE_READING_TIME:
                    if (a.pkg.equals(NEOREADER_PKG)) { toast(getString(R.string.launcher_neoreader_always_counts)); break; }
                    store.setReadingApp(a.pkg, !reads);
                    render();
                    break;
                case SHOW_IN_APPS: store.setHidden(a.key, false); render(); break;
                case HIDE_FROM_APPS: {
                    store.setHidden(a.key, true);
                    List<String> f = store.favorites();
                    f.remove(a.key);
                    store.setFavorites(f);
                    render();
                    break;
                }
                case APP_INFO: start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); break;
            }
        });
    }

    void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!start(i)) toast(getString(R.string.launcher_cannot_open, a.label));
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
        final boolean compact;

        Rows() { this(ink.folio.windowClass() == WindowClass.COMPACT); }
        Rows(boolean compact) { this.compact = compact; }

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
            for (int i = 0; i < values.length; i++) labels[i] = getString(R.string.launcher_percent, values[i]);
            int percent = Math.round(current * 100);
            control(getString(R.string.launcher_label_percent, label, percent), new LevelBar(ink.folio, label, labels, settingIndex(values, percent),
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
        String mode = store.sleepMode();
        if ("off".equals(mode) || !hasFileAccess()) return;
        SleepSize size = SleepScreen.panel(this);
        if ("custom".equals(mode)) {
            String path = store.sleepSource();
            // Older versions retained only the exported picture.
            if (!path.isEmpty() && (!size.key().equals(store.sleepImageSize())
                    || !"custom".equals(store.sleepFileMode()) || !SleepScreen.file().isFile())) {
                writeSleepImage(new File(path), store.sleepFill(), false);
            }
            return;
        }
        List<Library.Book> r = reading();
        Library.Book b = r.isEmpty() ? null : r.get(0);
        String detail = b == null ? "" : b.progressLine(this);
        if (b != null) {
            double[] reader = store.readerProgress(b.path);
            if (reader != null && (long) reader[1] == b.lastRead) {
                detail = getString(R.string.launcher_sleep_chapter, detail, store.readerPosition(b.path)[0] + 1);
            }
            if (stats != null) detail = getString(R.string.launcher_sleep_today, detail, stats.today());
        }
        boolean real = store.realCovers();
        String sig = size.key() + "|" + (b == null ? "none" : b.key() + b.title + b.author
                + Covers.pending(this, b) + real + b.progress + detail);
        if (!force && sig.equals(sleepSignature) && SleepScreen.file().isFile()) return;
        sleepSignature = sig;
        final String caption = detail;
        io.execute(() -> {
            if (!"book".equals(store.sleepMode())) return;
            boolean ok = SleepScreen.writeBook(this, size, b, real, caption);
            if (ok) store.setSleepFileMode("book");
            ui.post(() -> {
                if (!ok && sig.equals(sleepSignature)) sleepSignature = "";
                if ("sleep".equals(sub)) render();
            });
        });
    }

    void writeSleepImage(File pic, boolean fill, boolean picked) {
        SleepSize size = SleepScreen.panel(this);
        io.execute(() -> {
            if (!"custom".equals(store.sleepMode())) return;
            if (!picked && (!pic.getAbsolutePath().equals(store.sleepSource()) || fill != store.sleepFill())) return;
            boolean ok = SleepScreen.writeImage(this, size, pic, fill);
            if (ok) store.rememberSleepImage(pic, fill, size);
            ui.post(() -> {
                if (picked) toast(getString(ok ? R.string.launcher_sleep_picture_updated : R.string.launcher_picture_read_error));
                if (ok && !size.key().equals(SleepScreen.panel(this).key())) updateSleepPicture(false);
                if ("sleep".equals(sub)) render();
            });
        });
    }

    interface FilePick { void on(File f); }

    /** Thumbnail grid of the pictures on the tablet. The selected one gets a dark frame. */
    void pictureGrid(LinearLayout body, String selected, FilePick pick) {
        body.addView(ink.heading(getString(R.string.launcher_choose_a_picture)), ink.m(-1, -2, 0, 24, 0, 0));
        List<File> pics = SleepScreen.pictures();
        if (pics.isEmpty()) {
            body.addView(note(getString(R.string.launcher_no_pictures_found), getString(R.string.launcher_empty_pictures_hint), null));
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
            boolean mono = ink.folio.displayClass == dev.inkhome.folio.core.DisplayClass.MONO_EINK;
            int paper = ink.PAPER;
            thumbs.execute(() -> {
                Bitmap bm = SleepScreen.thumb(pic, 180, 240, mono, paper);
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
        if (!wifi().connected) { if (manual) toast(getString(R.string.launcher_update_wifi_required)); return; }
        checkingUpdates = true;
        if (manual) toast(getString(R.string.launcher_checking_for_updates));
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
                    sheets.message(getString(R.string.launcher_update_available, found.version),
                            found.notes.isEmpty() ? getString(R.string.launcher_install_the_new_version) : found.notes.trim(),
                            getString(R.string.launcher_install), this::installUpdate);
                } else if (manual) {
                    toast(failed != null ? getString(R.string.launcher_update_check_error) : getString(R.string.launcher_ink_home_is_up_to_date));
                }
                if (screen == Screen.SETTINGS && (sub == null || SettingsScreen.ABOUT.equals(sub))) render();
            });
        });
    }

    void installUpdate() {
        Updater.Release r = update;
        if (r == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast(getString(R.string.launcher_update_install_permission));
            start(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        toast(getString(R.string.launcher_downloading, r.version));
        io.execute(() -> {
            try {
                File apk = Updater.download(this, r);
                Updater.install(this, apk);
            } catch (SecurityException e) {
                ui.post(() -> toast(getString(R.string.launcher_update_checksum_error)));
            } catch (Exception e) {
                Log.w(TAG, "Update failed", e);
                ui.post(() -> toast(getString(R.string.launcher_update_failed_try_again_later)));
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
        return scrollable(body, "list:" + screen + ":" + sub);
    }

    View scrollable(LinearLayout body, String key) {
        if (store.listScroll()) {
            ScrollView sv = ink.scroller(body);
            bindScrollKeys(sv);
            jumper = v -> sv.scrollTo(0, PagedFlow.topIn(v, body));
            return sv;
        }
        PagedFlow flow = new PagedFlow(ink.folio, body, pages.getOrDefault(key, 0),
                page -> pages.put(key, page), this::pageChanged);
        jumper = flow::jumpTo;
        return bindPageKeys(flow);
    }

    private View bindPageKeys(PageLayout layout) {
        pageNext = layout::next;
        pagePrev = layout::previous;
        Controls controls = store.controls();
        layout.setSwipes(store.swipePages(),
                controlHandler(controls.action(Controls.Input.SWIPE_LEFT)),
                controlHandler(controls.action(Controls.Input.SWIPE_RIGHT)),
                controls.overridden(Controls.Input.SWIPE_UP)
                        ? controlHandler(controls.action(Controls.Input.SWIPE_UP)) : pageNext,
                controls.overridden(Controls.Input.SWIPE_DOWN)
                        ? controlHandler(controls.action(Controls.Input.SWIPE_DOWN)) : pagePrev);
        return layout;
    }

    /** Page keys jump a screen at a time in scrolling lists. */
    void bindScrollKeys(ScrollView sv) {
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

    void cleanScreen() {
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

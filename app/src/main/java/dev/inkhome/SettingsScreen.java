package dev.inkhome;

import dev.inkhome.folio.widget.Stepper;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;

final class SettingsScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        if ("hidden".equals(host.sub) || "reading".equals(host.sub)) { new AppPickerScreen().build(host); return; }
        if ("sleep".equals(host.sub)) { new SleepSettingsScreen().build(host); return; }
        if ("wallpaper".equals(host.sub)) { new WallpaperScreen().build(host); return; }
        LinearLayout col = host.page();
        col.addView(host.header("Settings", null, null));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(20));

        host.group(body, "Appearance", g -> {
            String[] layouts = {"hero", "shelf", "minimal"};
            g.segments("Home layout", new String[]{"Cover", "Shelf", "Minimal"},
                    Arrays.asList(layouts).indexOf(host.store.homeLayout()), i -> { host.store.setHomeLayout(layouts[i]); host.render(); });
            g.scale("Text size", new int[]{90, 100, 115, 130}, host.store.textScale(), value -> {
                host.store.setTextScale(value / 100f);
                host.ink = new Ink(host, host.store.textScale(), host.store.uiScale());
                host.render();
            });
            g.scale("Interface size", new int[]{100, 115, 130, 145}, host.store.uiScale(), value -> {
                host.store.setUiScale(value / 100f);
                host.ink = new Ink(host, host.store.textScale(), host.store.uiScale());
                host.iconCache.clear();
                host.render();
            });
            g.segments("Status bar", new String[]{"Ink Home", "System"}, host.store.statusBar() ? 0 : 1, i -> {
                host.store.setStatusBar(i == 0);
                host.applySystemBar();
                host.render();
            });
            String[] clocks = {"system", "12", "24"};
            g.segments("Clock", new String[]{"Auto", "12-hour", "24-hour"}, Arrays.asList(clocks).indexOf(host.store.clock()),
                    i -> { host.store.setClock(clocks[i]); host.render(); });
        });

        host.group(body, "Display", g -> {
            g.segments("Long lists", new String[]{"Pages", "Scroll"}, host.store.listScroll() ? 1 : 0,
                    i -> { host.store.setListScroll(i == 1); host.render(); });
            int[] intervals = {0, 1, 4, 8, 16};
            String refreshLabel = "Full refresh in the reader";
            g.control(refreshLabel, new Stepper(host.ink.folio, refreshLabel,
                    new String[]{"Off", "1 page", "4 pages", "8 pages", "16 pages"},
                    MainActivity.settingIndex(intervals, host.store.readerRefreshEvery()),
                    i -> host.store.setReaderRefreshEvery(intervals[i])), false);
            g.toggle("App icons", host.store.appIcons(), value -> { host.store.setAppIcons(value); host.render(); });
            g.toggle("Show original book covers", host.store.realCovers(), value -> {
                host.store.setRealCovers(value);
                Covers.flush();
                if (value) host.extractCovers();
                host.updateSleepPicture(false);
                host.render();
            });
            String sleep = host.store.sleepMode().equals("book") ? "Current book" : host.store.sleepMode().equals("custom") ? "Your picture" : "Off";
            g.add("Wallpaper", host.store.wallpaper().isEmpty() ? "None" : new File(host.store.wallpaper()).getName(), () -> { host.sub = "wallpaper"; host.render(); });
            g.add("Sleep screen", sleep, () -> { host.sub = "sleep"; host.render(); });
        });

        host.group(body, "Reading", g -> {
            g.add("Open books with", host.readerName(host.store.bookReader().isEmpty() ? Store.BUILT_IN : host.store.bookReader()), () -> host.chooseReader(true));
            g.add("Open PDFs and comics with", host.readerName(host.store.pdfReader()), () -> host.chooseReader(false));
            int[] goals = {0, 10, 15, 20, 30, 45, 60, 90, 120};
            String[] labels = new String[goals.length];
            for (int i = 0; i < goals.length; i++) labels[i] = goals[i] == 0 ? "Off" : goals[i] + " min";
            g.control("Daily goal", new Stepper(host.ink.folio, "Daily goal", labels, MainActivity.settingIndex(goals, host.store.dailyGoal()), i -> {
                host.store.setDailyGoal(goals[i]);
                host.refreshReadingSettings();
            }), false);
            g.segments("Week starts on", new String[]{"Sunday", "Monday"}, host.store.weekStartsMonday() ? 1 : 0, i -> {
                host.store.setWeekStartsMonday(i == 1);
                host.refreshReadingSettings();
            });
            int n = host.store.readingApps().size();
            g.add("Apps that count as reading", String.valueOf(n), () -> { host.sub = "reading"; host.render(); });
        });

        host.group(body, "Controls", g -> {
            g.toggle("Clean screen after reading", host.store.cleanOnHome(), value -> { host.store.setCleanOnHome(value); host.render(); });
            g.toggle("Lock to portrait", host.store.portraitLocked(), locked -> {
                host.store.setPortraitLocked(locked);
                ActivityWindow.applyOrientation(host, host.store);
            });
            g.segments("Page buttons", new String[]{"Normal", "Reversed"}, host.store.pageKeysReversed() ? 1 : 0,
                    i -> { host.store.setPageKeysReversed(i == 1); host.render(); });
        });

        host.group(body, "Library", g -> {
            g.add("Look for new books", host.booksLoaded ? host.books.size() + " books" : "…", () -> { host.lastLoad = 0; host.lastFullScan = 0; host.refreshData(0); host.toast("Looking for books…"); });
            g.add("File access", host.hasFileAccess() ? "Allowed" : "Off", host::requestFileAccess);
            g.add("Reading time", Stats.hasAccess(host) ? "Allowed" : "Off", () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
            g.add("Notification count", Notifs.count() >= 0 ? "Allowed" : "Off", () -> host.start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        });

        host.group(body, "Updates", g -> {
            g.add(host.update != null ? "Install version " + host.update.version : "Check for updates", host.update != null ? "Ready" : "Version " + Updater.installedVersion(host),
                    () -> { if (host.update != null) host.installUpdate(); else host.checkUpdates(true); });
            g.toggle("Check automatically", host.store.autoUpdate(), value -> { host.store.setAutoUpdate(value); host.render(); });
        });

        host.group(body, "System", g -> {
            g.add("Hidden apps", String.valueOf(host.store.hidden().size()), () -> { host.sub = "hidden"; host.render(); });
            g.add("Default home app", host.isDefaultHome() ? "Ink Home" : "Choose", host::openHomeSettings);
            g.add("Onyx Launcher", "", () -> { MainActivity.App a = host.findApp(MainActivity.BOOX_HOME); if (a != null) host.launch(a); });
            g.add("Android settings", "", () -> host.start(new Intent(Settings.ACTION_SETTINGS)));
        });

        TextView about = host.ink.center(host.ink.faint("Ink Home " + Updater.installedVersion(host), 13));
        about.setPadding(0, host.ink.dp(8), 0, 0);
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
                    dev.inkhome.lab.InkLab.open(host, host.store.uiScale(), host.store.textScale(),
                            host.books.isEmpty() ? null : Covers.get(host, host.books.get(0), host.store.realCovers()));
                }
            });
        }
        body.addView(about, new LinearLayout.LayoutParams(-1, -2));

        col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

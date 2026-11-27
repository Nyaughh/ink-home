package dev.inkhome;

import dev.inkhome.folio.DisplayProbe;
import dev.inkhome.folio.widget.Stepper;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;

final class SettingsScreen implements Screen {
    static final String APPEARANCE = "Appearance", DISPLAY = "Display & refresh", READING = "Reading",
            LIBRARY = "Library", SHELL = "System shell", CONTROLS = "Controls", DEVICE = "Device",
            ABOUT = "Updates & about", SETUP = "Set up Ink Home", LICENCES = "Font licences", DOT = "Ink dot", BOOX = "Boox apps";

    static String parent(String sub) {
        switch (sub) {
            case DOT: case BOOX: case "hidden": case "sleep": return SHELL;
            case "reading": return READING;
            case "wallpaper": return APPEARANCE;
            case LICENCES: case SETUP: return ABOUT;
            default: return null;
        }
    }

    static void open(MainActivity host, String sub) {
        host.sub = sub;
        host.render();
    }

    @Override public void build(MainActivity host) {
        LinearLayout page = host.page();
        boolean expanded = host.ink.folio.windowClass() == dev.inkhome.folio.core.WindowClass.EXPANDED;
        if (!expanded) {
            pane(host, page, host.sub, false);
            return;
        }
        page.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout categories = host.ink.col();
        page.addView(categories, new LinearLayout.LayoutParams(0, -1, 2));
        pane(host, categories, null, true);
        page.addView(host.ink.hair(), host.ink.folio.hairline, -1);
        LinearLayout detail = host.ink.col();
        page.addView(detail, new LinearLayout.LayoutParams(0, -1, 3));
        pane(host, detail, host.sub == null ? APPEARANCE : host.sub, true);
    }

    private void pane(MainActivity host, LinearLayout col, String category, boolean expanded) {
        if ("hidden".equals(category) || "reading".equals(category)) { new AppPickerScreen().build(host, col); return; }
        if ("sleep".equals(category)) { new SleepSettingsScreen().build(host, col); return; }
        if ("wallpaper".equals(category)) { new WallpaperScreen().build(host, col); return; }
        col.addView(host.header(category == null ? "Settings" : SetupScreen.FROM_HOME.equals(category) ? SETUP : category, null,
                category == null || (expanded && parent(category) == null && !SetupScreen.FROM_HOME.equals(category)) ? null : host::onBackPressed));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(8));
        MainActivity.Rows g = host.new Rows(expanded || host.ink.folio.windowClass() == dev.inkhome.folio.core.WindowClass.COMPACT);
        if (category == null) {
            category(host, g, APPEARANCE, "Text " + Math.round(host.store.textScale() * 100) + "% · Interface " + Math.round(host.store.uiScale() * 100) + "%");
            category(host, g, DISPLAY, SetupScreen.displayName(host) + " · " + (host.store.listScroll() ? "Scroll" : "Pages"));
            category(host, g, READING, host.store.dailyGoal() == 0 ? "Daily goal off" : host.store.dailyGoal() + " minute daily goal");
            category(host, g, LIBRARY, host.hasFileAccess() ? (host.booksLoaded ? host.books.size() + " books" : "Looking for books…") : "Allow file access");
            category(host, g, SHELL, host.store.statusBar() ? "Ink Home status strip" : "System status strip");
            category(host, g, CONTROLS, host.store.pageKeysReversed() ? "Reversed page buttons" : "Normal page buttons");
            category(host, g, DEVICE, freeSpace(host));
            category(host, g, ABOUT, "Version " + Updater.installedVersion(host));
        } else switch (category) {
            case APPEARANCE: appearance(host, g); break;
            case DISPLAY: display(host, g); break;
            case READING: reading(host, g); break;
            case LIBRARY: library(host, g); break;
            case DOT: dot(host, g); break;
            case BOOX:
                for (MainActivity.App app : host.apps) if (MainActivity.booxShellApp(app)) {
                    g.toggle(host.displayLabel(app), !host.store.hidden().contains(app.key), shown -> {
                        host.store.setHidden(app.key, !shown);
                        host.render();
                    });
                }
                break;
            case SHELL: shell(host, g); break;
            case CONTROLS: controls(host, g); break;
            case DEVICE: device(host, g); break;
            case ABOUT: updates(host, g); break;
            case SetupScreen.FROM_HOME: case SETUP: SetupScreen.steps(host, g); break;
            case LICENCES: licences(host, body); break;
        }
        for (View row : g.views) {
            row.setBackground(host.ink.listRow(0));
            body.addView(row);
        }
        if (category == null || ABOUT.equals(category)) version(host, body);
        col.addView(host.scrollable(body, "list:SETTINGS:" + category), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void category(MainActivity host, MainActivity.Rows g, String name, String summary) {
        String selected = host.sub == null ? APPEARANCE : host.sub;
        String parent = parent(selected);
        boolean active = host.ink.folio.windowClass() == dev.inkhome.folio.core.WindowClass.EXPANDED
                && name.equals(parent == null ? selected : parent);
        g.add(active ? "●  " + name : name, summary, () -> open(host, name));
        if (active) g.views.get(g.views.size() - 1).setSelected(true);
    }

    private static String freeSpace(MainActivity host) {
        return android.text.format.Formatter.formatFileSize(host, host.getFilesDir().getUsableSpace()) + " free";
    }

    private void appearance(MainActivity host, MainActivity.Rows g) {
        String[] layouts = {"hero", "shelf", "minimal"};
        String[] names = {"Cover", "Shelf", "Minimal"};
        int layout = Math.max(0, Arrays.asList(layouts).indexOf(host.store.homeLayout()));
        g.add("Home layout", names[layout], () -> host.choose("Home layout", names, layout, i -> {
            host.store.setHomeLayout(layouts[i]);
            host.render();
        }));
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
        String[] clocks = {"system", "12", "24"};
        g.segments("Clock", new String[]{"Auto", "12-hour", "24-hour"}, Arrays.asList(clocks).indexOf(host.store.clock()),
                i -> { host.store.setClock(clocks[i]); host.render(); });
        boolean systemTheme = !host.store.hasThemeOverride();
        boolean eink = host.ink.folio.displayClass.isEink();
        g.segments("Theme", eink ? new String[]{"Paper", "Night"} : new String[]{"Paper", "Night", "Follow system"},
                !eink && systemTheme ? 2 : host.ink.folio.night ? 1 : 0, i -> {
                    host.store.setNight(i == 2 ? null : i == 1);
                    host.recreate();
                });
        if (host.ink.folio.displayClass != dev.inkhome.folio.core.DisplayClass.MONO_EINK) {
            String[] accents = {"Vermilion", "Cobalt", "Forest", "Ochre"};
            int accent = DisplayProbe.accentIndex(host);
            g.add("Accent", accents[accent], () -> host.choose("Accent", accents, accent, i -> {
                DisplayProbe.setAccentIndex(host, i);
                host.recreate();
            }));
        }
        g.add("Wallpaper", host.store.wallpaper().isEmpty() ? "None" : new File(host.store.wallpaper()).getName(), () -> open(host, "wallpaper"));
    }

    private void display(MainActivity host, MainActivity.Rows g) {
        g.add("Display type", SetupScreen.displayName(host), () -> SetupScreen.chooseDisplay(host));
        g.segments("Long lists", new String[]{"Pages", "Scroll"}, host.store.listScroll() ? 1 : 0,
                i -> { host.store.setListScroll(i == 1); host.render(); });
        if (!host.ink.folio.displayClass.isEink()) {
            g.segments("Motion", new String[]{"Off", "Subtle"}, host.store.prefersMotion() ? 1 : 0,
                    i -> host.store.setMotion(i == 1));
        }
        int[] intervals = {0, 1, 4, 8, 16};
        if (host.ink.folio.displayClass.isEink()) {
            g.add("Strong clean now", "Flashes the screen", host::strongClean);
        }
        String refreshLabel = "Full refresh in the reader";
        g.control(refreshLabel, new Stepper(host.ink.folio, refreshLabel,
                new String[]{"Off", "1 page", "4 pages", "8 pages", "16 pages"},
                MainActivity.settingIndex(intervals, host.store.readerRefreshEvery()),
                i -> host.store.setReaderRefreshEvery(intervals[i])), false);
        g.toggle("App icons", host.store.appIcons(), value -> { host.store.setAppIcons(value); host.render(); });
        if (DisplayProbe.capabilities(host).regal && host.ink.folio.displayClass.isEink()) {
            g.toggle("Smooth text updates", DisplayProbe.smoothText(host), enabled -> {
                DisplayProbe.setSmoothText(host, enabled);
                host.recreate();
            });
        }
        g.toggle("Book covers", host.store.realCovers(), value -> {
            host.store.setRealCovers(value);
            Covers.flush();
            if (value) host.extractCovers();
            host.updateSleepPicture(false);
            host.render();
        });
        g.toggle("Clean screen after reading", host.store.cleanOnHome(), host.store::setCleanOnHome);
    }

    private void reading(MainActivity host, MainActivity.Rows g) {
        g.add("Open books with", host.readerName(host.store.bookReader().isEmpty() ? Store.BUILT_IN : host.store.bookReader()), () -> host.chooseReader(true));
        g.add("Open PDFs and comics with", host.readerName(host.store.pdfReader()), () -> host.chooseReader(false));
        int[] goals = {0, 10, 15, 20, 30, 45, 60, 90, 120};
        String[] labels = new String[goals.length];
        for (int i = 0; i < goals.length; i++) labels[i] = goals[i] == 0 ? "Off" : goals[i] + " min";
        int goal = MainActivity.settingIndex(goals, host.store.dailyGoal());
        g.add("Daily goal", labels[goal], () -> host.choose("Daily goal", labels, goal, i -> {
            host.store.setDailyGoal(goals[i]);
            host.refreshReadingSettings();
        }));
        g.segments("Week starts on", new String[]{"Sunday", "Monday"}, host.store.weekStartsMonday() ? 1 : 0, i -> {
            host.store.setWeekStartsMonday(i == 1);
            host.refreshReadingSettings();
        });
        int n = host.store.readingApps().size();
        g.add("Apps that count as reading", String.valueOf(n), () -> { host.sub = "reading"; host.render(); });
        g.add("Reading time access", Stats.hasAccess(host) ? "Allowed" : "Allow", () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
    }

    private void library(MainActivity host, MainActivity.Rows g) {
        g.add("Look for new books", host.booksLoaded ? host.books.size() + " books" : "…", () -> { host.lastLoad = 0; host.lastFullScan = 0; host.refreshData(0); host.toast("Looking for books…"); });
        g.add("File access", host.hasFileAccess() ? "Allowed" : "Off", host::requestFileAccess);
    }

    private void shell(MainActivity host, MainActivity.Rows g) {
        boolean shellOn = ShellService.connected();
        g.add("Ink shell", shellOn ? "On · Turn off" : "Off · Set up", () -> host.shellAccessSheet(shellOn));
        g.segments("Status strip", new String[]{"Ink Home", "System"}, host.store.statusBar() ? 0 : 1, i -> {
            host.store.setStatusBar(i == 0);
            host.applySystemBar();
            host.render();
        });
        String mode = host.store.sleepMode();
        g.add("Sleep screen", mode.equals("book") ? "Current book" : mode.equals("custom") ? "Your picture" : "Off", () -> open(host, "sleep"));
        g.add("Notification access", SetupScreen.notificationsAllowed(host) ? "Allowed" : "Allow", () -> host.start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        g.add("Default home app", host.isDefaultHome() ? "Ink Home" : "Choose", host::openHomeSettings);
        g.add("Hidden apps", String.valueOf(host.store.hidden().size()), () -> open(host, "hidden"));
        if (host.apps.stream().anyMatch(MainActivity::booxShellApp)) category(host, g, BOOX, "Choose which apps to show");
        if (DotService.available(host)) category(host, g, DOT, dotOn(host) ? "On" : "Off");
        if (Notifs.count() >= 0) {
            g.toggle("Quiet", host.store.quiet(), enabled -> { host.changeQuiet(enabled); host.render(); });
        } else g.add("Quiet", "Requires notification access", () -> host.changeQuiet(true));
    }

    static boolean dotOn(MainActivity host) {
        return DotService.enabled(host) && DotService.permitted(host);
    }

    private void dot(MainActivity host, MainActivity.Rows g) {
        g.toggle("Show Ink dot", dotOn(host), enabled -> {
            DotService.setEnabled(host, enabled);
            host.render();
        });
        int[] sizes = {36, 42, 52};
        g.segments("Size", new String[]{"Small", "Medium", "Large"},
                MainActivity.settingIndex(sizes, DotService.size(host)), i -> {
                    DotService.setSize(host, sizes[i]);
                    host.render();
                });
        g.toggle("Tuck against the edge", DotService.tucked(host), tucked -> {
            DotService.setTucked(host, tucked);
            host.render();
        });
        if (ShellService.connected()) g.add("Dot in each app", "Show, tuck or hide", () -> {
            String[] labels = host.apps.stream().map(host::displayLabel).toArray(String[]::new);
            String[] packages = host.apps.stream().map(app -> app.pkg).toArray(String[]::new);
            host.choose("Dot in each app", labels, -1, i -> host.choose(labels[i],
                    new String[]{"Show", "Tuck", "Hide"}, ShellService.dotPolicy(host, packages[i]),
                    mode -> ShellService.setDotPolicy(host, packages[i], mode)));
        });
        if (DotService.ballAvailable(host)) g.add("Boox Navigation Ball", "Its accessibility service is enabled", () -> {
            if (!DotService.openBallSettings(host)) host.toast("Navigation Ball settings are unavailable");
        });
    }

    private void controls(MainActivity host, MainActivity.Rows g) {
        g.toggle("Lock to portrait", host.store.portraitLocked(), locked -> {
            host.store.setPortraitLocked(locked);
            ActivityWindow.applyOrientation(host, host.store);
        });
        g.segments("Page buttons", new String[]{"Normal", "Reversed"}, host.store.pageKeysReversed() ? 1 : 0,
                i -> { host.store.setPageKeysReversed(i == 1); host.render(); });
        g.toggle("Swipe to turn pages", host.store.swipePages(), enabled -> {
            host.store.setSwipePages(enabled);
            host.render();
        });
    }

    private void device(MainActivity host, MainActivity.Rows g) {
        android.content.pm.PackageManager pm = host.getPackageManager();
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI))
            link(host, g, "Wi-Fi", "", Settings.ACTION_WIFI_SETTINGS);
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH))
            link(host, g, "Bluetooth", "", Settings.ACTION_BLUETOOTH_SETTINGS);
        if (DisplayProbe.capabilities(host).speaker)
            link(host, g, "Sound", "", Settings.ACTION_SOUND_SETTINGS);
        link(host, g, "Date and time", "", Settings.ACTION_DATE_SETTINGS);
        link(host, g, "Storage", freeSpace(host), Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        link(host, g, "Android apps", "", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        link(host, g, "All Android settings", "", Settings.ACTION_SETTINGS);
    }

    private void link(MainActivity host, MainActivity.Rows g, String title, String value, String action) {
        g.add(title, value, () -> {
            if (!host.start(new Intent(action).setPackage("com.android.settings"))) host.toast(title + " settings are unavailable");
        });
    }

    private void updates(MainActivity host, MainActivity.Rows g) {
        g.add(host.update != null ? "Install version " + host.update.version : "Check for updates", host.update != null ? "Ready" : "Version " + Updater.installedVersion(host),
                () -> { if (host.update != null) host.installUpdate(); else host.checkUpdates(true); });
        g.toggle("Check automatically", host.store.autoUpdate(), value -> { host.store.setAutoUpdate(value); host.render(); });
        g.add(SETUP, "", () -> open(host, SETUP));
        g.add(LICENCES, "Source Sans 3 · Literata", () -> open(host, LICENCES));
    }

    private void licences(MainActivity host, LinearLayout body) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                host.getAssets().open("fonts/OFL.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder paragraph = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) paragraph.append(line).append(' ');
                else if (paragraph.length() > 0) {
                    body.addView(MainActivity.item(host.ink.muted(paragraph.toString().trim(), 14)));
                    paragraph.setLength(0);
                }
            }
            if (paragraph.length() > 0) body.addView(MainActivity.item(host.ink.muted(paragraph.toString().trim(), 14)));
        } catch (java.io.IOException e) {
            body.addView(host.ink.body("The font licence could not be opened."));
        }
    }

    private void version(MainActivity host, LinearLayout body) {
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
                            host.books.isEmpty() ? null : Covers.get(host, host.books.get(0), host.store.realCovers()),
                            () -> SetupScreen.chooseDisplay(host));
                }
            });
        }
        body.addView(about, new LinearLayout.LayoutParams(-1, -2));
    }
}

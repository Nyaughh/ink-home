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
    // Saved navigation state uses these route keys across locale changes.
    static final String HOME = "Home", APPEARANCE = "Appearance", DISPLAY = "Display & refresh", READING = "Reading",
            LIBRARY = "Library", SHELL = "System shell", CONTROLS = "Controls", DEVICE = "Device",
            ABOUT = "Updates & about", SETUP = "Set up Ink Home", LICENCES = "Font licences", DOT = "Ink dot", BOOX = "Boox apps";

    private static String categoryTitle(MainActivity host, String category) {
        switch (category) {
            case HOME: return host.getString(R.string.launcher_home);
            case APPEARANCE: return host.getString(R.string.settings_ui_appearance);
            case DISPLAY: return host.getString(R.string.settings_ui_display_refresh);
            case READING: return host.getString(R.string.settings_ui_reading);
            case LIBRARY: return host.getString(R.string.settings_ui_library);
            case SHELL: return host.getString(R.string.settings_ui_system_shell);
            case CONTROLS: return host.getString(R.string.settings_ui_controls);
            case DEVICE: return host.getString(R.string.settings_ui_device);
            case ABOUT: return host.getString(R.string.settings_ui_updates_about);
            case SetupScreen.FROM_HOME:
            case SETUP: return host.getString(R.string.settings_ui_set_up_ink_home);
            case LICENCES: return host.getString(R.string.settings_ui_font_licences);
            case DOT: return host.getString(R.string.settings_ui_ink_dot);
            case BOOX: return host.getString(R.string.settings_ui_boox_apps);
            default: return category;
        }
    }

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
        col.addView(host.header(category == null ? host.getString(R.string.settings_ui_settings) : categoryTitle(host, category), null,
                category == null || (expanded && parent(category) == null && !SetupScreen.FROM_HOME.equals(category))
                        ? null : host::onBackPressed));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(8));
        MainActivity.Rows g = host.new Rows(expanded || host.ink.folio.windowClass() == dev.inkhome.folio.core.WindowClass.COMPACT);
        if (category == null) {
            category(host, g, APPEARANCE,
                    host.getString(R.string.settings_ui_appearance_summary, Math.round(host.store.textScale() * 100),
                    Math.round(host.store.uiScale() * 100)));
            category(host, g, HOME, host.getString(R.string.home_settings_summary));
            category(host, g, DISPLAY, host.getString(R.string.settings_ui_display_summary, SetupScreen.displayName(host),
                    host.getString(host.store.listScroll() ? R.string.settings_ui_scroll : R.string.settings_ui_pages)));
            category(host, g, READING, host.store.dailyGoal() == 0 ? host.getString(R.string.settings_ui_daily_goal_off)
                    : host.getString(R.string.settings_ui_goal_summary, host.store.dailyGoal()));
            category(host, g, LIBRARY, host.hasFileAccess()
                    ? (host.booksLoaded ? bookCount(host) : host.getString(R.string.settings_ui_looking_for_books))
                    : host.getString(R.string.settings_ui_allow_file_access));
            category(host, g, SHELL, host.getString(host.store.statusBar()
                    ? R.string.settings_ui_ink_home_status_strip : R.string.settings_ui_system_status_strip));
            category(host, g, CONTROLS, host.getString(R.string.controls_summary));
            category(host, g, DEVICE, freeSpace(host));
            category(host, g, ABOUT, host.getString(R.string.settings_ui_version, Updater.installedVersion(host)));
        } else switch (category) {
            case HOME: new HomeSettings(host).rows(g); break;
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
        String title = categoryTitle(host, name);
        g.add(active ? host.getString(R.string.settings_ui_selected_category, title) : title, summary, () -> open(host, name));
        if (active) g.views.get(g.views.size() - 1).setSelected(true);
    }

    private static String bookCount(MainActivity host) {
        int count = host.books.size();
        return host.getResources().getQuantityString(R.plurals.settings_ui_book_count, count, count);
    }

    private static String freeSpace(MainActivity host) {
        return host.getString(R.string.settings_ui_space_free, android.text.format.Formatter.formatFileSize(host, host.getFilesDir().getUsableSpace()));
    }

    private void appearance(MainActivity host, MainActivity.Rows g) {
        g.scale(host.getString(R.string.settings_ui_text_size), new int[]{90, 100, 115, 130}, host.store.textScale(), value -> {
            host.store.setTextScale(value / 100f);
            host.ink = new Ink(host, host.store.textScale(), host.store.uiScale());
            host.render();
        });
        g.scale(host.getString(R.string.settings_ui_interface_size), new int[]{100, 115, 130, 145}, host.store.uiScale(), value -> {
            host.store.setUiScale(value / 100f);
            host.ink = new Ink(host, host.store.textScale(), host.store.uiScale());
            host.iconCache.clear();
            host.render();
        });
        String[] clocks = {"system", "12", "24"};
        g.segments(host.getString(R.string.settings_ui_clock), host.getResources().getStringArray(R.array.settings_ui_clock_modes), Arrays.asList(clocks).indexOf(host.store.clock()),
                i -> { host.store.setClock(clocks[i]); host.render(); });
        boolean systemTheme = !host.store.hasThemeOverride();
        boolean eink = host.ink.folio.displayClass.isEink();
        g.segments(host.getString(R.string.settings_ui_theme), host.getResources().getStringArray(
                        eink ? R.array.settings_ui_eink_themes : R.array.settings_ui_system_themes),
                !eink && systemTheme ? 2 : host.ink.folio.night ? 1 : 0, i -> {
                    host.store.setNight(i == 2 ? null : i == 1);
                    host.recreate();
                });
        if (host.ink.folio.displayClass != dev.inkhome.folio.core.DisplayClass.MONO_EINK) {
            String[] accents = host.getResources().getStringArray(R.array.settings_ui_accents);
            int accent = DisplayProbe.accentIndex(host);
            g.add(host.getString(R.string.settings_ui_accent), accents[accent],
                    () -> host.choose(host.getString(R.string.settings_ui_accent), accents, accent, i -> {
                DisplayProbe.setAccentIndex(host, i);
                host.recreate();
            }));
        }
        g.add(host.getString(R.string.settings_ui_wallpaper), host.store.wallpaper().isEmpty()
                ? host.getString(R.string.settings_ui_none) : new File(host.store.wallpaper()).getName(), () -> open(host, "wallpaper"));
    }

    private void display(MainActivity host, MainActivity.Rows g) {
        g.add(host.getString(R.string.settings_ui_display_type), SetupScreen.displayName(host), () -> SetupScreen.chooseDisplay(host));
        g.segments(host.getString(R.string.settings_ui_long_lists), host.getResources().getStringArray(R.array.settings_ui_list_modes), host.store.listScroll() ? 1 : 0,
                i -> { host.store.setListScroll(i == 1); host.render(); });
        if (!host.ink.folio.displayClass.isEink()) {
            g.segments(host.getString(R.string.settings_ui_motion), host.getResources().getStringArray(R.array.settings_ui_motion_modes), host.store.prefersMotion() ? 1 : 0,
                    i -> host.store.setMotion(i == 1));
        }
        int[] intervals = {0, 1, 4, 8, 16};
        if (host.ink.folio.displayClass.isEink()) {
            g.add(host.getString(R.string.settings_ui_strong_clean_now),
                    host.getString(R.string.settings_ui_flashes_the_screen), host::cleanScreen);
        }
        String refreshLabel = host.getString(R.string.settings_ui_full_refresh_in_the_reader);
        g.control(refreshLabel, new Stepper(host.ink.folio, refreshLabel,
                host.getResources().getStringArray(R.array.settings_ui_refresh_intervals),
                MainActivity.settingIndex(intervals, host.store.readerRefreshEvery()),
                i -> host.store.setReaderRefreshEvery(intervals[i])), false);
        g.toggle(host.getString(R.string.settings_ui_app_icons), host.store.appIcons(), value -> { host.store.setAppIcons(value); host.render(); });
        if (DisplayProbe.capabilities(host).regal && host.ink.folio.displayClass.isEink()) {
            g.toggle(host.getString(R.string.settings_ui_smooth_text_updates), DisplayProbe.smoothText(host), enabled -> {
                DisplayProbe.setSmoothText(host, enabled);
                host.recreate();
            });
        }
        g.toggle(host.getString(R.string.settings_ui_book_covers), host.store.realCovers(), value -> {
            host.store.setRealCovers(value);
            Covers.flush();
            if (value) host.extractCovers();
            host.updateSleepPicture(false);
            host.render();
        });
        g.toggle(host.getString(R.string.settings_ui_clean_screen_after_reading), host.store.cleanOnHome(), host.store::setCleanOnHome);
    }

    private void reading(MainActivity host, MainActivity.Rows g) {
        g.add(host.getString(R.string.settings_ui_open_books_with), host.readerName(host.store.bookReader().isEmpty()
                ? Store.BUILT_IN : host.store.bookReader()), () -> host.chooseReader(true));
        g.add(host.getString(R.string.settings_ui_open_pdfs_and_comics_with), host.readerName(host.store.pdfReader()),
                () -> host.chooseReader(false));
        int[] goals = {0, 10, 15, 20, 30, 45, 60, 90, 120};
        String[] labels = new String[goals.length];
        for (int i = 0; i < goals.length; i++) labels[i] = goals[i] == 0 ? host.getString(R.string.settings_ui_off)
                : host.getString(R.string.settings_ui_minutes, goals[i]);
        int goal = MainActivity.settingIndex(goals, host.store.dailyGoal());
        g.add(host.getString(R.string.settings_ui_daily_goal), labels[goal],
                () -> host.choose(host.getString(R.string.settings_ui_daily_goal), labels, goal, i -> {
            host.store.setDailyGoal(goals[i]);
            host.refreshReadingSettings();
        }));
        g.segments(host.getString(R.string.settings_ui_week_starts_on), host.getResources().getStringArray(R.array.settings_ui_weekdays), host.store.weekStartsMonday() ? 1 : 0, i -> {
            host.store.setWeekStartsMonday(i == 1);
            host.refreshReadingSettings();
        });
        int n = host.store.readingApps().size();
        g.add(host.getString(R.string.settings_ui_apps_that_count_as_reading), String.valueOf(n),
                () -> { host.sub = "reading"; host.render(); });
        g.add(host.getString(R.string.settings_ui_reading_time_access),
                host.getString(Stats.hasAccess(host)
                ? R.string.settings_ui_allowed
                : R.string.settings_ui_allow), () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
    }

    private void library(MainActivity host, MainActivity.Rows g) {
        g.add(host.getString(R.string.settings_ui_look_for_new_books), host.booksLoaded ? bookCount(host)
                : host.getString(R.string.settings_ui_searching), () -> { host.lastLoad = 0; host.lastFullScan = 0; host.refreshData(0); host.toast(host.getString(R.string.settings_ui_looking_for_books)); });
        g.add(host.getString(R.string.settings_ui_file_access),
                host.getString(host.hasFileAccess() ? R.string.settings_ui_allowed : R.string.settings_ui_off), host::requestFileAccess);
    }

    private void shell(MainActivity host, MainActivity.Rows g) {
        boolean shellOn = ShellService.connected();
        g.add(host.getString(R.string.settings_ui_ink_shell),
                host.getString(shellOn ? R.string.settings_ui_on_turn_off
                : R.string.settings_ui_off_set_up), () -> host.shellAccessSheet(shellOn));
        g.segments(host.getString(R.string.settings_ui_status_strip), host.getResources().getStringArray(R.array.settings_ui_status_strips), host.store.statusBar() ? 0 : 1, i -> {
            host.store.setStatusBar(i == 0);
            host.applySystemBar();
            host.render();
        });
        String mode = host.store.sleepMode();
        g.add(host.getString(R.string.settings_ui_sleep_screen), mode.equals("book")
                ? host.getString(R.string.settings_ui_current_book)
                : mode.equals("custom") ? host.getString(R.string.settings_ui_your_picture) : host.getString(R.string.settings_ui_off), () -> open(host, "sleep"));
        g.add(host.getString(R.string.settings_ui_notification_access),
                host.getString(SetupScreen.notificationsAllowed(host)
                ? R.string.settings_ui_allowed
                : R.string.settings_ui_allow), () -> host.start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        g.add(host.getString(R.string.settings_ui_default_home_app),
                host.getString(host.isDefaultHome() ? R.string.settings_ui_ink_home : R.string.settings_ui_choose), host::openHomeSettings);
        g.add(host.getString(R.string.settings_ui_hidden_apps), String.valueOf(host.store.hidden().size()), () -> open(host, "hidden"));
        if (host.apps.stream().anyMatch(MainActivity::booxShellApp)) category(host, g, BOOX,
                host.getString(R.string.settings_ui_choose_which_apps_to_show));
        if (DotService.available(host)) category(host, g, DOT, host.getString(dotOn(host) ? R.string.settings_ui_dot_gestures : R.string.settings_ui_off));
        if (Notifs.count() >= 0) {
            g.toggle(host.getString(R.string.settings_ui_quiet), host.store.quiet(), enabled -> { host.changeQuiet(enabled); host.render(); });
        } else g.add(host.getString(R.string.settings_ui_quiet), host.getString(R.string.settings_ui_requires_notification_access),
                () -> host.changeQuiet(true));
    }

    static boolean dotOn(MainActivity host) {
        return DotService.enabled(host) && DotService.permitted(host);
    }

    private void dot(MainActivity host, MainActivity.Rows g) {
        g.toggle(host.getString(R.string.settings_ui_show_ink_dot), dotOn(host), enabled -> {
            DotService.setEnabled(host, enabled);
            host.render();
        });
        int[] sizes = {36, 42, 52};
        g.segments(host.getString(R.string.settings_ui_size), host.getResources().getStringArray(R.array.settings_ui_dot_sizes),
                MainActivity.settingIndex(sizes, DotService.size(host)), i -> {
                    DotService.setSize(host, sizes[i]);
                    host.render();
                });
        g.toggle(host.getString(R.string.settings_ui_tuck_against_the_edge), DotService.tucked(host), tucked -> {
            DotService.setTucked(host, tucked);
            host.render();
        });
        if (ShellService.connected()) g.add(host.getString(R.string.settings_ui_dot_in_each_app),
                host.getString(R.string.settings_ui_show_tuck_or_hide), () -> {
            String[] labels = host.apps.stream().map(host::displayLabel).toArray(String[]::new);
            String[] packages = host.apps.stream().map(app -> app.pkg).toArray(String[]::new);
            host.choose(host.getString(R.string.settings_ui_dot_in_each_app), labels, -1, i -> host.choose(labels[i],
                    host.getResources().getStringArray(R.array.settings_ui_dot_modes), ShellService.dotPolicy(host, packages[i]),
                    mode -> ShellService.setDotPolicy(host, packages[i], mode)));
        });
        if (DotService.ballAvailable(host)) g.add(host.getString(R.string.settings_ui_boox_navigation_ball),
                host.getString(R.string.settings_ui_its_accessibility_service_is_enabled), () -> {
            if (!DotService.openBallSettings(host)) host.toast(host.getString(R.string.settings_ui_navigation_ball_settings_are_unavailable));
        });
    }

    private void controls(MainActivity host, MainActivity.Rows g) {
        new ControlBindingsScreen(host).rows(g);
        g.toggle(host.getString(R.string.settings_ui_lock_to_portrait), host.store.portraitLocked(), locked -> {
            host.store.setPortraitLocked(locked);
            ActivityWindow.applyOrientation(host, host.store);
        });
        g.segments(host.getString(R.string.settings_ui_page_buttons), host.getResources().getStringArray(R.array.settings_ui_page_button_modes), host.store.pageKeysReversed() ? 1 : 0,
                i -> { host.store.setPageKeysReversed(i == 1); host.render(); });
        g.toggle(host.getString(R.string.settings_ui_swipe_to_turn_pages), host.store.swipePages(), enabled -> {
            host.store.setSwipePages(enabled);
            host.render();
        });
    }

    private void device(MainActivity host, MainActivity.Rows g) {
        android.content.pm.PackageManager pm = host.getPackageManager();
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI))
            link(host, g, host.getString(R.string.settings_ui_wi_fi), "", Settings.ACTION_WIFI_SETTINGS);
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH))
            link(host, g, host.getString(R.string.settings_ui_bluetooth), "", Settings.ACTION_BLUETOOTH_SETTINGS);
        if (DisplayProbe.capabilities(host).mediaVolume)
            link(host, g, host.getString(R.string.settings_ui_sound), "", Settings.ACTION_SOUND_SETTINGS);
        link(host, g, host.getString(R.string.settings_ui_date_and_time), "", Settings.ACTION_DATE_SETTINGS);
        link(host, g, host.getString(R.string.settings_ui_storage), freeSpace(host), Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        link(host, g, host.getString(R.string.settings_ui_android_apps), "", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        link(host, g, host.getString(R.string.settings_ui_all_android_settings), "", Settings.ACTION_SETTINGS);
    }

    private void link(MainActivity host, MainActivity.Rows g, String title, String value, String action) {
        g.add(title, value, () -> {
            if (!host.start(new Intent(action).setPackage("com.android.settings"))) host.toast(host.getString(R.string.settings_ui_settings_unavailable, title));
        });
    }

    private void updates(MainActivity host, MainActivity.Rows g) {
        String title = host.update != null ? host.getString(R.string.settings_ui_install_version, host.update.version)
                : host.getString(R.string.settings_ui_check_for_updates);
        String status = host.update != null ? host.getString(R.string.settings_ui_ready)
                : host.getString(R.string.settings_ui_version, Updater.installedVersion(host));
        g.add(title, status,
                () -> { if (host.update != null) host.installUpdate(); else host.checkUpdates(true); });
        g.toggle(host.getString(R.string.settings_ui_check_automatically), host.store.autoUpdate(), value -> { host.store.setAutoUpdate(value); host.render(); });
        g.add(categoryTitle(host, SETUP), "", () -> open(host, SETUP));
        g.add(categoryTitle(host, LICENCES), host.getString(R.string.settings_ui_source_sans_3_literata), () -> open(host, LICENCES));
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
            body.addView(host.ink.body(host.getString(R.string.settings_ui_the_font_licence_could_not_be_opened)));
        }
    }

    private void version(MainActivity host, LinearLayout body) {
        TextView about = host.ink.center(host.ink.faint(host.getString(R.string.settings_ui_product_version, Updater.installedVersion(host)), 13));
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

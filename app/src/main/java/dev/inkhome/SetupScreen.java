package dev.inkhome;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;
import java.util.Arrays;

/** First-run permissions are checked against Android again whenever the launcher resumes. */
final class SetupScreen {
    static final String FROM_HOME = "setup-from-home";
    private static final String[] PROFILES = {"", "MONO_EINK", "COLOR_EINK", "EMISSIVE"};

    static boolean notificationsAllowed(MainActivity host) {
        NotificationManager manager = host.getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(host, Notifs.class));
    }

    static String displayName(MainActivity host) {
        return displayName(host, host.ink.folio.displayClass);
    }

    private static String displayName(MainActivity host, dev.inkhome.folio.core.DisplayClass display) {
        switch (display) {
            case COLOR_EINK: return host.getString(R.string.display_colour);
            case EMISSIVE: return host.getString(R.string.display_emissive);
            default: return host.getString(R.string.display_mono);
        }
    }

    static void chooseDisplay(MainActivity host) {
        String[] choices = host.getResources().getStringArray(R.array.display_choices);
        choices[0] = host.getString(R.string.display_detected, choices[0], displayName(host, dev.inkhome.folio.DisplayProbe.hardwareClass()));
        host.choose(host.getString(R.string.ui_display_type), choices, Math.max(0, Arrays.asList(PROFILES).indexOf(host.store.displayOverride())), i -> {
            host.store.confirmDisplay(PROFILES[i]);
            host.recreate();
        });
    }

    static boolean addHomeCallout(MainActivity host, LinearLayout home) {
        if (host.store.setupDismissed()) return false;
        int remaining = (host.hasFileAccess() ? 0 : 1) + (Stats.hasAccess(host) ? 0 : 1)
                + (notificationsAllowed(host) ? 0 : 1) + (host.isDefaultHome() ? 0 : 1)
                + (host.store.displayConfirmed() ? 0 : 1);
        if (remaining == 0) return false;
        android.widget.TextView setup = host.ink.oneLine(host.ink.medium(host.getResources().getQuantityString(
                R.plurals.setup_remaining, remaining, host.getString(R.string.settings_ui_set_up_ink_home), remaining), 14));
        setup.setMinimumHeight(host.ink.dp(44));
        setup.setGravity(android.view.Gravity.CENTER_VERTICAL);
        setup.setPadding(host.ink.dp(12), 0, host.ink.dp(12), 0);
        setup.setBackground(host.ink.stroke(host.ink.PAPER, 0, host.ink.LINE));
        setup.setOnClickListener(v -> host.openHomeSetup());
        home.addView(setup, host.ink.m(-1, -2, 0, 0, 0, 8));
        return true;
    }

    static void steps(MainActivity host, MainActivity.Rows rows) {
        rows.add(host.getString(R.string.ui_show_your_books), host.hasFileAccess() ? host.getString(R.string.ui_file_access_allowed) : host.getString(R.string.ui_allow_ink_home_to_read_book_files), host::requestFileAccess);
        rows.add(host.getString(R.string.ui_count_reading_time), Stats.hasAccess(host) ? host.getString(R.string.ui_usage_access_allowed) : host.getString(R.string.ui_allow_access_to_reading_app_usage),
                () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        rows.add(host.getString(R.string.ui_show_notifications), notificationsAllowed(host) ? host.getString(R.string.ui_notification_access_allowed) : host.getString(R.string.ui_allow_notifications_in_the_control_panel),
                () -> host.start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        rows.add(host.getString(R.string.ui_make_ink_home_the_home_screen), host.isDefaultHome() ? host.getString(R.string.ui_ink_home_is_your_home_screen) : host.getString(R.string.ui_choose_ink_home_as_the_default_home),
                host::openHomeSettings);
        rows.add(host.getString(R.string.ui_confirm_your_display), host.getString(host.store.displayConfirmed()
                ? R.string.display_confirmed : R.string.display_needs_confirmation, displayName(host)), () -> chooseDisplay(host));
        if (DotService.available(host)) rows.add(host.getString(R.string.ui_ink_dot_optional), SettingsScreen.dotOn(host) ? host.getString(R.string.ui_on) : host.getString(R.string.ui_floating_screen_refresh_control),
                () -> SettingsScreen.open(host, SettingsScreen.DOT));
        if (!host.store.setupDismissed()) rows.add(host.getString(R.string.ui_dismiss_setup_on_home), host.getString(R.string.ui_reopen_this_checklist_in_updates_about), () -> {
            host.store.dismissSetup();
            host.onBackPressed();
        });
    }
}

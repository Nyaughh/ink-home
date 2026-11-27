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
    private static final String[] LABELS = {"Automatic", "Mono e-ink", "Colour e-ink", "LCD or OLED"};

    static boolean notificationsAllowed(MainActivity host) {
        NotificationManager manager = host.getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(host, Notifs.class));
    }

    static String displayName(MainActivity host) {
        switch (host.ink.folio.displayClass) {
            case COLOR_EINK: return LABELS[2];
            case EMISSIVE: return LABELS[3];
            default: return LABELS[1];
        }
    }

    static void chooseDisplay(MainActivity host) {
        host.choose("Display type", LABELS, Math.max(0, Arrays.asList(PROFILES).indexOf(host.store.displayOverride())), i -> {
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
        android.widget.TextView setup = host.ink.oneLine(host.ink.medium(SettingsScreen.SETUP + " · "
                + remaining + (remaining == 1 ? " step remaining" : " steps remaining"), 14));
        setup.setMinimumHeight(host.ink.dp(44));
        setup.setGravity(android.view.Gravity.CENTER_VERTICAL);
        setup.setPadding(host.ink.dp(12), 0, host.ink.dp(12), 0);
        setup.setBackground(host.ink.stroke(host.ink.PAPER, 0, host.ink.LINE));
        setup.setOnClickListener(v -> host.openHomeSetup());
        home.addView(setup, host.ink.m(-1, -2, 0, 0, 0, 8));
        return true;
    }

    static void steps(MainActivity host, MainActivity.Rows rows) {
        rows.add("Show your books", host.hasFileAccess() ? "File access allowed" : "Allow Ink Home to read book files.", host::requestFileAccess);
        rows.add("Count reading time", Stats.hasAccess(host) ? "Usage access allowed" : "Allow access to reading app usage.",
                () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        rows.add("Show notifications", notificationsAllowed(host) ? "Notification access allowed" : "Allow notifications in the control panel.",
                () -> host.start(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        rows.add("Make Ink Home the home screen", host.isDefaultHome() ? "Ink Home is your home screen" : "Choose Ink Home as the default home app.",
                host::openHomeSettings);
        rows.add("Confirm your display", displayName(host) + (host.store.displayConfirmed() ? " · Confirmed" : " · Confirm or change"), () -> chooseDisplay(host));
        if (!host.store.setupDismissed()) rows.add("Dismiss setup on Home", "Reopen this checklist in Updates & about.", () -> {
            host.store.dismissSetup();
            host.onBackPressed();
        });
    }
}

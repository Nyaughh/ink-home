package dev.inkhome;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

/** Offers each missing grant once. Android remains the source of truth for access. */
final class FirstRunAccess {
    private static final int FILES = 0, USAGE = 1, LISTENER = 2, HOME = 3, WIFI_NAME = 4, DONE = 5;
    private static final String CURSOR = "automaticAccessStep";
    private final MainActivity host;
    private final SharedPreferences prefs;
    private final SheetHost sheets;

    FirstRunAccess(MainActivity host, SheetHost sheets) {
        this.host = host;
        this.sheets = sheets;
        prefs = host.getSharedPreferences("inkhome", 0);
    }

    void resume() {
        if (host.isFinishing() || host.isDestroyed()) return;
        int step = prefs.getInt(CURSOR, FILES);
        if (step >= DONE) return;
        if (host.store.setupDismissed() || (!prefs.contains(CURSOR) && host.hasFileAccess()
                && Stats.hasAccess(host) && SetupScreen.notificationsAllowed(host) && host.isDefaultHome())) {
            save(DONE);
            return;
        }
        Uri app = Uri.fromParts("package", host.getPackageName(), null);
        for (; step < DONE; step++) {
            switch (step) {
                case FILES:
                    if (host.hasFileAccess()) continue;
                    explain(step, "Show your books", "Allow file access so Ink Home can find books on your device.",
                            new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app));
                    return;
                case USAGE:
                    if (Stats.hasAccess(host)) continue;
                    explain(step, "Count reading time", "Allow usage access so Ink Home can count time in your reading apps.",
                            new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, app));
                    return;
                case LISTENER:
                    if (SetupScreen.notificationsAllowed(host)) continue;
                    explain(step, "Show notifications", "Allow notification access to see notifications in the control panel.",
                            new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                            new ComponentName(host, Notifs.class).flattenToString()));
                    return;
                case HOME:
                    if (host.isDefaultHome()) continue;
                    RoleManager roles = host.getSystemService(RoleManager.class);
                    if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                        save(step + 1);
                        if (launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))) return;
                    }
                    explain(step, "Choose your home app", "Choose Ink Home to open it when you press Home.",
                            new Intent(Settings.ACTION_HOME_SETTINGS));
                    return;
                case WIFI_NAME:
                    if (host.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) continue;
                    explain(step, "Show the Wi-Fi name", "Android requires location access to show your Wi-Fi network name in the control panel.", null);
                    return;
            }
        }
        save(DONE);
    }

    private void explain(int step, String title, String message, Intent settings) {
        // If the activity disappears while asking, leave setup deferred until opened manually.
        save(DONE);
        sheets.accessStep(title, message, () -> {
            save(step + 1);
            if (settings == null) {
                host.requestPermissions(new String[] { Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION }, 4601);
            } else if (!launch(settings)) {
                host.toast("This access page is unavailable on your device.");
                resume();
            }
        }, () -> {
            save(step + 1);
            resume();
        });
    }

    private boolean launch(Intent intent) {
        try {
            host.startActivityForResult(intent, 4602);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    private void save(int next) {
        prefs.edit().putInt(CURSOR, next).apply();
    }

}

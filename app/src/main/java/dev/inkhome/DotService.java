package dev.inkhome;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;

import dev.inkhome.folio.DisplayProbe;

/** Launcher-level floating clean control. Android owns the overlay permission. */
public final class DotService extends Service {
    public static final String ACTION_SETTINGS = "dev.inkhome.action.INK_DOT_SETTINGS";
    private static final String BALL = "com.onyx.floatingbutton";
    private static final String CHANNEL = "ink-dot";
    private static final int NOTIFICATION = 517;
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("inkhome", MODE_PRIVATE);
    }

    public static boolean available(Context c) { return DisplayProbe.displayClass(c).isEink(); }
    public static boolean permitted(Context c) { return ShellService.connected() || Settings.canDrawOverlays(c); }
    public static boolean enabled(Context c) {
        return available(c) && permitted(c) && prefs(c).getBoolean("dotEnabled", false);
    }
    public static int size(Context c) { return prefs(c).getInt("dotSize", 42); }
    public static void setSize(Context c, int size) {
        if (size == 36 || size == 42 || size == 52)
            prefs(c).edit().putInt("dotSize", size).apply();
    }
    public static boolean ballAvailable(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName ball = new ComponentName(BALL, BALL + ".service.FloatButtonAccessibilityService");
        for (String name : list.split(":"))
            if (ball.equals(ComponentName.unflattenFromString(name))) return true;
        return false;
    }

    public static boolean openBallSettings(Activity activity) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(BALL);
        if (launch == null) return false;
        try { activity.startActivity(launch); return true; }
        catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }

    public static void setEnabled(MainActivity activity, boolean on) {
        if (on && !available(activity)) return;
        prefs(activity).edit().putBoolean("dotEnabled", on).apply();
        if (on && !permitted(activity)) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName())));
                return;
            } catch (ActivityNotFoundException unavailable) {
                activity.toast(activity.getString(R.string.service_ui_overlay_permission_settings_unavailable));
            }
        }
        resume(activity);
    }

    public static void resume(Activity activity) {
        Intent service = new Intent(activity, DotService.class);
        if (ShellService.connected()) activity.stopService(service);
        else if (enabled(activity)) activity.startForegroundService(service);
        else {
            prefs(activity).edit().remove("dotEnabled").apply();
            activity.stopService(service);
        }
    }

    private DotOverlay dot;
    @Override public void onCreate() {
        super.onCreate();
        dot = new DotOverlay(this, false, this::stopSelf);
    }

    static Intent settingsIntent(Context context) {
        return new Intent(context, MainActivity.class).setAction(ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    // Lint does not resolve this service for the suffixed dev application ID.
    // Both manifests declare specialUse and the merged APK carries type 0x40000000.
    @android.annotation.SuppressLint("ForegroundServiceType")
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.service_ui_ink_dot), NotificationManager.IMPORTANCE_LOW));
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.service_ui_ink_dot_is_on))
                .setContentText(getString(R.string.service_ui_open_ink_dot_settings)).setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, settingsIntent(this), PendingIntent.FLAG_IMMUTABLE))
                .build();
        startForeground(NOTIFICATION, note, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        if (ShellService.connected() || !enabled(this)) { stopSelf(); return START_NOT_STICKY; }
        dot.refresh();
        return enabled(this) ? START_STICKY : START_NOT_STICKY;
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        dot.refresh();
    }

    @Override public void onDestroy() {
        dot.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

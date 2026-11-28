package dev.inkhome;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextClock;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.MotionEvent;
import dev.inkhome.folio.widget.Button;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;

import static android.view.WindowManager.LayoutParams.*;

/** Uses only window-state package names. Never requests screen content or filters hardware keys. */
public final class ShellService extends AccessibilityService {
    static final String PANEL = "dev.inkhome.action.SHELL_PANEL";
    static final String SWITCHER = "dev.inkhome.action.SHELL_SWITCHER";
    private static ShellService active;
    private String foreground = "";
    private TextClock strip;
    private ScrollView panel;
    private DotOverlay dot;

    static boolean connected() { return active != null; }

    static int dotPolicy(Context c, String pkg) {
        boolean tuck = pkg.equals(c.getPackageName()) || new Store(c).readingApps().contains(pkg)
                || DotService.tucked(c);
        return DotService.prefs(c).getInt("dotPolicy:" + pkg, tuck ? 1 : 0);
    }

    static void setDotPolicy(Context c, String pkg, int mode) {
        DotService.prefs(c).edit().putInt("dotPolicy:" + pkg, mode).apply();
    }

    static void openPanel() {
        if (active != null) active.togglePanel();
    }

    static void disable() {
        if (active == null) return;
        ShellService service = active;
        service.disconnect();
        service.disableSelf();
    }

    static void open(Context context, String action) {
        context.startActivity(new Intent(context, MainActivity.class).setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    private final BroadcastReceiver lockState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { updateStrip(); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener settings = (p, key) -> {
        if (key == null || "statusBar".equals(key) || "night".equals(key)
                || "displayClass".equals(key) || "textScale".equals(key) || "uiScale".equals(key)) {
            removeStrip();
            updateStrip();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (dot != null) return;
        active = this;
        stopService(new Intent(this, DotService.class));
        dot = new DotOverlay(this, true, () -> android.util.Log.w("InkShell", "Dot overlay unavailable"));
        IntentFilter changes = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        changes.addAction(Intent.ACTION_SCREEN_ON);
        changes.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(lockState, changes);
        DotService.prefs(this).registerOnSharedPreferenceChangeListener(settings);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (dot == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        // Our own floating controls can emit window-state events over another app.
        if (pkg.equals(getPackageName()) && event.getClassName() != null
                && event.getClassName().toString().startsWith("android.widget.")) return;
        if (pkg.equals(foreground)) return;
        closePanel();
        foreground = pkg;
        dot.foreground(pkg);
        updateStrip();
    }

    private void updateStrip() {
        Store store = new Store(this);
        if (foreground.isEmpty() || foreground.equals(getPackageName()) || !store.statusBar()
                || !getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) {
            removeStrip();
            return;
        }
        if (strip != null) return;
        // A compact affordance leaves the rest of the system bar accessible.
        Folio f = new Folio(this, store.textScale(), store.uiScale());
        TextClock clock = new TextClock(this);
        clock.setTextColor(f.palette.ink);
        clock.setTypeface(Type.sans(this, 600));
        clock.setGravity(Gravity.CENTER);
        clock.setBackgroundColor(f.palette.paper);
        clock.setContentDescription(getString(R.string.service_ui_open_ink_home_control_panel));
        clock.setOnClickListener(v -> togglePanel());
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT,
                TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        clock.setPadding(f.u(10), f.u(4), f.u(10), f.u(4));
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.setFitInsetsTypes(0);
        try {
            getSystemService(WindowManager.class).addView(clock, p);
            strip = clock;
        } catch (WindowManager.BadTokenException | SecurityException ignored) {
            android.util.Log.w("InkShell", "Clock overlay unavailable", ignored);
        }
    }

    private void togglePanel() {
        if (panel != null) { closePanel(); return; }
        Store store = new Store(this);
        Folio f = new Folio(this, store.textScale(), store.uiScale());
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackgroundColor(f.palette.paper);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI))
            action(f, rows, getString(R.string.service_ui_wi_fi), () -> systemSettings(Settings.ACTION_WIFI_SETTINGS));
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            action(f, rows, getString(R.string.service_ui_bluetooth), () -> systemSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        if (f.displayClass.isEink()) action(f, rows, getString(R.string.service_ui_strong_clean),
                () -> dot.clean(panel));
        action(f, rows, getString(R.string.service_ui_switcher), () -> open(this, SWITCHER));
        action(f, rows, getString(R.string.service_ui_all_ink_home_controls), () -> open(this, PANEL));
        action(f, rows, getString(R.string.service_ui_close), () -> {});
        ScrollView sheet = new ScrollView(this);
        sheet.addView(rows);
        sheet.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
            closePanel();
            return true;
        });
        WindowManager windows = getSystemService(WindowManager.class);
        int width = Math.min(f.u(300), windows.getCurrentWindowMetrics().getBounds().width());
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, WRAP_CONTENT,
                TYPE_ACCESSIBILITY_OVERLAY, FLAG_NOT_FOCUSABLE | FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        try {
            windows.addView(sheet, p);
            panel = sheet;
        } catch (WindowManager.BadTokenException | SecurityException ignored) {
            open(this, PANEL);
        }
    }

    private void action(Folio f, LinearLayout rows, String label, Runnable run) {
        rows.addView(Button.create(f, label, Button.Style.SECONDARY, v -> {
            run.run();
            closePanel();
        }));
    }

    private void systemSettings(String screen) {
        try { startActivity(new Intent(screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (ActivityNotFoundException | SecurityException denied) {
            dot.showNotice(getString(R.string.service_ui_settings_unavailable));
        }
    }

    private void closePanel() {
        if (panel != null) {
            getSystemService(WindowManager.class).removeViewImmediate(panel);
            panel = null;
        }
    }

    private void removeStrip() {
        closePanel();
        if (strip != null) {
            getSystemService(WindowManager.class).removeViewImmediate(strip);
            strip = null;
        }
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (dot != null) { removeStrip(); updateStrip(); dot.refresh(); }
    }

    private void disconnect() {
        if (dot == null) return;
        active = null;
        DotService.prefs(this).unregisterOnSharedPreferenceChangeListener(settings);
        unregisterReceiver(lockState);
        removeStrip();
        dot.close();
        dot = null;
        foreground = "";
        // Restore the launcher host only when the user already enabled both the dot
        // and its separate Android overlay permission.
        if (DotService.enabled(this)) {
            try { startForegroundService(new Intent(this, DotService.class)); }
            catch (IllegalStateException | SecurityException restricted) {
                // Android can restrict a background start. MainActivity retries on resume.
                android.util.Log.i("InkShell", "Launcher dot will resume with Ink Home", restricted);
            }
        }
    }
    @Override public void onInterrupt() {}
    @Override public boolean onUnbind(Intent intent) { disconnect(); return super.onUnbind(intent); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }
}

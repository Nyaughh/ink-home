package dev.inkhome;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import dev.inkhome.folio.DisplayProbe;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.epd.Epd;
import dev.inkhome.folio.core.TypeScale;

import static android.view.WindowManager.LayoutParams.*;

/** Launcher-level floating clean control. Android owns the overlay permission. */
public final class DotService extends Service implements InkDot.Actions {
    public static final String ACTION_SETTINGS = "dev.inkhome.action.INK_DOT_SETTINGS";
    private static final String BALL = "com.onyx.floatingbutton";
    private static final String CHANNEL = "ink-dot";
    private static final int NOTIFICATION = 517;
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("inkhome", MODE_PRIVATE);
    }

    public static boolean available(Context c) { return DisplayProbe.displayClass(c).isEink(); }
    public static boolean permitted(Context c) { return Settings.canDrawOverlays(c); }
    public static boolean enabled(Context c) {
        return available(c) && permitted(c) && prefs(c).getBoolean("dotEnabled", false);
    }
    public static int size(Context c) { return prefs(c).getInt("dotSize", 42); }
    public static boolean tucked(Context c) { return prefs(c).getBoolean("dotTucked", false); }
    public static void setSize(Context c, int size) {
        if (size == 36 || size == 42 || size == 52)
            prefs(c).edit().putInt("dotSize", size).apply();
    }
    public static void setTucked(Context c, boolean tucked) {
        prefs(c).edit().putBoolean("dotTucked", tucked).apply();
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

    public static void setEnabled(Activity activity, boolean on) {
        if (on && !available(activity)) return;
        prefs(activity).edit().putBoolean("dotEnabled", on).apply();
        if (on && !permitted(activity)) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName())));
                return;
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(activity, "Overlay permission settings unavailable", Toast.LENGTH_SHORT).show();
            }
        }
        resume(activity);
    }

    public static void resume(Activity activity) {
        Intent service = new Intent(activity, DotService.class);
        if (enabled(activity)) activity.startForegroundService(service);
        else {
            prefs(activity).edit().remove("dotEnabled").apply();
            activity.stopService(service);
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Context overlay;
    private WindowManager windows;
    private Folio folio;
    private InkDot dot;
    private ScrollView menu;
    private FrameLayout flash;
    private Display display;
    private boolean left;
    private Rect area;
    private int rotation;
    private WindowManager.LayoutParams placement;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsChanged = (p, key) -> {
        if (key == null || key.startsWith("dot") || "displayClass".equals(key)
                || "uiScale".equals(key) || "textScale".equals(key) || "night".equals(key)) refresh();
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { refresh(); }
    };
    private final DisplayManager.DisplayListener displays = new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) {}
        public void onDisplayRemoved(int id) {}
        public void onDisplayChanged(int id) {
            if (id == display.getDisplayId() && (rotation != display.getRotation() || !bounds().equals(area))) refresh();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        DisplayManager dm = getSystemService(DisplayManager.class);
        display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        overlay = createDisplayContext(display).createWindowContext(TYPE_APPLICATION_OVERLAY, null);
        windows = overlay.getSystemService(WindowManager.class);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, filter);
        dm.registerDisplayListener(displays, main);
        prefs(this).registerOnSharedPreferenceChangeListener(prefsChanged);
    }

    private Intent settingsIntent() {
        return new Intent(this, MainActivity.class).setAction(ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    // Lint does not resolve this service for the suffixed dev application ID.
    // Both manifests declare specialUse and the merged APK carries type 0x40000000.
    @android.annotation.SuppressLint("ForegroundServiceType")
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Ink dot", NotificationManager.IMPORTANCE_LOW));
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Ink dot is on")
                .setContentText("Open Ink dot settings").setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, settingsIntent(), PendingIntent.FLAG_IMMUTABLE))
                .build();
        startForeground(NOTIFICATION, note, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        refresh();
        return enabled(this) ? START_STICKY : START_NOT_STICKY;
    }

    private WindowManager.LayoutParams params(int width, int height, int extraFlags) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_FOCUSABLE | extraFlags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.windowAnimations = 0;
        return p;
    }

    private boolean add(View v, WindowManager.LayoutParams p) {
        try { windows.addView(v, p); return true; }
        catch (WindowManager.BadTokenException | SecurityException denied) {
            stopSelf();
            return false;
        }
    }

    private void remove(View v) {
        if (v != null && v.getParent() != null) windows.removeViewImmediate(v);
    }

    private void clear() {
        remove(menu);
        menu = null;
        remove(dot);
        dot = null;
        remove(flash);
        flash = null;
    }

    private Rect bounds() {
        WindowMetrics metrics = windows.getCurrentWindowMetrics();
        Rect frame = new Rect(metrics.getBounds());
        Insets bars = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        frame.set(frame.left + bars.left, frame.top + bars.top,
                frame.right - bars.right, frame.bottom - bars.bottom);
        return frame;
    }

    private String rotationKey(String base) { return base + display.getRotation(); }

    private void refresh() {
        clear();
        if (!enabled(this)) { stopSelf(); return; }
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        Store store = new Store(this);
        folio = new Folio(overlay, store.textScale(), store.uiScale());
        area = bounds();
        rotation = display.getRotation();
        boolean tucked = tucked(this);
        left = prefs(this).getBoolean(rotationKey("edgeLeft"), ballAvailable(this));
        int hit = Math.max(folio.u(48), folio.u(size(this))+4);
        int width = hit;
        float fraction = prefs(this).getFloat(rotationKey("edgeHeight"), .58f);
        placement = params(width, hit, 0);
        int gap = tucked ? 0 : Math.max(0, folio.u(6) - (hit-folio.u(size(this)))/2);
        placement.x = left ? gap : area.width()-width-gap;
        placement.y = marginTop() + Math.round(travel(hit)*fraction);
        dot = new InkDot(folio, this);
        dot.appearance(tucked, left, size(this), false);
        add(dot, placement);
    }

    private int marginTop() { return folio.u(30); }
    private int travel(int height) {
        return Math.max(0, area.height()-marginTop()-folio.u(52)-height);
    }

    @Override public void drop(float x, float y, float dx) {
        boolean inward = left ? dx > folio.u(16) : dx < -folio.u(16);
        boolean off = left ? x <= area.left+folio.u(4) : x >= area.right-folio.u(4);
        float fraction = (y-area.top-marginTop()-placement.height/2f) / Math.max(1, travel(placement.height));
        prefs(this).edit().putBoolean(rotationKey("edgeLeft"), x < area.exactCenterX())
                .putFloat(rotationKey("edgeHeight"), Math.max(0f, Math.min(1f, fraction)))
                .putBoolean("dotTucked", off || (tucked(this) && !inward)).apply();
        refresh();
    }

    private void closeMenu() {
        if (menu == null) return;
        remove(menu);
        menu = null;
        if (dot != null) dot.appearance(tucked(this), left, size(this), false);
    }

    @Override public void options() {
        if (menu != null) { closeMenu(); return; }
        if (dot == null) return;
        menu = new ScrollView(overlay);
        GradientDrawable border = new GradientDrawable();
        border.setColor(folio.palette.paper);
        border.setStroke(folio.rule, folio.palette.ink);
        menu.setBackground(border);
        LinearLayout rows = new LinearLayout(overlay);
        rows.setOrientation(LinearLayout.VERTICAL);
        menu.addView(rows);
        row(rows, "Clean screen now", this::clean);
        row(rows, tucked(this) ? "Show dot" : "Tuck to edge", () -> setTucked(this, !tucked(this)));
        row(rows, "Hide dot", () -> prefs(this).edit().putBoolean("dotEnabled", false).apply());
        row(rows, "Settings", () -> startActivity(settingsIntent()));
        int width = Math.min(folio.u(252), area.width()-placement.width-folio.u(12));
        if (width <= 0) { menu = null; return; }
        rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);
        int height = Math.min(rows.getMeasuredHeight()+folio.rule*2, area.height());
        WindowManager.LayoutParams p = params(width, height, FLAG_WATCH_OUTSIDE_TOUCH);
        p.x = left ? placement.x+placement.width : placement.x-width;
        p.y = Math.min(placement.y, area.height()-height);
        menu.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { closeMenu(); return true; }
            return false;
        });
        if (add(menu, p)) dot.appearance(tucked(this), left, size(this), true);
    }

    private void row(LinearLayout rows, String label, Runnable action) {
        TextView row = Type.text(folio, label, TypeScale.BODY, folio.palette.ink);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(folio.u(12), 0, folio.u(12), 0);
        row.setMinHeight(folio.u(48));
        row.setOnClickListener(v -> { closeMenu(); action.run(); });
        rows.addView(row);
    }

    @Override public void clean() {
        closeMenu();
        if (flash != null) return;
        boolean nativeClean = dot != null && Epd.requestFullClean(dot);
        if (BuildConfig.DEBUG) android.util.Log.i("InkDot", nativeClean
                ? "Overlay refreshScreen(98) returned" : "Using full-screen black/white fallback");
        if (nativeClean) return;
        // Draw the black and white fallback across the display without changing any app's mode.
        FrameLayout layer = new FrameLayout(overlay);
        flash = layer;
        layer.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams p = params(-1, -1, FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN);
        p.setFitInsetsTypes(0);
        p.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        if (!add(layer, p)) return;
        main.postDelayed(() -> { if (flash == layer) layer.setBackgroundColor(Color.WHITE); }, 140);
        main.postDelayed(() -> {
            if (flash == layer) { remove(layer); flash = null; }
        }, 300);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        refresh();
    }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        unregisterReceiver(screen);
        getSystemService(DisplayManager.class).unregisterDisplayListener(displays);
        prefs(this).unregisterOnSharedPreferenceChangeListener(prefsChanged);
        clear();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

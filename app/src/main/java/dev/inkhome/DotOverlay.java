package dev.inkhome;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.*;
import android.widget.*;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.epd.Epd;
import dev.inkhome.folio.core.TypeScale;

import static android.view.WindowManager.LayoutParams.*;

import static dev.inkhome.DotService.*;

final class DotOverlay extends ContextWrapper implements InkDot.Actions {
    private final boolean shell;
    private final Runnable unavailable;
    private String foreground = "";
    void foreground(String pkg) {
        if (!foreground.equals(pkg)) { foreground = pkg; refresh(); }
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private Context overlay;
    private WindowManager windows;
    private Folio folio;
    private InkDot dot;
    private ScrollView menu;
    private FrameLayout flash;
    private TextView notice;
    private final Runnable expireNotice = this::clearNotice;
    private Display display;
    private boolean left;
    private Rect area;
    private int rotation;
    private int dragStartX, dragStartY;
    private WindowManager.LayoutParams placement;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsChanged = (p, key) -> {
        if (key == null || key.startsWith("dot") || "readingApps".equals(key) || "displayClass".equals(key)
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

    DotOverlay(Context service, boolean shell, Runnable unavailable) {
        super(service);
        this.shell = shell;
        this.unavailable = unavailable;
        DisplayManager dm = getSystemService(DisplayManager.class);
        display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        overlay = shell ? service : createDisplayContext(display).createWindowContext(TYPE_APPLICATION_OVERLAY, null);
        windows = overlay.getSystemService(WindowManager.class);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screen, filter);
        dm.registerDisplayListener(displays, main);
        prefs(this).registerOnSharedPreferenceChangeListener(prefsChanged);
    }

    private WindowManager.LayoutParams params(int width, int height, int extraFlags) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, shell ? TYPE_ACCESSIBILITY_OVERLAY : TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_FOCUSABLE | extraFlags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.windowAnimations = 0;
        return p;
    }

    private boolean add(View v, WindowManager.LayoutParams p) {
        try { windows.addView(v, p); return true; }
        catch (WindowManager.BadTokenException | SecurityException denied) {
            unavailable.run();
            return false;
        }
    }

    private void remove(View v) {
        if (v != null && v.getParent() != null) windows.removeViewImmediate(v);
    }

    private void clearNotice() {
        main.removeCallbacks(expireNotice);
        remove(notice);
        notice = null;
    }

    void showNotice(String message) {
        clearNotice();
        Store store = new Store(this);
        Folio style = new Folio(overlay, store.textScale(), store.uiScale());
        TextView label = Type.text(style, message, TypeScale.BODY, style.palette.onInverse);
        label.setBackgroundColor(style.palette.inverse);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(style.u(16), 0, style.u(16), 0);
        label.setMinHeight(style.u(42));
        WindowManager.LayoutParams p = params(MATCH_PARENT, WRAP_CONTENT, FLAG_NOT_TOUCHABLE);
        p.gravity = Gravity.BOTTOM;
        if (add(label, p)) {
            notice = label;
            label.announceForAccessibility(message);
            main.postDelayed(expireNotice, 4000);
        }
    }

    private void clear() {
        clearNotice();
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

    void refresh() {
        clear();
        if (!enabled(this)) {
            if (!shell) unavailable.run();
            return;
        }
        if (shell && (foreground.isEmpty() || ShellService.dotHidden(this, foreground))) return;
        if (!getSystemService(PowerManager.class).isInteractive()
                || getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        Store store = new Store(this);
        folio = new Folio(overlay, store.textScale(), store.uiScale());
        area = bounds();
        rotation = display.getRotation();
        int minTarget = Math.round(48 * getResources().getDisplayMetrics().density);
        int hit = Math.max(minTarget, folio.u(48));
        placement = params(Math.min(hit, area.width()),
                Math.min(Math.max(hit, folio.u(size(this))), area.height()), 0);
        int maxX = area.width() - placement.width;
        int maxY = area.height() - placement.height;
        DotPosition position = DotPosition.load(prefs(this), rotation, maxX, maxY, ballAvailable(this));
        left = position.x < maxX / 2f;
        placement.x = position.x;
        placement.y = position.y;
        dot = new InkDot(folio, this);
        updateAppearance();
        add(dot, placement);
    }

    private void updateAppearance() {
        float horizontal = DotPosition.fraction(placement.x, area.width() - placement.width);
        dot.appearance(horizontal, size(this));
    }

    @Override public void beginDrag() {
        dragStartX = placement.x;
        dragStartY = placement.y;
        closeMenu();
    }

    @Override public void move(float dx, float dy) {
        int x = DotPosition.clamp(dragStartX + Math.round(dx), area.width() - placement.width);
        int y = DotPosition.clamp(dragStartY + Math.round(dy), area.height() - placement.height);
        if (placement.x == x && placement.y == y) return;
        placement.x = x;
        placement.y = y;
        updateAppearance();
        windows.updateViewLayout(dot, placement);
    }

    @Override public void endDrag(boolean save) {
        if (!save) {
            move(0, 0);
            return;
        }
        int maxX = area.width() - placement.width;
        placement.x = DotPosition.nearestSide(placement.x, maxX);
        left = placement.x == 0;
        updateAppearance();
        windows.updateViewLayout(dot, placement);
        new DotPosition(placement.x, placement.y).save(prefs(this), rotation,
                maxX, area.height() - placement.height);
    }

    private void closeMenu() {
        if (menu == null) return;
        remove(menu);
        menu = null;
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
        row(rows, getString(R.string.service_ui_strong_clean), this::clean);
        if (shell) row(rows, getString(R.string.service_ui_hide_in_this_app), () -> ShellService.setDotHidden(this, foreground, true));
        else row(rows, getString(R.string.service_ui_hide_dot), () -> prefs(this).edit().putBoolean("dotEnabled", false).apply());
        if (shell) {
            row(rows, getString(R.string.service_ui_control_panel), ShellService::openPanel);
            row(rows, getString(R.string.service_ui_ink_home), () -> ShellService.open(this, Intent.ACTION_MAIN));
            row(rows, getString(R.string.service_ui_switcher), () -> ShellService.open(this, ShellService.SWITCHER));
        }
        row(rows, getString(R.string.service_ui_settings), () -> startActivity(settingsIntent(this)));
        int width = Math.min(folio.u(252), area.width());
        if (width <= 0) { menu = null; return; }
        rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);
        int height = Math.min(rows.getMeasuredHeight()+folio.rule*2, area.height());
        WindowManager.LayoutParams p = params(width, height, FLAG_WATCH_OUTSIDE_TOUCH);
        int beside = left ? placement.x + placement.width : placement.x - width;
        p.x = DotPosition.clamp(beside, area.width() - width);
        p.y = Math.min(placement.y, area.height()-height);
        menu.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) { closeMenu(); return true; }
            return false;
        });
        add(menu, p);
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
        clean(dot);
    }

    void clean(View target) {
        closeMenu();
        if (flash != null) return;
        boolean nativeClean = target != null && Epd.requestFullClean(target);
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
        if (!add(layer, p)) { flash = null; return; }
        main.postDelayed(() -> { if (flash == layer) layer.setBackgroundColor(Color.WHITE); }, 140);
        main.postDelayed(() -> {
            if (flash == layer) { remove(layer); flash = null; }
        }, 300);
    }

    void close() {
        main.removeCallbacksAndMessages(null);
        unregisterReceiver(screen);
        getSystemService(DisplayManager.class).unregisterDisplayListener(displays);
        prefs(this).unregisterOnSharedPreferenceChangeListener(prefsChanged);
        clear();
    }
}

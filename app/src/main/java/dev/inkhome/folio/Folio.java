package dev.inkhome.folio;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;

import dev.inkhome.folio.core.*;

public final class Folio {
    public final Context context;
    public final DisplayClass displayClass;
    public final Palette palette;
    public final boolean night;
    public final int accentIndex;
    public final float uiScale, textScale;
    public final int hairline, rule;
    private final float density;

    public Folio(Context context, float textScale, float uiScale) {
        this.context = context;
        this.textScale = textScale;
        this.uiScale = uiScale;
        displayClass = DisplayProbe.displayClass(context);
        night = DisplayProbe.night(context);
        accentIndex = DisplayProbe.accentIndex(context);
        palette = Palette.of(displayClass, night, accentIndex);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        hairline = WindowClass.hairline(density);
        rule = hairline * Tokens.Stroke.RULE_MULTIPLIER;
        if (context instanceof Activity) applyWindow(((Activity) context).getWindow());
    }

    public int u(float units) { return Math.round(units * density * uiScale); }

    public int solidInk(float width, float height) {
        int minimum = u(Tokens.Shape.ACCENT_MIN_THICKNESS);
        return Math.min(width, height) >= minimum ? palette.accent : palette.ink;
    }

    /** Gallery colour updates are slow enough that live progress stays in black and white. */
    public int progressInk(float width, float height) {
        return displayClass == DisplayClass.COLOR_EINK && DisplayProbe.isGalleryPanel()
                ? palette.ink : solidInk(width, height);
    }

    public WindowClass windowClass() {
        return WindowClass.fromWidth(context.getResources().getConfiguration().screenWidthDp / uiScale);
    }

    private void applyWindow(Window window) {
        window.setBackgroundDrawable(new ColorDrawable(palette.paper));
        window.setNavigationBarColor(palette.paper);
        window.setStatusBarColor(palette.paper);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(night ? flags & ~light : flags | light);
    }
}

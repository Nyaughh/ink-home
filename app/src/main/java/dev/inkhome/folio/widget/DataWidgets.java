package dev.inkhome.folio.widget;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.ImageView;
import dev.inkhome.folio.Glyph;

final class DataWidgets {
    private DataWidgets() {}

    static GradientDrawable shape(Folio f, int fill, int stroke, boolean round) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        if (stroke > 0) d.setStroke(stroke, f.palette.strong);
        if (round) d.setShape(GradientDrawable.OVAL);
        return d;
    }

    static void action(View view, View.OnClickListener listener) {
        view.setFocusable(true);
        view.setOnClickListener(listener);
    }

    static android.graphics.drawable.Drawable focus(Folio f) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(f, Color.TRANSPARENT, f.rule, false));
        if (!f.displayClass.isEink()) {
            int overlay = (f.palette.ink & 0x00ffffff) | 0x1a000000;
            states.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(overlay));
        }
        return states;
    }

    static TextView label(Folio f, String value, TypeScale role) {
        return Type.text(f, value, role, f.palette.ink);
    }

    static ImageView icon(Folio f, String name, int color) {
        ImageView image = new ImageView(f.context);
        Glyph glyph = new Glyph(name);
        glyph.setTint(color);
        image.setImageDrawable(glyph);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return image;
    }

    static LinearLayout.LayoutParams share() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    static LinearLayout column(Folio f) {
        LinearLayout column = new LinearLayout(f.context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static void line(LinearLayout column, Folio f, int height) {
        View line = new View(f.context);
        line.setBackgroundColor(height == f.rule ? f.palette.strong : f.palette.rule);
        column.addView(line, -1, height);
    }
}

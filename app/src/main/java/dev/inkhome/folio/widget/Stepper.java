package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Glyph;
import dev.inkhome.folio.Shapes;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import java.util.function.IntConsumer;

public final class Stepper extends LinearLayout {
    private final ImageButton minus, plus;
    private final TextView value;
    private final String[] values;
    private int selected;

    public Stepper(Folio f, String label, String[] values, int selected, IntConsumer changed) {
        super(f.context);
        this.values = values.clone();
        this.selected = Math.max(0, Math.min(selected, values.length - 1));
        setGravity(Gravity.CENTER_VERTICAL);
        minus = button(f, "minus", f.context.getString(R.string.reader_ui_decrease, label), () -> move(-1, changed));
        plus = button(f, "plus", f.context.getString(R.string.reader_ui_increase, label), () -> move(1, changed));
        value = Type.text(f, "", TypeScale.BODY, f.palette.ink);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(f.u(36));
        value.setPadding(f.u(6), 0, f.u(6), 0);
        value.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(minus, f.u(48), f.u(48));
        addView(value);
        addView(plus, f.u(48), f.u(48));
        update();
    }

    public static ImageButton button(Folio f, String glyph, String label, Runnable click) {
        ImageButton b = new ImageButton(f.context);
        Glyph icon = new Glyph(glyph);
        icon.setTint(f.palette.ink);
        b.setImageDrawable(icon);
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        b.setPadding(f.u(9), f.u(7), f.u(9), f.u(7));
        b.setBackground(new Shapes(f).stroke(f.palette.paper, f.palette.ink));
        b.setContentDescription(label);
        b.setForeground(DataWidgets.focus(f));
        b.setOnClickListener(v -> click.run());
        return b;
    }

    static void available(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setVisibility(enabled ? VISIBLE : INVISIBLE);
    }

    private void move(int delta, IntConsumer changed) {
        selected += delta;
        update();
        changed.accept(selected);
    }

    private void update() {
        value.setText(values[selected]);
        setButtonEnabled(minus, selected > 0);
        setButtonEnabled(plus, selected < values.length - 1);
    }

    private static void setButtonEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .38f);
    }
}

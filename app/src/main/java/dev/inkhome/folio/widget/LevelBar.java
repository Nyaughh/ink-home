package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Shapes;
import java.util.function.IntConsumer;

/** Equal, directly selectable levels with single-step end buttons. */
public final class LevelBar extends LinearLayout {
    private final Folio f;
    private final View[] segments;
    private final ImageButton minus, plus;
    private final IntConsumer changed;
    private int selected;

    public LevelBar(Folio f, String label, String[] values, int selected, IntConsumer changed) {
        super(f.context);
        this.f = f;
        this.selected = selected;
        this.changed = changed;
        setGravity(Gravity.CENTER_VERTICAL);
        segments = new View[values.length];
        minus = Stepper.button(f, "minus", f.context.getString(R.string.reader_ui_decrease, label), () -> select(this.selected - 1));
        plus = Stepper.button(f, "plus", f.context.getString(R.string.reader_ui_increase, label), () -> select(this.selected + 1));
        addView(minus, f.u(38), f.u(34));
        for (int i = 0; i < values.length; i++) {
            final int level = i;
            View v = new View(f.context);
            segments[i] = v;
            v.setContentDescription(f.context.getString(R.string.reader_ui_label_value, label, values[i]));
            v.setFocusable(true);
            v.setForeground(DataWidgets.focus(f));
            v.setOnClickListener(view -> select(level));
            LayoutParams params = new LayoutParams(0, f.u(30), 1);
            params.leftMargin = f.u(3);
            if (i == values.length - 1) params.rightMargin = f.u(3);
            addView(v, params);
        }
        addView(plus, f.u(38), f.u(34));
        update();
    }

    private void select(int index) {
        if (index == selected || index < 0 || index >= segments.length) return;
        selected = index;
        update();
        changed.accept(index);
    }

    private void update() {
        Shapes shapes = new Shapes(f);
        for (int i = 0; i < segments.length; i++) {
            segments[i].setSelected(i == selected);
            segments[i].setBackground(shapes.stroke(i <= selected ? f.palette.inverse : f.palette.paper, f.palette.ink));
        }
        Stepper.available(minus, selected > 0);
        Stepper.available(plus, selected < segments.length - 1);
    }
}

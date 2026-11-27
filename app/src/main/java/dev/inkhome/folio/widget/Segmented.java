package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Shapes;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import java.util.function.IntConsumer;

public final class Segmented extends LinearLayout {
    private final Folio f;
    private final TextView[] options;
    private int selected;

    public Segmented(Folio f, String label, String[] choices, int selected, IntConsumer changed) {
        super(f.context);
        this.f = f;
        this.selected = selected;
        options = new TextView[choices.length];
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(f.hairline, f.hairline, f.hairline, f.hairline);
        setBackground(new Shapes(f).stroke(f.palette.paper, f.palette.ink));
        for (int i = 0; i < choices.length; i++) {
            if (i > 0) {
                View line = new View(f.context);
                line.setBackgroundColor(f.palette.ink);
                addView(line, f.hairline, -1);
            }
            int index = i;
            TextView option = Type.text(f, choices[i], TypeScale.LABEL, f.palette.ink);
            options[i] = option;
            option.setGravity(Gravity.CENTER);
            option.setForeground(DataWidgets.focus(f));
            option.setFocusable(true);
            option.setPadding(f.u(4), 0, f.u(4), 0);
            option.setMinHeight(f.u(34) - 2 * f.hairline);
            option.setContentDescription(f.context.getString(R.string.reader_ui_label_value, label, choices[i]));
            option.setOnClickListener(v -> {
                if (this.selected == index) return;
                this.selected = index;
                update();
                changed.accept(index);
            });
            addView(option, new LayoutParams(0, -1, 1));
        }
        update();
    }

    private void update() {
        for (int i = 0; i < options.length; i++) {
            boolean active = i == selected;
            options[i].setSelected(active);
            options[i].setBackgroundColor(active ? f.palette.inverse : f.palette.paper);
            options[i].setTextColor(active ? f.palette.onInverse : f.palette.ink);
        }
    }
}

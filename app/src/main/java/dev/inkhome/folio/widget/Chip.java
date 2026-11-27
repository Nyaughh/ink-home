package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;

public final class Chip {
    private Chip() {}

    public static View create(Folio f, String label, View.OnClickListener remove) {
        LinearLayout chip = new LinearLayout(f.context);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(f.u(8), 0, f.u(8), 0);
        chip.setMinimumHeight(f.u(30));
        chip.setBackground(DataWidgets.shape(f, f.palette.paper, f.hairline, false));
        chip.addView(DataWidgets.label(f, label, TypeScale.LABEL));
        LinearLayout.LayoutParams close = new LinearLayout.LayoutParams(f.u(14), f.u(14));
        close.setMarginStart(f.u(6));
        chip.addView(DataWidgets.icon(f, "close", f.palette.ink), close);
        chip.setContentDescription(f.context.getString(R.string.reader_ui_remove_filter, label));
        DataWidgets.action(chip, remove);
        chip.setForeground(DataWidgets.focus(f));
        return chip;
    }
}

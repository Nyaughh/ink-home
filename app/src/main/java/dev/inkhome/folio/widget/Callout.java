package dev.inkhome.folio.widget;

import android.view.View;
import android.widget.LinearLayout;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;

public final class Callout {
    private Callout() {}

    public static LinearLayout create(Folio f, String title, String sentence, String actionLabel,
                                      View.OnClickListener action) {
        LinearLayout column = DataWidgets.column(f);
        column.setPadding(f.u(12), f.u(12), f.u(12), f.u(12));
        column.setBackground(DataWidgets.shape(f, f.palette.paper, f.rule, false));
        column.addView(DataWidgets.label(f, title, TypeScale.TITLE_SM));
        column.addView(DataWidgets.label(f, sentence, TypeScale.BODY));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = f.u(8);
        column.addView(Button.create(f, actionLabel, Button.Style.PRIMARY, action), params);
        return column;
    }
}

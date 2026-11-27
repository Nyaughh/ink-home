package dev.inkhome.folio.widget;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;

public final class DataRow {
    private DataRow() {}

    public static LinearLayout create(Folio f, String name, float fraction, String value) {
        LinearLayout row = new LinearLayout(f.context);
        row.setMinimumHeight(f.u(34));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(DataWidgets.label(f, name, TypeScale.BODY), DataWidgets.share());
        Progress bar = new Progress(f, 3, false, Math.round(fraction * 100));
        row.addView(bar, f.u(84), -2);
        TextView number = DataWidgets.label(f, value, TypeScale.META);
        number.setGravity(Gravity.END);
        number.setMinWidth(f.u(48));
        row.addView(number);
        return row;
    }
}

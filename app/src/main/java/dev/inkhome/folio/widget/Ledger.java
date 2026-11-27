package dev.inkhome.folio.widget;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;

public final class Ledger {
    private Ledger() {}

    public static LinearLayout create(Folio f, String[] labels, String[] values) {
        if (labels.length != values.length) throw new IllegalArgumentException("Each metric needs a value");
        LinearLayout table = DataWidgets.column(f);
        DataWidgets.line(table, f, f.rule);
        for (int start = 0; start < labels.length; start += 3) {
            if (start > 0) DataWidgets.line(table, f, f.hairline);
            LinearLayout row = new LinearLayout(f.context);
            row.setBaselineAligned(false);
            table.addView(row, -1, -2);
            for (int col = 0; col < 3; col++) {
                if (col > 0) {
                    View divider = new View(f.context);
                    divider.setBackgroundColor(f.palette.rule);
                    row.addView(divider, f.hairline, -1);
                }
                LinearLayout cell = DataWidgets.column(f);
                cell.setMinimumHeight(f.u(46));
                cell.setGravity(Gravity.CENTER_VERTICAL);
                // Give enlarged figures more of their column before wrapping onto another line.
                int inset = f.u(f.textScale > 1f ? 2 : 6);
                cell.setPadding(inset, 0, inset, 0);
                int index = start + col;
                if (index < labels.length) {
                    cell.addView(DataWidgets.label(f, labels[index], TypeScale.META));
                    cell.addView(DataWidgets.label(f, values[index], TypeScale.TITLE_SM));
                }
                row.addView(cell, DataWidgets.share());
            }
        }
        return table;
    }
}

package dev.inkhome.folio.widget;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;

public final class MonthGrid {
    private MonthGrid() {}

    public static LinearLayout create(Folio f, YearMonth month, DayOfWeek firstDay, int[] minutes,
                                     int goal, LocalDate today, Consumer<LocalDate> select) {
        if (minutes.length != month.lengthOfMonth()) throw new IllegalArgumentException("Provide minutes for every date");
        LinearLayout calendar = DataWidgets.column(f);
        int offset = Math.floorMod(month.atDay(1).getDayOfWeek().getValue() - firstDay.getValue(), 7);
        int threshold = goal > 0 ? goal : 30;
        for (int start = -offset; start < minutes.length; start += 7) {
            LinearLayout week = new LinearLayout(f.context);
            week.setBaselineAligned(false);
            LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(-1, -2);
            if (start != -offset) weekParams.topMargin = f.u(4);
            calendar.addView(week, weekParams);
            for (int col = 0; col < 7; col++) {
                int index = start + col;
                LinearLayout cell = DataWidgets.column(f);
                LinearLayout.LayoutParams params = DataWidgets.share();
                if (col > 0) params.setMarginStart(f.u(4));
                week.addView(cell, params);
                if (index < 0 || index >= minutes.length) continue;
                LocalDate date = month.atDay(index + 1);
                int count = minutes[index];
                boolean met = count >= threshold;
                boolean useAccent = met && goal > 0;
                int color = useAccent ? f.palette.onAccent : met ? f.palette.onInverse : f.palette.ink;
                LinearLayout labels = DataWidgets.column(f);
                labels.setGravity(Gravity.CENTER);
                labels.setMinimumHeight(f.u(40));
                labels.setBackground(DataWidgets.shape(f, useAccent ? f.palette.accent : met ? f.palette.inverse : f.palette.paper,
                        count > 0 && !met ? f.hairline : 0, false));
                cell.addView(labels, -1, -2);
                labels.addView(Type.text(f, String.valueOf(index + 1), TypeScale.LABEL, color));
                labels.addView(Type.text(f, count + "m", TypeScale.META, color));
                if (date.equals(today)) {
                    View underline = new View(f.context);
                    underline.setBackgroundColor(f.palette.strong);
                    cell.addView(underline, -1, f.u(3));
                }
                cell.setContentDescription(date + ", " + count + " minutes" + (met ? ", goal met" : "")
                        + (date.equals(today) ? ", today" : ""));
                if (select != null) {
                    DataWidgets.action(cell, v -> select.accept(date));
                    cell.setForeground(DataWidgets.focus(f));
                }
            }
        }
        return calendar;
    }
}

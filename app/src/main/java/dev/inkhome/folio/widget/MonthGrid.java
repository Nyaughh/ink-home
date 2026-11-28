package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        // Each date owns the same two bands, even when its minutes are blank.
        for (int start = -offset; start < minutes.length; start += 7) {
            LinearLayout week = new LinearLayout(f.context);
            week.setBaselineAligned(false);
            calendar.addView(week, -1, -2);
            for (int col = 0; col < 7; col++) {
                int index = start + col;
                LinearLayout cell = DataWidgets.column(f);
                cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                week.addView(cell, DataWidgets.share());
                if (index < 0 || index >= minutes.length) continue;
                LocalDate date = month.atDay(index + 1);
                int count = minutes[index];
                boolean met = goal > 0 && count >= goal;
                TextView number = Type.text(f, String.valueOf(index + 1), TypeScale.LABEL,
                        met ? f.palette.onInverse : f.palette.ink);
                number.setGravity(Gravity.CENTER);
                number.setSingleLine();
                int badgeHeight = Math.max(f.u(20), number.getLineHeight() + f.u(2));
                int badgeWidth = Math.max(f.u(28), (int) Math.ceil(number.getPaint().measureText("88")) + f.u(4));
                number.setBackground(DataWidgets.shape(f, met ? f.palette.inverse : f.palette.paper,
                        count > 0 && !met ? f.hairline : 0, false));
                cell.addView(number, badgeWidth, badgeHeight);
                View marker = new View(f.context);
                if (date.equals(today)) marker.setBackgroundColor(f.palette.strong);
                cell.addView(marker, badgeWidth, f.rule);
                TextView duration = Type.text(f, count > 0
                        ? f.context.getString(R.string.reader_ui_short_minutes, count) : "",
                        TypeScale.CHART, f.palette.ink);
                duration.setGravity(Gravity.CENTER);
                duration.setSingleLine();
                cell.addView(duration, -1, -2);
                cell.setPadding(0, f.u(2), 0, 0);
                cell.setScreenReaderFocusable(true);
                number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                duration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                String description = f.context.getResources().getQuantityString(
                        R.plurals.reader_ui_date_minutes, count, date, count);
                if (goal > 0 && met) description = f.context.getString(R.string.reader_ui_goal_met, description);
                if (date.equals(today)) description = f.context.getString(R.string.reader_ui_today, description);
                cell.setContentDescription(description);
                if (select != null) {
                    DataWidgets.action(cell, v -> select.accept(date));
                    cell.setForeground(DataWidgets.focus(f));
                }
            }
        }
        return calendar;
    }
}

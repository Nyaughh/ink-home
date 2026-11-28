package dev.inkhome;

import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.widget.MonthGrid;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Calendar;
import java.util.Locale;

final class CalendarTile extends DataTile {
    static final HomeComponent COMPONENT = new BuiltinTile("calendar", R.string.stats_ui_calendar, 1, 1,
            CalendarTile::new, 0, "default", "day");
    static final HomeComponent READING_MONTH = new BuiltinTile("month", R.string.home_settings_month, 1, 1,
            CalendarTile::new, 0, "default", "week");

    private CalendarTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
        calendar("month".equals(item.type));
    }

    private void calendar(boolean activity) {
        observe(() -> {
            tile.removeAllViews();
            LocalDate today = LocalDate.now();
            YearMonth month = YearMonth.from(today);
            Locale locale = Locale.getDefault();
            boolean weekOnly = activity && "week".equals(item.variant);
            String monthName = today.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
            boolean hasStats = !activity || Stats.hasAccess(a);
            if (activity && (!hasStats || a.stats == null)) {
                readingTimeMessage(hasStats);
                prependHeading(a.getString(weekOnly ? R.string.stats_ui_this_week : R.string.stats_ui_this_month));
                return;
            }
            DayOfWeek first = a.store.weekStartsMonday() ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY;
            if ((activity || !"day".equals(item.variant))
                    && showGrid(today, month, first, locale, monthName, activity, weekOnly)) return;
            if (activity) {
                tile.addView(label(Stats.hm(a, minutesOn(today)), TypeScale.BODY, 1));
                prependHeading(today.getDayOfWeek().getDisplayName(TextStyle.FULL, locale));
            } else {
                String day = Integer.toString(today.getDayOfMonth());
                TextView number = label(day, compact ? TypeScale.TITLE : TypeScale.DISPLAY, 1);
                if (measuredHeight(number) > height || textWidth(number) > width) number = label(day, TypeScale.BODY, 1);
                tile.addView(number);
                TextView weekday = label(today.getDayOfWeek().getDisplayName(TextStyle.FULL, locale), TypeScale.META, 1);
                if (measuredHeight(number) + measuredHeight(weekday) <= height) tile.addView(weekday);
                prependHeading(monthName);
            }
        }, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
        action(() -> {
            if (activity) {
                a.statsTab = "calendar";
                a.statsMonth.setTimeInMillis(System.currentTimeMillis());
                a.go(MainActivity.Screen.STATS);
            } else {
                Uri date = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                        .appendPath(String.valueOf(System.currentTimeMillis())).build();
                a.start(new Intent(Intent.ACTION_VIEW, date));
            }
        });
    }

    private boolean showGrid(LocalDate today, YearMonth month, DayOfWeek first, Locale locale,
                             String monthName, boolean activity, boolean weekOnly) {
        int cellWidth = width / 7;
        TextView sample = center("88");
        if (cellWidth < textWidth(sample) || height < measuredHeight(sample) * 2) return false;
        LinearLayout grid = ink.col();
        LinearLayout names = ink.row();
        grid.addView(names);
        for (int i = 0; i < 7; i++) {
            names.addView(center(first.plus(i).getDisplayName(TextStyle.NARROW, locale)), ink.weight(1));
        }
        LocalDate weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(first));
        LocalDate start = month.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(first));
        int weeks = (int) ((month.atEndOfMonth().toEpochDay() - start.toEpochDay()) / 7 + 1);
        if (!weekOnly && height >= (weeks + 1) * measuredHeight(sample)) {
            String title = activity ? a.getString(R.string.stats_ui_this_month) : monthName;
            if (activity && !compact && cellWidth >= Math.max(ink.dp(28),
                    textWidth(label("88", TypeScale.LABEL, 1)) + ink.dp(4))) {
                int[] minutes = new int[month.lengthOfMonth()];
                for (int i = 0; i < minutes.length; i++) minutes[i] = minutesOn(month.atDay(i + 1));
                grid.addView(MonthGrid.create(ink.folio, month, first, minutes, a.store.dailyGoal(), today, null));
                if (useGrid(grid, title)) return true;
                grid.removeViewAt(1);
            }
            for (; !start.isAfter(month.atEndOfMonth()); start = start.plusWeeks(1)) {
                grid.addView(week(start, month, today, activity, false));
            }
            if (useGrid(grid, title)) return true;
            grid.removeViews(1, grid.getChildCount() - 1);
        }
        String title = activity ? a.getString(R.string.stats_ui_this_week) : monthName;
        if (activity) {
            grid.addView(week(weekStart, null, today, true, true));
            if (useGrid(grid, title)) return true;
            grid.removeViewAt(1);
        }
        grid.addView(week(weekStart, null, today, activity, false));
        return useGrid(grid, title);
    }

    private boolean useGrid(LinearLayout grid, String title) {
        if (measuredHeight(grid) > height) return false;
        tile.addView(grid);
        prependHeading(title);
        return true;
    }

    private TextView center(String text) {
        TextView cell = label(text, TypeScale.CHART, 1);
        cell.setGravity(Gravity.CENTER);
        return cell;
    }

    private LinearLayout week(LocalDate start, YearMonth month, LocalDate today, boolean activity, boolean durations) {
        LinearLayout row = ink.row();
        row.setBaselineAligned(false);
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            LinearLayout cell = ink.col();
            row.addView(cell, ink.weight(1));
            if (month != null && !YearMonth.from(date).equals(month)) continue;
            TextView number = center(String.valueOf(date.getDayOfMonth()));
            cell.addView(number);
            int minutes = activity ? minutesOn(date) : 0;
            if (date.equals(today)) {
                number.setBackgroundColor(ink.folio.palette.inverse);
                number.setTextColor(ink.folio.palette.onInverse);
            }
            if (durations) {
                cell.addView(center(a.getString(R.string.reader_ui_short_minutes, minutes)));
            } else if (activity && minutes > 0) {
                View mark = new View(a);
                mark.setBackgroundColor(ink.TEXT);
                cell.addView(mark, -1, ink.folio.rule);
            }
            cell.setContentDescription(activity ? a.getResources().getQuantityString(
                    R.plurals.reader_ui_date_minutes, minutes, date, minutes)
                    : date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)));
        }
        return row;
    }

    private int minutesOn(LocalDate day) {
        Calendar date = Calendar.getInstance();
        date.set(day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
        return a.stats.on(date);
    }
}

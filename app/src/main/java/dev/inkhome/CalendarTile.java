package dev.inkhome;

import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

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
        TextView sample = center("88");
        int line = measuredHeight(sample);
        int minimum = Math.max(line + (activity ? ink.folio.rule : 0), textWidth(sample));
        LocalDate start = month.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(first));
        int weeks = (int) ((month.atEndOfMonth().toEpochDay() - start.toEpochDay()) / 7 + 1);
        int side = Math.min(width / 7, Math.max(0, height - line) / weeks);
        boolean monthFits = !weekOnly && side >= minimum;
        if (!monthFits) {
            start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(first));
            weeks = 1;
            side = Math.min(width / 7, Math.max(0, height - line));
            if (side < minimum) return false;
        }
        String title = activity ? a.getString(monthFits ? R.string.stats_ui_this_month
                : R.string.stats_ui_this_week) : monthName;
        TextView heading = center(title);
        int headingHeight = measuredHeight(heading);
        int titledSide = Math.min(width / 7, Math.max(0, height - line - headingHeight) / weeks);
        tile.setGravity(Gravity.CENTER);
        if (titledSide >= minimum) {
            side = titledSide;
            tile.addView(heading, -1, headingHeight);
        }
        LinearLayout names = ink.row();
        for (int i = 0; i < 7; i++) {
            names.addView(center(first.plus(i).getDisplayName(TextStyle.NARROW, locale)), side, line);
        }
        tile.addView(names, 7 * side, line);
        boolean durations = activity && side >= line * 2 + ink.folio.rule
                && side >= textWidth(center(a.getString(R.string.reader_ui_short_minutes, 1440)));
        for (int i = 0; i < weeks; i++) {
            tile.addView(week(start.plusWeeks(i), monthFits ? month : null, today, activity, durations, side),
                    7 * side, side);
        }
        return true;
    }

    private TextView center(String text) {
        TextView cell = label(text, TypeScale.CHART, 1);
        cell.setGravity(Gravity.CENTER);
        return cell;
    }

    private LinearLayout week(LocalDate start, YearMonth month, LocalDate today, boolean activity, boolean durations, int side) {
        LinearLayout row = ink.row();
        row.setBaselineAligned(false);
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            LinearLayout cell = ink.col();
            cell.setGravity(Gravity.CENTER);
            row.addView(cell, side, side);
            if (month != null && !YearMonth.from(date).equals(month)) continue;
            TextView number = center(String.valueOf(date.getDayOfMonth()));
            cell.addView(number);
            int minutes = activity ? minutesOn(date) : 0;
            boolean met = activity && a.store.dailyGoal() > 0 && minutes >= a.store.dailyGoal();
            boolean current = date.equals(today);
            boolean filled = activity ? met : current;
            if (filled) {
                cell.setBackgroundColor(ink.folio.palette.inverse);
                number.setTextColor(ink.folio.palette.onInverse);
            }
            if (durations && minutes > 0) {
                TextView duration = center(a.getString(R.string.reader_ui_short_minutes, minutes));
                if (filled) duration.setTextColor(ink.folio.palette.onInverse);
                cell.addView(duration);
            }
            if (activity && (current || !durations && minutes > 0)) {
                View mark = new View(a);
                mark.setBackgroundColor(filled ? ink.folio.palette.onInverse : ink.TEXT);
                cell.addView(mark, current ? side : side / 3, ink.folio.rule);
            }
            String description = activity ? a.getResources().getQuantityString(
                    R.plurals.reader_ui_date_minutes, minutes, date, minutes)
                    : date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL));
            if (met) description = a.getString(R.string.reader_ui_goal_met, description);
            if (current) description = a.getString(R.string.reader_ui_today, description);
            cell.setContentDescription(description);
        }
        return row;
    }

    private int minutesOn(LocalDate day) {
        Calendar date = Calendar.getInstance();
        date.set(day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
        return a.stats.on(date);
    }
}

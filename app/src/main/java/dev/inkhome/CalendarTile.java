package dev.inkhome;

import android.content.Intent;
import dev.inkhome.folio.widget.MonthGrid;
import android.net.Uri;
import android.provider.CalendarContract;
import android.view.Gravity;
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

final class CalendarTile extends HomeTiles {
    static final HomeComponent COMPONENT = new BuiltinTile("calendar", R.string.stats_ui_calendar, 2, 4,
            CalendarTile::new, 0, "default", "day");
    static final HomeComponent READING_MONTH = new BuiltinTile("month", R.string.home_settings_month, 2, 4,
            CalendarTile::new, 0, "default", "week");

    private CalendarTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        calendar("month".equals(item.type));
    }

    private void calendar(boolean activity) {
        observe(() -> {
            tile.removeAllViews();
            LocalDate today = LocalDate.now();
            YearMonth month = YearMonth.from(today);
            Locale locale = Locale.getDefault();
            boolean weekOnly = activity && "week".equals(item.variant);
            heading(activity ? a.getString(weekOnly ? R.string.stats_ui_this_week : R.string.stats_ui_this_month)
                    : today.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)));
            if (activity && (!Stats.hasAccess(a) || a.stats == null)) {
                tile.addView(label(a.getString(!Stats.hasAccess(a) ? R.string.stats_ui_turn_on_reading_time
                        : R.string.stats_ui_loading_reading_time), TypeScale.META, 3));
                return;
            }
            if (!activity && "day".equals(item.variant)) {
                tile.addView(label(Integer.toString(today.getDayOfMonth()), TypeScale.DISPLAY, 1));
                tile.addView(label(today.getDayOfWeek().getDisplayName(TextStyle.FULL, locale), TypeScale.BODY, 1));
                return;
            }
            DayOfWeek first = a.store.weekStartsMonday() ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY;
            LinearLayout days = ink.row();
            tile.addView(days);
            for (int i = 0; i < 7; i++) {
                TextView name = label(first.plus(i).getDisplayName(TextStyle.NARROW, locale), TypeScale.CHART, 1);
                name.setGravity(Gravity.CENTER);
                days.addView(name, ink.weight(1));
            }
            if (activity) {
                addReadingDates(today, month, first, weekOnly);
                return;
            }
            LinearLayout dates = ink.col();
            dates.setOnClickListener(v -> tile.performClick());
            tile.addView(ink.scroller(dates), new LinearLayout.LayoutParams(-1, 0, 1));
            int offset = Math.floorMod(month.atDay(1).getDayOfWeek().getValue() - first.getValue(), 7);
            for (int start = 1 - offset; start <= month.lengthOfMonth(); start += 7) {
                LinearLayout week = ink.row();
                week.setBaselineAligned(false);
                dates.addView(week);
                for (int day = start; day < start + 7; day++) {
                    boolean inMonth = day > 0 && day <= month.lengthOfMonth();
                    TextView cell = label(inMonth ? String.valueOf(day) : "", TypeScale.CHART, 1);
                    cell.setGravity(Gravity.CENTER);
                    cell.setMinimumHeight(ink.dp(compact || item.h <= 5 ? 16 : 26));
                    week.addView(cell, ink.weight(1));
                    if (!inMonth) continue;
                    LocalDate date = month.atDay(day);
                    if (date.equals(today)) {
                        cell.setBackgroundColor(ink.folio.palette.inverse);
                        cell.setTextColor(ink.folio.palette.onInverse);
                    }
                    cell.setContentDescription(date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL)));
                }
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

    private void addReadingDates(LocalDate today, YearMonth month, DayOfWeek first, boolean weekOnly) {
        if (weekOnly) {
            LocalDate start = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(first));
            LinearLayout row = ink.row();
            tile.addView(row);
            for (int i = 0; i < 7; i++) {
                LocalDate day = start.plusDays(i);
                LinearLayout column = ink.col();
                row.addView(column, ink.weight(1));
                TextView number = label(Integer.toString(day.getDayOfMonth()), TypeScale.BODY, 1);
                number.setGravity(Gravity.CENTER);
                column.addView(number);
                int minutes = minutesOn(day);
                TextView value = label(a.getString(R.string.reader_ui_short_minutes, minutes), TypeScale.CHART, 1);
                value.setGravity(Gravity.CENTER);
                column.addView(value);
            }
            return;
        }
        int[] minutes = new int[month.lengthOfMonth()];
        for (int i = 0; i < minutes.length; i++) minutes[i] = minutesOn(month.atDay(i + 1));
        LinearLayout grid = MonthGrid.create(ink.folio, month, first, minutes, a.store.dailyGoal(), today, date -> tile.performClick());
        android.widget.ScrollView scroll = ink.scroller(grid);
        tile.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private int minutesOn(LocalDate day) {
        Calendar date = Calendar.getInstance();
        date.set(day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
        return a.stats.on(date);
    }
}

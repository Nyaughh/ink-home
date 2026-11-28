package dev.inkhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.Settings;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Content-sized sections for the customized Home. */
final class HomeTiles {
    private final MainActivity a;
    private final Ink ink;
    private final HomeSections sections;
    private final LinearLayout tile;

    static View build(MainActivity a, String kind, HomeSections sections) {
        HomeTiles renderer = new HomeTiles(a, sections);
        renderer.fill(kind);
        return renderer.tile;
    }

    private HomeTiles(MainActivity a, HomeSections sections) {
        this.a = a;
        ink = a.ink;
        this.sections = sections;
        tile = ink.col();
        int pad = ink.dp(sections.compact ? 4 : 10);
        tile.setPadding(pad, pad, pad, pad);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        if (sections.borders) tile.setBackground(ink.card(0, false));
    }

    private void fill(String kind) {
        switch (kind) {
            case "current": current(); break;
            case "next": next(); break;
            case "apps": apps(); break;
            case "clock": clock(); break;
            case "calendar": calendar(false); break;
            case "month": calendar(true); break;
            case "goal": goal(); break;
            case "battery": battery(); break;
            default: heading(a.getString(R.string.tile_unavailable));
        }
    }

    private TextView label(String s, TypeScale role, int lines) {
        return ink.lines(ink.text(s, role, ink.TEXT), lines);
    }

    private void heading(String s) {
        tile.addView(ink.oneLine(ink.text(s, TypeScale.LABEL, ink.TEXT2)));
    }

    private void action(Runnable click) {
        tile.setBackground(sections.borders ? ink.card(0, true) : ink.pressable());
        tile.setOnClickListener(v -> click.run());
    }

    private void emptyBook() {
        boolean access = a.hasFileAccess();
        int title = !access ? R.string.library_ui_allow_file_access :
                a.booksLoaded ? R.string.launcher_no_book_yet : R.string.launcher_opening_your_library;
        tile.addView(label(a.getString(title), TypeScale.BODY, 2));
        action(() -> {
            if (access) a.go(MainActivity.Screen.LIBRARY);
            else a.requestFileAccess();
        });
    }

    private List<Library.Book> orderedBooks() {
        LinkedHashSet<Library.Book> result = new LinkedHashSet<>(a.reading());
        result.addAll(a.newest());
        return new ArrayList<>(result);
    }

    private void current() {
        if (!a.hasFileAccess() || a.books.isEmpty()) {
            emptyBook();
            return;
        }
        List<Library.Book> reading = a.reading();
        boolean newestInLibrary = reading.isEmpty();
        Library.Book book = newestInLibrary ? a.newest().get(0) : reading.get(0);
        tile.addView(a.homeBookHero(book, newestInLibrary, sections.compact ? 72 : 116, true));
    }

    private void next() {
        List<Library.Book> books = orderedBooks();
        heading(a.getString(R.string.launcher_up_next));
        if (books.size() < 2 || !a.hasFileAccess()) {
            if (books.isEmpty() || !a.hasFileAccess()) emptyBook();
            else {
                tile.addView(label(a.getString(R.string.launcher_library), TypeScale.BODY, 1));
                action(() -> a.go(MainActivity.Screen.LIBRARY));
            }
            return;
        }
        float height = a.getResources().getConfiguration().screenHeightDp / ink.folio.uiScale;
        int limit = sections.nextCount == 0 ? Math.max(1, (int) (height / 180)) : sections.nextCount;
        for (int i = 1; i <= Math.min(books.size() - 1, limit); i++) {
            View row = a.homeBookRow(books.get(i));
            row.setMinimumHeight(ink.dp(sections.compact ? 44 : 52));
            tile.addView(row);
        }
    }

    private void apps() {
        List<MainActivity.App> favorites = a.favoriteApps();
        heading(a.getString(R.string.launcher_favorites));
        if (favorites.isEmpty()) {
            tile.addView(label(a.getString(R.string.launcher_empty_favorites_hint), TypeScale.META, 3));
            action(() -> a.go(MainActivity.Screen.APPS));
            return;
        }
        float width = a.getResources().getConfiguration().screenWidthDp / ink.folio.uiScale;
        int columns = ink.folio.windowClass().columns(width, sections.compact ? 72 : 88);
        int limit = sections.appCount == 0 ? columns : sections.appCount;
        if (sections.verticalApps) columns = 1;
        LinearLayout row = null;
        for (int i = 0; i < Math.min(limit, favorites.size()); i++) {
            if (i % columns == 0) {
                row = ink.row();
                tile.addView(row);
            }
            MainActivity.App app = favorites.get(i);
            LinearLayout cell = sections.verticalApps ? ink.row() : ink.col();
            cell.setGravity(sections.verticalApps ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            cell.setMinimumHeight(ink.dp(48));
            cell.setBackground(ink.pressable());
            row.addView(cell, ink.weight(1));
            if (a.store.appIcons()) {
                int size = sections.compact ? 28 : 36;
                cell.addView(a.appIcon(app, size), ink.m(ink.dp(size), ink.dp(size), 0, 0,
                        sections.verticalApps ? 12 : 0, sections.verticalApps ? 0 : 4));
            }
            TextView name = label(a.displayLabel(app), TypeScale.APP_NAME, 1);
            if (!sections.verticalApps) name.setGravity(Gravity.CENTER);
            cell.addView(name, sections.verticalApps ? ink.weight(1) : new LinearLayout.LayoutParams(-1, -2));
            cell.setOnClickListener(v -> a.launch(app));
            cell.setOnLongClickListener(v -> { a.appMenu(app); return true; });
        }
        while (row != null && row.getChildCount() < columns) row.addView(new View(a), ink.weight(1));
    }

    private void clock() {
        TextView time = label("", TypeScale.TITLE, 1);
        LinearLayout face = ink.row();
        tile.addView(face);
        face.addView(time, ink.weight(1));
        TextView date = label("", TypeScale.META, 1);
        face.addView(date);
        observe(() -> {
            Date now = new Date();
            String value = a.timeText(now);
            if (!value.contentEquals(time.getText())) time.setText(value);
            String day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(now);
            if (!day.contentEquals(date.getText())) date.setText(day);
        }, Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
        action(() -> a.start(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
    }

    private void goal() {
        heading(a.getString(R.string.launcher_daily_goal));
        if (a.stats == null || !Stats.hasAccess(a)) {
            tile.addView(label(a.getString(a.stats == null && Stats.hasAccess(a)
                    ? R.string.stats_ui_loading_reading_time : R.string.stats_ui_turn_on_reading_time), TypeScale.META, 2));
        } else {
            int target = a.store.dailyGoal();
            int today = a.stats.today();
            LinearLayout numbers = ink.row();
            tile.addView(numbers);
            numbers.addView(label(Stats.hm(a, today), TypeScale.TITLE_SM, 1));
            if (target > 0) numbers.addView(label(a.getString(R.string.stats_ui_goal_minutes, target),
                    TypeScale.META, 1), ink.m(-2, -2, 8, 0, 0, 0));
            if (target > 0) tile.addView(ink.progress((float) today / target, 4), ink.m(-1, -2, 0, 4, 0, 0));
        }
        action(() -> { a.statsTab = "overview"; a.go(MainActivity.Screen.STATS); });
    }

    private void battery() {
        TextView percent = label("", TypeScale.TITLE_SM, 1);
        TextView status = label("", TypeScale.META, 1);
        LinearLayout body = ink.row();
        tile.addView(body);
        int size = 24;
        body.addView(ink.icon("battery", size), ink.m(ink.dp(size), ink.dp(size), 0, 0, 8, 0));
        body.addView(percent, ink.weight(1));
        body.addView(status);
        observe(() -> {
            MainActivity.Battery state = a.battery();
            String percentage = a.getString(R.string.launcher_percent, state.percent);
            if (!percentage.contentEquals(percent.getText())) percent.setText(percentage);
            String caption = a.getString(!state.charging ? R.string.launcher_on_battery :
                    state.percent == 100 ? R.string.launcher_charged : R.string.launcher_charging);
            if (!caption.contentEquals(status.getText())) status.setText(caption);
        }, Intent.ACTION_BATTERY_CHANGED);
        action(() -> a.start(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
    }

    private void calendar(boolean activity) {
        observe(() -> {
            tile.removeAllViews();
            LocalDate today = LocalDate.now();
            YearMonth month = YearMonth.from(today);
            Locale locale = Locale.getDefault();
            heading(activity ? a.getString(R.string.stats_ui_this_month)
                    : today.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)));
            if (activity && (!Stats.hasAccess(a) || a.stats == null)) {
                tile.addView(label(a.getString(!Stats.hasAccess(a) ? R.string.stats_ui_turn_on_reading_time
                        : R.string.stats_ui_loading_reading_time), TypeScale.META, 3));
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
            int offset = Math.floorMod(month.atDay(1).getDayOfWeek().getValue() - first.getValue(), 7);
            for (int start = 1 - offset; start <= month.lengthOfMonth(); start += 7) {
                LinearLayout week = ink.row();
                week.setBaselineAligned(false);
                tile.addView(week);
                for (int day = start; day < start + 7; day++) {
                    boolean inMonth = day > 0 && day <= month.lengthOfMonth();
                    TextView cell = label(inMonth ? String.valueOf(day) : "", TypeScale.CHART, 1);
                    cell.setGravity(Gravity.CENTER);
                    cell.setMinimumHeight(ink.dp(sections.compact ? 20 : 26));
                    week.addView(cell, ink.weight(1));
                    if (!inMonth) continue;
                    markDay(cell, month.atDay(day), activity, today);
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

    private void markDay(TextView cell, LocalDate date, boolean activity, LocalDate today) {
        Calendar cal = Calendar.getInstance();
        cal.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        int minutes = activity ? a.stats.on(cal) : 0;
        boolean isToday = date.equals(today);
        int goal = a.store.dailyGoal();
        if (activity ? minutes >= (goal > 0 ? goal : 30) : isToday) {
            boolean accent = activity && goal > 0;
            cell.setBackgroundColor(accent ? ink.folio.palette.accent : ink.folio.palette.inverse);
            cell.setTextColor(accent ? ink.folio.palette.onAccent : ink.folio.palette.onInverse);
        } else if (activity && minutes > 0) {
            cell.setBackground(ink.stroke(ink.PAPER, 0, ink.TEXT));
        }
        if (activity && isToday) cell.setPaintFlags(cell.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        String description = date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL));
        if (activity) description += ", " + Stats.hm(a, minutes);
        cell.setContentDescription(description);
    }

    /** Subscribe only while the tile is visible in the hierarchy. */
    private void observe(Runnable update, String... actions) {
        IntentFilter filter = new IntentFilter();
        for (String action : actions) filter.addAction(action);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { update.run(); }
        };
        tile.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                a.registerReceiver(receiver, filter);
                update.run();
            }
            @Override public void onViewDetachedFromWindow(View view) { a.unregisterReceiver(receiver); }
        });
    }
}

package dev.inkhome;

import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.widget.BarChart;
import dev.inkhome.folio.widget.DataRow;
import dev.inkhome.folio.widget.Ledger;
import dev.inkhome.folio.widget.MonthGrid;
import dev.inkhome.folio.widget.Progress;
import dev.inkhome.folio.widget.Structure;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class StatsScreen implements Screen {
    @Override public void build(MainActivity host) {
        LinearLayout col = host.page();
        int target = host.store.dailyGoal();
        TextView goal = host.ink.pill(target > 0 ? host.getString(R.string.stats_ui_goal_minutes, target) : host.getString(R.string.stats_ui_set_a_goal), false);
        goal.setOnClickListener(v -> host.chooseGoal());
        col.addView(host.header(host.getString(R.string.stats_ui_reading_time), null, null, goal));
        boolean calendar = host.statsTab.equals("calendar");
        col.addView(host.tabs(new String[]{host.getString(R.string.stats_ui_overview), host.getString(R.string.stats_ui_calendar)}, calendar ? 1 : 0,
                i -> { host.statsTab = i == 1 ? "calendar" : "overview"; host.render(); }),
                host.ink.m(-1, -2, MainActivity.PAD, 0, MainActivity.PAD, 0));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), 0);
        if (!Stats.hasAccess(host)) {
            col.addView(body);
            body.addView(host.note(host.getString(R.string.stats_ui_turn_on_reading_time), host.getString(R.string.stats_ui_reading_time_access_description),
                    () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        } else if (host.stats == null) {
            col.addView(body);
            body.addView(host.ink.muted(host.getString(R.string.stats_ui_loading_reading_time), 15));
        } else if (calendar) {
            renderCalendar(host, body, host.stats);
            Runnable previousMonth = host.pagePrev, nextMonth = host.pageNext;
            FrameLayout months = monthSwipes(host);
            int month = host.statsMonth.get(Calendar.MONTH);
            int year = host.statsMonth.get(Calendar.YEAR);
            months.addView(host.scrollable(body, "calendar:" + year + ":" + month), -1, -1);
            host.pagePrev = previousMonth;
            host.pageNext = nextMonth;
            col.addView(months, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            BarChart chart = renderOverview(host, body, host.stats);
            View viewport = host.scrollable(body);
            col.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
            viewport.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                // Let the plot absorb a small window inset while preserving both label bands.
                int available = b - t - (body.getMeasuredHeight() - chart.getMeasuredHeight());
                int height = chart.heightForSpace(available);
                if (chart.getLayoutParams().height != height) {
                    chart.getLayoutParams().height = height;
                    chart.requestLayout();
                }
            });
        }
    }

    private static FrameLayout monthSwipes(MainActivity host) {
        return new FrameLayout(host) {
            float x, y;
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { x = event.getX(); y = event.getY(); }
                if (event.getActionMasked() == MotionEvent.ACTION_UP && host.store.swipePages()) {
                    float dx = event.getX() - x;
                    if (Math.abs(dx) > host.ink.dp(48) && Math.abs(dx) > 2 * Math.abs(event.getY() - y)) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        (dx > 0 ? host.pagePrev : host.pageNext).run();
                        return true;
                    }
                }
                return super.dispatchTouchEvent(event);
            }
        };
    }

    private static BarChart renderOverview(MainActivity host, LinearLayout col, Stats s) {
        Folio f = host.ink.folio;
        Structure structure = new Structure(f);
        LinearLayout today = host.ink.row();
        today.setMinimumHeight(f.u(54));
        today.addView(host.ink.text(String.valueOf(s.today()), TypeScale.FIGURE, host.ink.TEXT));
        today.addView(host.ink.medium(host.getString(R.string.stats_ui_min_today), 15), host.ink.m(-2, -2, 4, 0, 12, 0));
        if (s.hasGoal()) {
            LinearLayout goal = host.ink.col();
            goal.addView(new Progress(f, 5, false, Math.round(100f * s.today() / s.goal)));
            int extra = s.today() - s.goal;
            String status = extra < 0 ? host.getString(R.string.stats_ui_goal_remaining, -extra) : extra == 0 ? host.getString(R.string.stats_ui_goal_met) : host.getString(R.string.stats_ui_goal_exceeded, extra);
            goal.addView(host.ink.muted(status, 14), host.ink.m(-1, -2, 0, 3, 0, 0));
            today.addView(goal, host.ink.weight(1));
        }
        col.addView(MainActivity.item(today));

        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR), finished = 0;
        List<Library.Book> ranked = new ArrayList<>();
        Calendar stamp = Calendar.getInstance();
        for (Library.Book b : host.books) {
            if (s.forBook(b.path) > 0) ranked.add(b);
            if (!b.finished) continue;
            stamp.setTimeInMillis(b.lastRead > 0 ? b.lastRead : b.modified);
            if (stamp.get(Calendar.YEAR) == year) finished++;
        }
        ranked.sort((a, b) -> Integer.compare(s.forBook(b.path), s.forBook(a.path)));
        col.addView(MainActivity.item(Ledger.create(f,
                new String[]{host.getString(R.string.stats_ui_best_streak, s.bestStreak()), host.getString(R.string.stats_ui_this_week), host.getString(R.string.stats_ui_this_month), host.getString(R.string.stats_ui_all_time),
                        host.getString(R.string.stats_ui_finished_in_year, year), host.getString(R.string.stats_ui_in_progress)},
                new String[]{quantity(host, R.plurals.stats_ui_days, s.currentStreak()), Stats.hm(host, s.weekTotal()), Stats.hm(host, s.monthTotal(now)),
                        Stats.hm(host, s.total()), quantity(host, R.plurals.stats_ui_books, finished), quantity(host, R.plurals.stats_ui_books, host.reading().size())})));

        LinearLayout week = MainActivity.keep(host.ink.col());
        week.addView(structure.sectionHead(host.getString(R.string.stats_ui_this_week), true, s.hasGoal() ? host.ink.faint(host.getString(R.string.stats_ui_filled_bars_met_the_goal), 13) : null));
        String[] names = new String[7];
        int[] values = new int[7];
        Calendar[] dates = s.week();
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            names[i] = weekday.format(dates[i].getTime());
            values[i] = s.on(dates[i]);
        }
        BarChart chart = new BarChart(f, names, values, s.goal);
        week.addView(chart);
        col.addView(week);

        LinearLayout most = MainActivity.keep(host.ink.col());
        most.addView(structure.sectionHead(host.getString(R.string.stats_ui_most_read), true, host.ink.faint(host.getString(R.string.stats_ui_all_time), 13)));
        if (ranked.isEmpty()) most.addView(host.ink.muted(host.getString(R.string.stats_ui_most_read_empty), 14));
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            Library.Book book = ranked.get(i);
            int minutes = s.forBook(book.path);
            LinearLayout row = DataRow.create(f, book.title, minutes / (float) s.forBook(ranked.get(0).path), Stats.hm(host, minutes));
            host.ink.oneLine((TextView) row.getChildAt(0));
            row.setBackground(i < Math.min(5, ranked.size()) - 1 ? host.ink.listRow(0) : host.ink.pressable());
            row.setOnClickListener(v -> host.openBook(book));
            most.addView(MainActivity.item(row));
        }
        col.addView(most);
        return chart;
    }

    private static String quantity(MainActivity host, int resource, int count) {
        return host.getResources().getQuantityString(resource, count, count);
    }

    private static void renderCalendar(MainActivity host, LinearLayout col, Stats s) {
        Folio f = host.ink.folio;
        Calendar selected = host.statsMonth;
        YearMonth month = YearMonth.of(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH) + 1);
        LocalDate today = LocalDate.now();
        LinearLayout head = host.ink.row();
        head.addView(host.ink.medium(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selected.getTime()), 18),
                host.ink.weight(1));
        host.pagePrev = () -> { selected.add(Calendar.MONTH, -1); host.render(); };
        host.pageNext = () -> { selected.add(Calendar.MONTH, 1); host.render(); };
        ImageView previous = host.ink.iconButton("back", host.pagePrev);
        previous.setContentDescription(host.getString(R.string.stats_ui_previous_month));
        ImageView next = host.ink.iconButton("chevron", host.pageNext);
        next.setContentDescription(host.getString(R.string.stats_ui_next_month));
        head.addView(previous);
        head.addView(next);
        col.addView(head);
        DayOfWeek first = s.mondayFirst ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY;
        LinearLayout weekdays = host.ink.row();
        weekdays.setMinimumHeight(f.u(20));
        for (int i = 0; i < 7; i++) {
            String name = first.plus(i).getDisplayName(TextStyle.SHORT, Locale.getDefault());
            weekdays.addView(host.ink.center(host.ink.faint(name, 13)), host.ink.weight(1));
        }
        col.addView(weekdays);
        int[] minutes = new int[month.lengthOfMonth()];
        int read = 0, met = 0, best = 0, bestDay = 0, streak = 0, longest = 0, total = 0;
        Calendar cursor = (Calendar) selected.clone();
        for (int i = 0; i < minutes.length; i++) {
            cursor.set(Calendar.DAY_OF_MONTH, i + 1);
            int n = minutes[i] = s.on(cursor);
            total += n;
            if (n > 0) read++;
            if (s.hasGoal() && n >= s.goal) met++;
            streak = (s.hasGoal() ? n >= s.goal : n > 0) ? streak + 1 : 0;
            longest = Math.max(longest, streak);
            if (n > best) { best = n; bestDay = i + 1; }
        }
        LinearLayout grid = MonthGrid.create(f, month, first, minutes, s.goal, today, null);
        for (int i = 0; i < grid.getChildCount(); i++) MainActivity.item(grid.getChildAt(i));
        col.addView(grid);
        int elapsed = month.equals(YearMonth.from(today)) ? today.getDayOfMonth()
                : month.isAfter(YearMonth.from(today)) ? 0 : minutes.length;
        col.addView(Ledger.create(f, new String[]{host.getString(R.string.stats_ui_read), host.getString(R.string.stats_ui_days_read), host.getString(R.string.stats_ui_goal_met),
                        bestDay == 0 ? host.getString(R.string.stats_ui_best_day) : host.getString(R.string.stats_ui_best_day_number, bestDay), host.getString(R.string.stats_ui_average_a_day), host.getString(R.string.stats_ui_longest_streak)},
                new String[]{Stats.hm(host, total), host.getString(R.string.stats_ui_days_read_of, read, elapsed), s.hasGoal() ? quantity(host, R.plurals.stats_ui_days, met) : host.getString(R.string.stats_ui_no_goal),
                        Stats.hm(host, best), Stats.hm(host, elapsed == 0 ? 0 : Math.round(total / (float) elapsed)), quantity(host, R.plurals.stats_ui_days, longest)}),
                host.ink.m(-1, -2, 0, 8, 0, 0));
        col.addView(host.ink.faint(s.hasGoal() ? host.getString(R.string.stats_ui_calendar_legend_with_goal)
                : host.getString(R.string.stats_ui_calendar_legend_without_goal), 13),
                host.ink.m(-1, -2, 0, 4, 0, 0));
    }
}

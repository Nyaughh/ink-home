package dev.inkhome;

import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        TextView goal = host.ink.pill(target > 0 ? "Goal " + target + " min" : "Set a goal", false);
        goal.setOnClickListener(v -> host.chooseGoal());
        col.addView(host.header("Reading time", null, null, goal));
        boolean calendar = host.statsTab.equals("calendar");
        col.addView(host.tabs(new String[]{"Overview", "Calendar"}, calendar ? 1 : 0,
                i -> { host.statsTab = i == 1 ? "calendar" : "overview"; host.render(); }),
                host.ink.m(-1, -2, MainActivity.PAD, 0, MainActivity.PAD, 0));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), 0);
        if (!Stats.hasAccess(host)) {
            col.addView(body);
            body.addView(host.note("Turn on reading time", "Allow usage access to count the minutes spent in reading apps.",
                    () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        } else if (host.stats == null) {
            col.addView(body);
            body.addView(host.ink.muted("Loading reading time…", 15));
        } else if (calendar) {
            renderCalendar(host, body, host.stats);
            ScrollView scroll = calendarScroll(host);
            scroll.addView(body);
            col.addView(scroll, -1, -1);
        } else {
            renderOverview(host, body, host.stats);
            col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private static ScrollView calendarScroll(MainActivity host) {
        ScrollView scroll = new ScrollView(host) {
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
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    private static void renderOverview(MainActivity host, LinearLayout col, Stats s) {
        Folio f = host.ink.folio;
        Structure structure = new Structure(f);
        LinearLayout today = host.ink.row();
        today.setMinimumHeight(f.u(54));
        today.addView(host.ink.text(String.valueOf(s.today()), TypeScale.FIGURE, host.ink.TEXT));
        today.addView(host.ink.medium("min today", 15), host.ink.m(-2, -2, 4, 0, 12, 0));
        if (s.hasGoal()) {
            LinearLayout goal = host.ink.col();
            goal.addView(new Progress(f, 5, false, Math.round(100f * s.today() / s.goal)));
            int extra = s.today() - s.goal;
            String status = extra < 0 ? -extra + " min to go" : extra == 0 ? "Goal met" : "Goal met · " + extra + " min over";
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
                new String[]{"Streak · best " + s.bestStreak(), "This week", "This month", "All time",
                        "Finished in " + year, "In progress"},
                new String[]{quantity(s.currentStreak(), "day"), Stats.hm(s.weekTotal()), Stats.hm(s.monthTotal(now)),
                        Stats.hm(s.total()), quantity(finished, "book"), quantity(host.reading().size(), "book")})));

        LinearLayout week = MainActivity.keep(host.ink.col());
        week.addView(structure.sectionHead("This week", true, s.hasGoal() ? host.ink.faint("Filled bars met the goal", 13) : null));
        String[] names = new String[7];
        int[] values = new int[7];
        Calendar[] dates = s.week();
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            names[i] = weekday.format(dates[i].getTime());
            values[i] = s.on(dates[i]);
        }
        week.addView(new BarChart(f, names, values, s.goal));
        col.addView(week);

        LinearLayout most = MainActivity.keep(host.ink.col());
        most.addView(structure.sectionHead("Most read", true, host.ink.faint("All time", 13)));
        if (ranked.isEmpty()) most.addView(host.ink.muted("Open a book from Ink Home to see its reading time here.", 14));
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            Library.Book book = ranked.get(i);
            int minutes = s.forBook(book.path);
            LinearLayout row = DataRow.create(f, book.title, minutes / (float) s.forBook(ranked.get(0).path), Stats.hm(minutes));
            host.ink.oneLine((TextView) row.getChildAt(0));
            row.setBackground(i < Math.min(5, ranked.size()) - 1 ? host.ink.listRow(0) : host.ink.pressable());
            row.setOnClickListener(v -> host.openBook(book));
            most.addView(MainActivity.item(row));
        }
        col.addView(most);
    }

    private static String quantity(int n, String unit) { return n + " " + unit + (n == 1 ? "" : "s"); }

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
        previous.setContentDescription("Previous month");
        ImageView next = host.ink.iconButton("chevron", host.pageNext);
        next.setContentDescription("Next month");
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
        col.addView(MonthGrid.create(f, month, first, minutes, s.goal, today, null));
        int elapsed = month.equals(YearMonth.from(today)) ? today.getDayOfMonth()
                : month.isAfter(YearMonth.from(today)) ? 0 : minutes.length;
        col.addView(Ledger.create(f, new String[]{"Read", "Days read", "Goal met",
                        bestDay == 0 ? "Best day" : "Best day · " + bestDay, "Average a day", "Longest streak"},
                new String[]{Stats.hm(total), read + " of " + elapsed, s.hasGoal() ? quantity(met, "day") : "No goal",
                        Stats.hm(best), Stats.hm(elapsed == 0 ? 0 : Math.round(total / (float) elapsed)), quantity(longest, "day")}),
                host.ink.m(-1, -2, 0, 8, 0, 0));
        col.addView(host.ink.faint(s.hasGoal() ? "Filled: goal met. Outlined: some reading. Small number: minutes."
                : "Filled: 30+ minutes. Outlined: some reading. Small number: minutes.", 13),
                host.ink.m(-1, -2, 0, 4, 0, 0));
    }
}

package dev.inkhome;

import dev.inkhome.folio.core.TypeScale;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class StatsScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        TextView goal = host.ink.pill(host.store.dailyGoal() > 0 ? "Goal " + host.store.dailyGoal() + " min" : "Set a goal", false);
        goal.setOnClickListener(v -> host.chooseGoal());
        col.addView(host.header("Reading time", null, null, goal));
        LinearLayout tabs = host.tabs(new String[]{"Overview", "Calendar"}, host.statsTab.equals("calendar") ? 1 : 0,
                i -> { host.statsTab = i == 1 ? "calendar" : "overview"; host.render(); });
        col.addView(tabs, host.ink.m(-1, -2, MainActivity.PAD, 0, MainActivity.PAD, 0));

        Stats s = host.stats != null ? host.stats : Stats.refresh(host, host.store);
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), host.ink.dp(18), host.ink.dp(MainActivity.PAD), host.ink.dp(16));
        if (!Stats.hasAccess(host)) {
            body.addView(host.note("Turn on reading time", "Ink Home counts the minutes your reading apps are on screen. Tap, then switch Ink Home on.",
                    () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))), host.ink.m(-1, -2, 0, 0, 0, 16));
        }
        if (host.statsTab.equals("calendar")) {
            renderCalendar(host, body, s);
            col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            renderOverview(host, body, s);
            col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
        }
    }

    private void renderOverview(MainActivity host, LinearLayout col, Stats s) {
        int today = s.today();
        col.addView(host.ink.faint("Today", 14));
        TextView big = host.ink.text(Stats.hm(today), TypeScale.FIGURE, host.ink.TEXT);
        big.setPadding(0, host.ink.dp(8), 0, 0);
        col.addView(big);
        if (s.hasGoal()) {
            col.addView(host.ink.progress(today / (float) s.goal, 5), host.ink.m(-1, -2, 0, 14, 0, 0));
            TextView g = host.ink.muted(today >= s.goal ? "Goal met" : (s.goal - today) + " min to your " + s.goal + " min goal", 14);
            g.setPadding(0, host.ink.dp(8), 0, 0);
            col.addView(g);
        }

        int best = s.bestStreak();
        col.addView(host.metricRow(host.metric("Streak", s.currentStreak() + (s.currentStreak() == 1 ? " day" : " days"), best > 0 ? "Best " + best : null),
                host.metric("This week", Stats.hm(s.weekTotal()), null)), host.ink.m(-1, -2, 0, 22, 0, 0));
        col.addView(host.metricRow(host.metric("This month", Stats.hm(s.monthTotal(Calendar.getInstance())), null),
                host.metric("All time", Stats.hm(s.total()), null)), host.ink.m(-1, -2, 0, 10, 0, 0));

        LinearLayout weekSec = MainActivity.keep(host.ink.col());
        col.addView(weekSec, host.ink.m(-1, -2, 0, 24, 0, 0));
        weekSec.addView(host.ink.heading("This week"));
        LinearLayout chartCard = host.ink.col();
        chartCard.setPadding(host.ink.dp(14), host.ink.dp(14), host.ink.dp(14), host.ink.dp(10));
        chartCard.setBackground(host.ink.card(12, false));
        chartCard.addView(host.new WeekChart(host, s), new LinearLayout.LayoutParams(-1, host.ink.dp(140)));
        weekSec.addView(chartCard);

        List<Library.Book> byTime = new ArrayList<>();
        for (Library.Book b : host.books) if (s.forBook(b.path) > 0) byTime.add(b);
        byTime.sort((a, b) -> Integer.compare(s.forBook(b.path), s.forBook(a.path)));
        LinearLayout mostSec = MainActivity.keep(host.ink.col());
        col.addView(mostSec, host.ink.m(-1, -2, 0, 24, 0, 0));
        mostSec.addView(host.ink.heading("Most read"));
        LinearLayout list = host.ink.col();
        list.setBackground(host.ink.card(12, false));
        if (byTime.isEmpty()) {
            TextView t = host.ink.muted("Open a book from Ink Home and its reading time shows up here.", 14);
            t.setPadding(host.ink.dp(16), host.ink.dp(14), host.ink.dp(16), host.ink.dp(14));
            list.addView(t);
        }
        for (int i = 0; i < Math.min(5, byTime.size()); i++) {
            Library.Book b = byTime.get(i);
            LinearLayout r = host.ink.row();
            r.setPadding(host.ink.dp(16), 0, host.ink.dp(16), 0);
            r.setBackground(i < Math.min(5, byTime.size()) - 1 ? host.ink.listRow(16) : host.ink.pressable());
            r.addView(host.ink.oneLine(host.ink.body(b.title)), host.ink.weight(1));
            r.addView(host.ink.muted(Stats.hm(s.forBook(b.path)), 14), host.ink.m(-2, -2, 12, 0, 0, 0));
            r.setOnClickListener(v -> host.openBook(b));
            list.addView(MainActivity.item(r), new LinearLayout.LayoutParams(-1, host.ink.dp(50)));
        }
        mostSec.addView(list);

        int year = Calendar.getInstance().get(Calendar.YEAR), finished = 0;
        for (Library.Book b : host.books) {
            if (!b.finished) continue;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(b.lastRead > 0 ? b.lastRead : b.modified);
            if (c.get(Calendar.YEAR) == year) finished++;
        }
        col.addView(host.metricRow(host.metric("Finished in " + year, String.valueOf(finished), null),
                host.metric("In progress", String.valueOf(host.reading().size()), null)), host.ink.m(-1, -2, 0, 24, 0, 0));
    }

    private void renderCalendar(MainActivity host, LinearLayout col, Stats s) {
        LinearLayout head = host.ink.row();
        head.addView(host.ink.medium(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(host.statsMonth.getTime()), 18), host.ink.weight(1));
        ImageView prev = host.ink.iconButton("back", () -> { host.statsMonth.add(Calendar.MONTH, -1); host.render(); });
        ImageView next = host.ink.iconButton("chevron", () -> { host.statsMonth.add(Calendar.MONTH, 1); host.render(); });
        head.addView(prev);
        head.addView(next, host.ink.m(-2, -2, 8, 0, 0, 0));
        col.addView(head);
        host.pagePrev = () -> { host.statsMonth.add(Calendar.MONTH, -1); host.render(); };
        host.pageNext = () -> { host.statsMonth.add(Calendar.MONTH, 1); host.render(); };

        LinearLayout names = host.ink.row();
        Calendar d = Calendar.getInstance();
        int first = s.mondayFirst ? Calendar.MONDAY : Calendar.SUNDAY;
        SimpleDateFormat dn = new SimpleDateFormat("EEEEE", Locale.getDefault());
        d.set(Calendar.DAY_OF_WEEK, first);
        for (int i = 0; i < 7; i++) {
            names.addView(host.ink.center(host.ink.faint(dn.format(d.getTime()), 13)), host.ink.weight(1));
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        col.addView(names, host.ink.m(-1, -2, 0, 18, 0, 8));

        Calendar day = (Calendar) host.statsMonth.clone();
        day.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (day.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
        int days = day.getActualMaximum(Calendar.DAY_OF_MONTH);
        int max = 1;
        for (int i = 1; i <= days; i++) {
            Calendar cd = (Calendar) day.clone();
            cd.set(Calendar.DAY_OF_MONTH, i);
            max = Math.max(max, s.on(cd));
        }
        int full = s.hasGoal() ? s.goal : Math.max(30, max);
        Calendar today = Calendar.getInstance();
        LinearLayout line = null;
        for (int i = 0; i < offset + days; i++) {
            if (i % 7 == 0) {
                line = host.ink.row();
                col.addView(line, host.ink.m(-1, host.ink.dp(48), 0, 0, 0, 5));
            }
            if (i < offset) { line.addView(new View(host), new LinearLayout.LayoutParams(0, -1, 1f)); continue; }
            int dom = i - offset + 1;
            Calendar cd = (Calendar) day.clone();
            cd.set(Calendar.DAY_OF_MONTH, dom);
            int m = s.on(cd);
            boolean isToday = cd.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cd.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            // Heat map: light grey for a little reading, darker as you approach the goal (or 30 min), black past it.
            float level = m == 0 ? 0 : Math.min(1f, m / (float) full);
            int fill = m == 0 ? Color.WHITE : level >= 1f ? host.ink.TEXT : level >= 0.5f ? 0xFF8C8C8C : 0xFFDADADA;
            boolean dark = fill == host.ink.TEXT || fill == 0xFF8C8C8C;
            LinearLayout c = host.ink.col();
            c.setGravity(Gravity.CENTER);
            c.setBackground(m > 0 ? host.ink.solid(fill, 8) : isToday ? host.ink.stroke(host.ink.PAPER, 8, host.ink.TEXT) : null);
            TextView num = host.ink.center(host.ink.text(String.valueOf(dom), TypeScale.META, dark ? Color.WHITE : host.ink.TEXT));
            c.addView(num, new LinearLayout.LayoutParams(-1, -2));
            if (m > 0) {
                TextView mins = host.ink.center(host.ink.text(String.valueOf(m), TypeScale.CHART, dark ? Color.WHITE : host.ink.TEXT2));
                mins.setPadding(0, host.ink.dp(3), 0, 0);
                c.addView(mins, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
            p.setMargins(host.ink.dp(3), 0, host.ink.dp(3), 0);
            line.addView(c, p);
        }
        while (line != null && line.getChildCount() < 7) line.addView(new View(host), new LinearLayout.LayoutParams(0, -1, 1f));

        int readDays = s.readingDaysInMonth(host.statsMonth);
        TextView sum = host.ink.medium(Stats.hm(s.monthTotal(host.statsMonth)) + " read on " + readDays + (readDays == 1 ? " day" : " days"), 15);
        sum.setPadding(0, host.ink.dp(18), 0, host.ink.dp(6));
        col.addView(sum);
        col.addView(host.ink.faint(s.hasGoal() ? "Darker days are closer to your goal. Black means you met it." : "Darker days mean more reading. The small number is minutes.", 13));
    }
}

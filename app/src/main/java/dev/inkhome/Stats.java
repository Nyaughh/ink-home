package dev.inkhome;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reading time, measured as the minutes your reading apps are on screen. Each session is also
 * credited to the book you last opened from Ink Home, so every book gets its own time read.
 * Android keeps detailed events for about a week; each refresh folds the last 8 days into
 * Ink Home's own log, which keeps the history for good.
 */
final class Stats {

    private static final SimpleDateFormat KEY = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
    private static final long SESSION_MAX = 4 * 3_600_000L;
    private static final long BOOK_LINK_WINDOW = 12 * 3_600_000L;

    final Map<String, Integer> minutes = new HashMap<>();
    final Map<String, Integer> bookMinutes = new HashMap<>();
    /** 0 when no goal is set. */
    final int goal;
    final boolean mondayFirst;

    private Stats(int goal, boolean mondayFirst) {
        this.goal = goal;
        this.mondayFirst = mondayFirst;
    }

    boolean hasGoal() { return goal > 0; }

    static boolean hasAccess(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Folds recent usage into the stored logs and returns the full history. Call off the UI thread. */
    static Stats refresh(Context c, Store store) {
        Stats s = new Stats(store.dailyGoal(), store.weekStartsMonday());
        JSONObject log = store.readingLog();
        JSONObject bookLog = store.bookLog();
        if (hasAccess(c)) {
            List<long[]> sessions = sessions(c, store.readingApps(), 8);
            Map<String, Long> perDay = new HashMap<>();
            Map<String, Long> perBookDay = new HashMap<>();
            JSONObject opened = store.openedAll();
            for (long[] ses : sessions) {
                String book = bookFor(opened, ses[0]);
                split(ses[0], ses[1], (day, ms) -> {
                    perDay.merge(day, ms, Long::sum);
                    if (book != null) perBookDay.merge(book + "|" + day, ms, Long::sum);
                });
            }
            try {
                // Recent days are recomputed exactly; never shrink a day below what was logged before.
                for (Map.Entry<String, Long> e : perDay.entrySet())
                    log.put(e.getKey(), Math.max((int) (e.getValue() / 60000L), log.optInt(e.getKey(), 0)));
                for (Map.Entry<String, Long> e : perBookDay.entrySet())
                    bookLog.put(e.getKey(), Math.max((int) (e.getValue() / 60000L), bookLog.optInt(e.getKey(), 0)));
            } catch (Exception ignored) { }
            store.setReadingLog(log);
            store.setBookLog(bookLog);
        }
        Iterator<String> it = log.keys();
        while (it.hasNext()) {
            String k = it.next();
            s.minutes.put(k, log.optInt(k, 0));
        }
        Iterator<String> bt = bookLog.keys();
        while (bt.hasNext()) {
            String k = bt.next();
            int bar = k.lastIndexOf('|');
            if (bar > 0) s.bookMinutes.merge(k.substring(0, bar), bookLog.optInt(k, 0), Integer::sum);
        }
        return s;
    }

    /** The book most recently opened from Ink Home before this session started. */
    private static String bookFor(JSONObject opened, long sessionStart) {
        String best = null;
        long bestAt = 0;
        Iterator<String> it = opened.keys();
        while (it.hasNext()) {
            String path = it.next();
            long at = opened.optLong(path, 0);
            if (at <= sessionStart + 60_000L && at > bestAt && sessionStart - at < BOOK_LINK_WINDOW) {
                best = path;
                bestAt = at;
            }
        }
        return best;
    }

    private static List<long[]> sessions(Context c, Set<String> pkgs, int days) {
        List<long[]> out = new ArrayList<>();
        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        if (usm == null) return out;
        Calendar start = startOfDay(System.currentTimeMillis());
        start.add(Calendar.DAY_OF_YEAR, -(days - 1));
        long now = System.currentTimeMillis();
        UsageEvents ev = usm.queryEvents(start.getTimeInMillis(), now);
        UsageEvents.Event e = new UsageEvents.Event();
        String openPkg = null;
        long openAt = 0;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            int type = e.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (openPkg != null) out.add(new long[]{openAt, e.getTimeStamp()});
                openPkg = pkgs.contains(e.getPackageName()) ? e.getPackageName() : null;
                openAt = e.getTimeStamp();
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                if (openPkg != null && openPkg.equals(e.getPackageName())) {
                    out.add(new long[]{openAt, e.getTimeStamp()});
                    openPkg = null;
                }
            } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                if (openPkg != null) out.add(new long[]{openAt, e.getTimeStamp()});
                openPkg = null;
            }
        }
        if (openPkg != null) out.add(new long[]{openAt, now});
        return out;
    }

    private interface DayChunk { void add(String day, long ms); }

    /** Splits a session across midnight. Sessions over 4 hours are treated as the screen left on. */
    private static void split(long from, long to, DayChunk sink) {
        if (to <= from) return;
        to = Math.min(to, from + SESSION_MAX);
        while (from < to) {
            Calendar next = startOfDay(from);
            next.add(Calendar.DAY_OF_YEAR, 1);
            long end = Math.min(to, next.getTimeInMillis());
            sink.add(key(from), end - from);
            from = end;
        }
    }

    private static Calendar startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    // ---------------------------------------------------------------- queries

    static String key(long t) {
        synchronized (KEY) { return KEY.format(new Date(t)); }
    }

    int on(Calendar day) {
        Integer m = minutes.get(key(day.getTimeInMillis()));
        return m == null ? 0 : m;
    }

    int today() { return on(Calendar.getInstance()); }

    int forBook(String path) {
        Integer m = bookMinutes.get(path);
        return m == null ? 0 : m;
    }

    int total() {
        int t = 0;
        for (int m : minutes.values()) t += m;
        return t;
    }

    /** A day counts toward a streak when you read at all (or met the goal, if one is set). */
    private boolean counts(Calendar d) {
        int m = on(d);
        return hasGoal() ? m >= goal : m > 0;
    }

    int currentStreak() {
        Calendar d = Calendar.getInstance();
        if (!counts(d)) d.add(Calendar.DAY_OF_YEAR, -1);
        int n = 0;
        while (counts(d)) {
            n++;
            d.add(Calendar.DAY_OF_YEAR, -1);
        }
        return n;
    }

    int bestStreak() {
        if (minutes.isEmpty()) return 0;
        String first = null;
        for (String k : minutes.keySet()) if (first == null || k.compareTo(first) < 0) first = k;
        Calendar d = Calendar.getInstance();
        try {
            synchronized (KEY) { d.setTime(KEY.parse(first)); }
        } catch (Exception e) {
            return currentStreak();
        }
        Calendar end = Calendar.getInstance();
        int best = 0, run = 0;
        while (!d.after(end)) {
            if (counts(d)) best = Math.max(best, ++run); else run = 0;
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        return best;
    }

    Calendar[] week() {
        Calendar d = Calendar.getInstance();
        int first = mondayFirst ? Calendar.MONDAY : Calendar.SUNDAY;
        while (d.get(Calendar.DAY_OF_WEEK) != first) d.add(Calendar.DAY_OF_YEAR, -1);
        Calendar[] out = new Calendar[7];
        for (int i = 0; i < 7; i++) {
            out[i] = (Calendar) d.clone();
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    int weekTotal() {
        int t = 0;
        for (Calendar c : week()) t += on(c);
        return t;
    }

    int monthTotal(Calendar month) {
        Calendar d = (Calendar) month.clone();
        d.set(Calendar.DAY_OF_MONTH, 1);
        int days = d.getActualMaximum(Calendar.DAY_OF_MONTH), t = 0;
        for (int i = 0; i < days; i++) {
            t += on(d);
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        return t;
    }

    int readingDaysInMonth(Calendar month) {
        Calendar d = (Calendar) month.clone();
        d.set(Calendar.DAY_OF_MONTH, 1);
        int days = d.getActualMaximum(Calendar.DAY_OF_MONTH), n = 0;
        for (int i = 0; i < days; i++) {
            if (counts(d)) n++;
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        return n;
    }

    static String hm(int minutes) {
        if (minutes < 60) return minutes + " min";
        int m = minutes % 60;
        return (minutes / 60) + " h" + (m == 0 ? "" : " " + m + " min");
    }
}

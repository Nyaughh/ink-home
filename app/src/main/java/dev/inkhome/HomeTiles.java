package dev.inkhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Built-in tiles. Width and height are the number of occupied home grid cells. */
final class HomeTiles {
    private final MainActivity a;
    private final Ink ink;
    private final int w, h;
    private final LinearLayout tile;

    static View build(MainActivity a, String kind, int w, int h) {
        HomeTiles renderer = new HomeTiles(a, w, h);
        renderer.fill(kind);
        return renderer.tile;
    }

    private HomeTiles(MainActivity a, int w, int h) {
        this.a = a;
        ink = a.ink;
        this.w = w;
        this.h = h;
        tile = ink.col();
        int pad = ink.dp(w == 1 || h == 1 ? 5 : 10);
        tile.setPadding(pad, pad, pad, pad);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(ink.card(0, false));
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
            case "moon": moon(); break;
            default: heading(a.getString(R.string.tile_unavailable));
        }
    }

    private TextView label(String s, TypeScale role, int lines) {
        TextView text = ink.lines(ink.text(s, role, ink.TEXT), lines);
        if (w == 1 && lines == 1 && role.size >= TypeScale.TITLE_SM.size) {
            int min = Math.round(text.getTextSize() * 0.72f);
            int max = Math.round(text.getTextSize());
            if (min > 0 && max > min) text.setAutoSizeTextTypeUniformWithConfiguration(min, max, 1,
                    android.util.TypedValue.COMPLEX_UNIT_PX);
        }
        return text;
    }

    private void heading(String s) {
        tile.addView(ink.oneLine(ink.text(s, TypeScale.LABEL, ink.TEXT2)));
    }

    private void action(Runnable click) {
        tile.setBackground(ink.card(0, true));
        tile.setOnClickListener(v -> click.run());
    }

    private static LinearLayout.LayoutParams remaining() {
        return new LinearLayout.LayoutParams(-1, 0, 1);
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
        List<Library.Book> books = orderedBooks();
        if (books.isEmpty() || !a.hasFileAccess()) {
            emptyBook();
            return;
        }
        Library.Book b = books.get(0);
        if (h > 1) heading(a.getString(b.started() ? R.string.launcher_continue_reading : R.string.launcher_newest_in_your_library));
        LinearLayout row = ink.row();
        tile.addView(row, remaining());
        if (w >= 2) {
            int width = h >= 3 && w >= 3 ? 100 : h >= 2 ? 58 : 32;
            row.addView(ink.cover(Covers.get(a, b, a.store.realCovers())),
                    ink.m(ink.dp(width), ink.dp(width * 1.42f), 0, 0, 9, 0));
        }
        LinearLayout info = ink.col();
        row.addView(info, ink.weight(1));
        info.addView(label(b.title, h >= 3 && w >= 3 ? TypeScale.TITLE : h > 1 && w >= 2 ? TypeScale.TITLE_SM : TypeScale.LABEL, h >= 2 ? 3 : 1));
        if (h >= 2 && !b.author.isEmpty()) info.addView(label(b.author, TypeScale.BYLINE, 1));
        if (b.progress >= 0) info.addView(ink.progress(b.finished ? 1 : b.progress, 3), ink.m(-1, -2, 0, 4, 0, 3));
        info.addView(label(a.bookStatus(b), TypeScale.META, 1));
        action(() -> a.openBook(b));
        tile.setOnLongClickListener(v -> { a.bookMenu(b); return true; });
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
        int count = Math.min(books.size() - 1, h);
        for (int i = 1; i <= count; i++) {
            Library.Book book = books.get(i);
            if (w >= 2 && h >= 2) tile.addView(a.homeBookRow(book), remaining());
            else {
                TextView item = label(book.title, TypeScale.BODY, 2);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setOnClickListener(v -> a.openBook(book));
                tile.addView(item, remaining());
            }
        }
    }

    private void apps() {
        List<MainActivity.App> favorites = a.favoriteApps();
        if (h > 1 || favorites.isEmpty()) heading(a.getString(R.string.launcher_favorites));
        if (favorites.isEmpty()) {
            tile.addView(label(a.getString(R.string.launcher_empty_favorites_hint), TypeScale.META, 3));
            action(() -> a.go(MainActivity.Screen.APPS));
            return;
        }
        int count = Math.min(w * Math.max(1, h - 1), favorites.size());
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % w == 0) {
                row = ink.row();
                tile.addView(row, remaining());
            }
            MainActivity.App app = favorites.get(i);
            LinearLayout cell = ink.col();
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(ink.pressable());
            row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
            if (a.store.appIcons()) cell.addView(a.appIcon(app, h == 1 ? 22 : 32));
            cell.addView(ink.center(label(a.displayLabel(app), TypeScale.LABEL, 1)), -1, -2);
            cell.setOnClickListener(v -> a.launch(app));
            cell.setOnLongClickListener(v -> { a.appMenu(app); return true; });
        }
    }

    private void clock() {
        TextView time = label("", w > 1 ? TypeScale.DISPLAY : TypeScale.TITLE_MD, 1);
        LinearLayout face = wideStrip() ? ink.row() : ink.col();
        tile.addView(face);
        face.addView(time, wideStrip() ? ink.weight(1) : new LinearLayout.LayoutParams(-1, -2));
        TextView date = label("", TypeScale.META, 1);
        if (w > 1 || h > 1) face.addView(date);
        else time.setGravity(Gravity.CENTER);
        observe(() -> {
            Date now = new Date();
            String value = a.timeText(now);
            if (!value.contentEquals(time.getText())) time.setText(value);
            String day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(now);
            if (!day.contentEquals(date.getText())) date.setText(day);
        }, Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
        action(() -> a.start(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
    }

    private boolean wideStrip() { return w >= 3 && h == 1; }

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
            numbers.addView(label(Stats.hm(a, today), w == 1 ? TypeScale.TITLE_SM : TypeScale.FIGURE, 1));
            if (target > 0 && w > 1) numbers.addView(label(a.getString(R.string.stats_ui_goal_minutes, target),
                    TypeScale.META, 1), ink.m(-2, -2, 8, 0, 0, 0));
            if (target > 0) tile.addView(ink.progress((float) today / target, 4), ink.m(-1, -2, 0, 4, 0, 0));
        }
        action(() -> { a.statsTab = "overview"; a.go(MainActivity.Screen.STATS); });
    }

    private void battery() {
        TextView percent = label("", w > 1 ? TypeScale.FIGURE : TypeScale.TITLE_MD, 1);
        TextView status = label("", TypeScale.META, 1);
        LinearLayout body = ink.row();
        tile.addView(body);
        if (w > 1) {
            int size = h == 1 ? 22 : 32;
            body.addView(ink.icon("battery", size), ink.m(ink.dp(size), ink.dp(size), 0, 0, 8, 0));
        }
        LinearLayout value = wideStrip() ? ink.row() : ink.col();
        body.addView(value, ink.weight(1));
        value.addView(percent, wideStrip() ? ink.weight(1) : new LinearLayout.LayoutParams(-1, -2));
        value.addView(status);
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
                    : today.format(DateTimeFormatter.ofPattern(w == 1 ? "LLL" : "LLLL yyyy", locale)));
            if (activity && (!Stats.hasAccess(a) || a.stats == null)) {
                tile.addView(label(a.getString(!Stats.hasAccess(a) ? R.string.stats_ui_turn_on_reading_time
                        : R.string.stats_ui_loading_reading_time), TypeScale.META, 3));
                return;
            }
            DayOfWeek first = a.store.weekStartsMonday() ? DayOfWeek.MONDAY : DayOfWeek.SUNDAY;
            if (wideStrip()) {
                LinearLayout week = ink.row();
                tile.addView(week, remaining());
                LocalDate weekStart = today.minusDays(Math.floorMod(today.getDayOfWeek().getValue() - first.getValue(), 7));
                for (int i = 0; i < 7; i++) {
                    LocalDate date = weekStart.plusDays(i);
                    String weekday = date.getDayOfWeek().getDisplayName(TextStyle.NARROW, locale);
                    TextView cell = label(weekday + " " + date.getDayOfMonth(), TypeScale.LABEL, 1);
                    cell.setGravity(Gravity.CENTER);
                    week.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
                    markDay(cell, date, activity, today);
                }
                return;
            }
            if (w < 2 || h < 2) {
                String summary = activity ? Stats.hm(a, a.stats.monthTotal(Calendar.getInstance()))
                        : today.format(DateTimeFormatter.ofPattern("EEE d", locale));
                tile.addView(label(summary, w == 1 ? TypeScale.TITLE_SM : TypeScale.TITLE, 1));
                return;
            }
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
                tile.addView(week, remaining());
                for (int day = start; day < start + 7; day++) {
                    boolean inMonth = day > 0 && day <= month.lengthOfMonth();
                    TextView cell = label(inMonth ? String.valueOf(day) : "", TypeScale.CHART, 1);
                    cell.setGravity(Gravity.CENTER);
                    week.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
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

    private void moon() {
        SharedPreferences prefs = a.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
        String[] phases = a.getResources().getStringArray(R.array.tile_moon_phases);
        boolean narrow = w == 1;
        MoonDisc disc = new MoonDisc(a, ink);
        LinearLayout row = ink.row();
        tile.addView(row);
        int size = ink.dp(narrow ? 16 : h == 1 ? 24 : 44);
        row.addView(disc, ink.m(size, size, 0, 0, narrow ? 3 : 8, 0));
        TextView phase = label("", narrow ? TypeScale.META : TypeScale.TITLE_SM, narrow && h > 1 ? 2 : 1);
        row.addView(phase, ink.weight(1));
        LinearLayout events = w >= 3 ? ink.row() : ink.col();
        tile.addView(events);
        TextView rise = label("", TypeScale.META, 1);
        TextView set = label("", TypeScale.META, 1);
        events.addView(rise);
        events.addView(set, ink.m(-2, -2, w >= 3 ? 12 : 0, 0, 0, 0));
        observe(() -> {
            // Mean lunar age relative to the new moon of 2000-01-06 18:14 UTC.
            double cycles = (System.currentTimeMillis() - 947182440000L) / (29.530588853 * 86400000);
            disc.phase = cycles - Math.floor(cycles);
            disc.invalidate();
            String phaseName = phases[(int) Math.floor(disc.phase * 8 + 0.5) % 8];
            phase.setText(phaseName);
            if (!prefs.contains("moonLatitude") || !prefs.contains("moonLongitude")) {
                rise.setText(R.string.tile_set_location);
                set.setVisibility(View.GONE);
                tile.setContentDescription(phaseName + ", " + rise.getText());
                return;
            }
            ZoneId zone = ZoneId.systemDefault();
            MoonTimes times = MoonTimes.on(LocalDate.now(zone), zone,
                    prefs.getFloat("moonLatitude", 0), prefs.getFloat("moonLongitude", 0));
            set.setVisibility(View.VISIBLE);
            rise.setText(eventTime(times.rise, true, narrow));
            set.setText(eventTime(times.set, false, narrow));
            tile.setContentDescription(phaseName + ", " + eventTime(times.rise, true, false)
                    + ", " + eventTime(times.set, false, false));
        }, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
        action(() -> editLocation(prefs));
    }

    private String eventTime(Instant time, boolean rise, boolean compact) {
        if (!compact && time == null) return a.getString(rise ? R.string.tile_no_rise : R.string.tile_no_set);
        String text = time == null ? a.getString(R.string.tile_moon_missing)
                : android.text.format.DateFormat.getTimeFormat(a).format(Date.from(time.plusSeconds(30)));
        int format = compact ? (rise ? R.string.tile_moonrise_short : R.string.tile_moonset_short)
                : (rise ? R.string.tile_moonrise : R.string.tile_moonset);
        return a.getString(format, text);
    }

    private void editLocation(SharedPreferences prefs) {
        LinearLayout fields = ink.col();
        fields.addView(label(a.getString(R.string.tile_coordinates_hint, ZoneId.systemDefault().getId()), TypeScale.BODY, 6));
        EditText latitude = coordinate(fields, R.string.tile_latitude, prefs, "moonLatitude");
        EditText longitude = coordinate(fields, R.string.tile_longitude, prefs, "moonLongitude");
        a.sheets.form(a.getString(R.string.tile_location), fields, () -> {
            Float lat = coordinateValue(latitude, 90);
            if (lat == null) return;
            Float lon = coordinateValue(longitude, 180);
            if (lon == null) return;
            prefs.edit().putFloat("moonLatitude", lat).putFloat("moonLongitude", lon).apply();
            a.sheets.dismiss();
            a.render();
        }, latitude);
    }

    private EditText coordinate(LinearLayout fields, int name, SharedPreferences prefs, String key) {
        TextView caption = label(a.getString(name), TypeScale.LABEL, 1);
        fields.addView(caption, ink.m(-1, -2, 0, 12, 0, 0));
        EditText field = new EditText(a);
        field.setId(View.generateViewId());
        caption.setLabelFor(field.getId());
        field.setTextColor(ink.TEXT);
        field.setTypeface(ink.REGULAR);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        if (prefs.contains(key)) field.setText(Float.toString(prefs.getFloat(key, 0)));
        fields.addView(field);
        return field;
    }

    private Float coordinateValue(EditText field, int max) {
        try {
            float value = Float.parseFloat(field.getText().toString().trim().replace(',', '.'));
            if (Math.abs(value) <= max) return value;
        } catch (NumberFormatException ignored) { }
        field.requestFocus();
        field.setError(a.getString(R.string.tile_coordinate_range, max));
        return null;
    }

    private static final class MoonDisc extends View {
        private final Ink ink;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path light = new Path();
        double phase;

        MoonDisc(Context context, Ink ink) {
            super(context);
            this.ink = ink;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float x = getWidth() / 2f, y = getHeight() / 2f;
            float r = Math.max(0, Math.min(x, y) - ink.folio.hairline);
            paint.setColor(ink.TEXT);
            canvas.drawCircle(x, y, r, paint);
            float inner = Math.max(0, r - ink.folio.hairline);
            canvas.save();
            canvas.translate(x, y);
            if (phase > 0.5) canvas.scale(-1, 1);
            float terminator = (float) Math.cos(phase * 2 * Math.PI);
            light.reset();
            light.arcTo(-inner, -inner, inner, inner, -90, 180, true);
            float axis = Math.abs(terminator) * inner;
            light.arcTo(-axis, -inner, axis, inner, 90, terminator > 0 ? -180 : 180, false);
            light.close();
            paint.setColor(ink.PAPER);
            canvas.drawPath(light, paint);
            canvas.restore();
        }
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

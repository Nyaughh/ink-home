package dev.inkhome;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.view.View;
import android.widget.LinearLayout;

import dev.inkhome.folio.widget.FolioSwitch;
import dev.inkhome.folio.widget.Stepper;
import java.util.Collections;
import java.util.function.IntConsumer;

final class HomeSettings {
    private final MainActivity host;
    private final HomeSections sections;

    HomeSettings(MainActivity host) {
        this.host = host;
        sections = HomeSections.load(host);
    }

    void rows(MainActivity.Rows rows) {
        boolean hasWidgets = false;
        for (int i = 0; i < sections.order.size(); i++) {
            String key = sections.order.get(i);
            section(rows, key, i);
            if (HomeSections.widgetId(key) > 0) hasWidgets = true;
        }
        rows.add(host.getString(R.string.home_widget_add), "", () -> host.homeWidgets.pick(id -> {
            HomeSections latest = HomeSections.load(host);
            String key = "widget:" + id;
            latest.order.add(key);
            latest.visible.add(key);
            latest.save(host);
            host.render();
        }));
        if (hasWidgets) rows.add(host.getString(R.string.home_widget_redraw_limit), "", host.homeWidgets::settings);
        rows.segments(host.getString(R.string.home_settings_apps_layout),
                host.getResources().getStringArray(R.array.home_settings_orientations), sections.verticalApps ? 1 : 0,
                index -> { sections.verticalApps = index == 1; sections.save(host); });
        count(rows, R.string.home_settings_app_count, sections.appCount, 12, n -> sections.appCount = n);
        count(rows, R.string.home_settings_next_count, sections.nextCount, 12, n -> sections.nextCount = n);
        rows.toggle(host.getString(R.string.home_settings_compact), sections.compact,
                on -> { sections.compact = on; sections.save(host); });
        rows.toggle(host.getString(R.string.home_settings_borders), sections.borders,
                on -> { sections.borders = on; sections.save(host); });
    }

    private void section(MainActivity.Rows rows, String key, int index) {
        int widgetId = HomeSections.widgetId(key);
        String label = widgetId == -1 ? builtInLabel(key) : widgetLabel(widgetId);
        LinearLayout row = host.ink.row();
        FolioSwitch visibility = new FolioSwitch(host.ink.folio, host.getString(R.string.home_settings_show, label),
                sections.visible.contains(key), shown -> {
                    if (shown) sections.visible.add(key);
                    else sections.visible.remove(key);
                    sections.save(host);
                });
        View title = host.ink.body(label);
        title.setOnClickListener(v -> visibility.performClick());
        row.addView(title, host.ink.weight(1));
        row.addView(visibility);
        moveButton(row, label, index, -1);
        moveButton(row, label, index, 1);
        if (widgetId != -1) {
            String remove = host.getString(R.string.home_settings_remove, label);
            row.addView(Stepper.button(host.ink.folio, "close", remove, () -> {
                sections.visible.remove(key);
                sections.order.remove(key);
                sections.save(host);
                host.homeWidgets.remove(widgetId);
                host.render();
            }), host.ink.dp(44), host.ink.dp(48));
        }
        rows.append(row);
    }

    private void moveButton(LinearLayout row, String label, int index, int delta) {
        String description = host.getString(delta < 0 ? R.string.home_settings_earlier : R.string.home_settings_later, label);
        int to = index + delta;
        View button = Stepper.button(host.ink.folio, delta < 0 ? "up" : "down", description, () -> {
            Collections.swap(sections.order, index, to);
            sections.save(host);
            host.render();
        });
        if (to < 0 || to >= sections.order.size()) button.setVisibility(View.INVISIBLE);
        row.addView(button, host.ink.dp(44), host.ink.dp(48));
    }

    private void count(MainActivity.Rows rows, int title, int selected, int maximum, IntConsumer change) {
        String[] values = new String[maximum + 1];
        values[0] = host.getString(R.string.home_settings_auto);
        for (int i = 1; i < values.length; i++) values[i] = String.valueOf(i);
        String label = host.getString(title);
        rows.control(label, new Stepper(host.ink.folio, label, values, selected, i -> {
            change.accept(i);
            sections.save(host);
        }), false);
    }

    private String builtInLabel(String key) {
        switch (key) {
            case "current": return host.getString(R.string.settings_ui_current_book);
            case "next": return host.getString(R.string.launcher_up_next);
            case "apps": return host.getString(R.string.home_settings_apps);
            case "clock": return host.getString(R.string.settings_ui_clock);
            case "calendar": return host.getString(R.string.stats_ui_calendar);
            case "goal": return host.getString(R.string.home_settings_goal);
            case "month": return host.getString(R.string.home_settings_month);
            case "battery": return host.getString(R.string.home_settings_battery);
            default: return key;
        }
    }

    private String widgetLabel(int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(host).getAppWidgetInfo(id);
        if (info != null) {
            String label = info.loadLabel(host.getPackageManager());
            if (label != null) return label;
            return info.provider.getShortClassName();
        }
        return host.getString(R.string.home_widget_unavailable);
    }
}

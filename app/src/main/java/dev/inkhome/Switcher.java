package dev.inkhome;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.format.Formatter;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.widget.Button;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared content for the panel switcher and Apps > Recent. */
final class Switcher {
    static void show(MainActivity host, LinearLayout page, List<MainActivity.App> candidates, String query) {
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), 0);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        List<Library.Book> reading = host.reading();
        if (!reading.isEmpty() && reading.get(0).title.toLowerCase(Locale.getDefault()).contains(query)) {
            body.addView(host.homeBookRow(reading.get(0)));
        }
        if (!Stats.hasAccess(host)) {
            body.addView(host.note("Allow usage access", "Show recently opened apps and when you used them.",
                    () -> host.start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
            return;
        }
        TextView loading = host.ink.muted("Loading recent apps…", 15);
        body.addView(loading);
        List<MainActivity.App> apps = new ArrayList<>(candidates);
        Set<String> hidden = host.store.hidden();
        host.io.execute(() -> {
            long now = System.currentTimeMillis();
            long start = now - 7L * 86400000L;
            UsageStatsManager usage = host.getSystemService(UsageStatsManager.class);
            Map<String, UsageStats> records;
            try {
                records = usage == null ? null : usage.queryAndAggregateUsageStats(start, now);
            } catch (SecurityException denied) {
                records = null;
            }
            final Map<String, UsageStats> times = records == null ? Collections.emptyMap() : records;
            SharedPreferences dismissed = host.getSharedPreferences("switcherDismissals", Context.MODE_PRIVATE);
            Set<String> seen = new HashSet<>();
            apps.removeIf(app -> {
                UsageStats record = times.get(app.pkg);
                return hidden.contains(app.key) || record == null || record.getLastTimeUsed() < start
                        || record.getLastTimeUsed() <= dismissed.getLong(app.pkg, 0) || !seen.add(app.pkg);
            });
            apps.sort((a, b) -> Long.compare(times.get(b.pkg).getLastTimeUsed(), times.get(a.pkg).getLastTimeUsed()));
            host.ui.post(() -> {
                if (body.getRootView() != host.getWindow().getDecorView()) return;
                body.removeView(loading);
                populate(host, body, apps, times, dismissed);
            });
        });
    }

    private static void populate(MainActivity host, LinearLayout body, List<MainActivity.App> apps,
                                 Map<String, UsageStats> times, SharedPreferences dismissed) {
        ActivityManager manager = host.getSystemService(ActivityManager.class);
        boolean canClose = manager != null && Build.VERSION.SDK_INT < 34;
        if (!apps.isEmpty() && canClose) body.addView(Button.create(host.ink.folio, "Close all", Button.Style.TEXT,
                v -> close(host, manager, apps, times, dismissed)));
        LinearLayout rows = host.ink.col();
        if (apps.isEmpty()) rows.addView(host.ink.muted("No recent apps.", 15));
        for (MainActivity.App app : apps) {
            LinearLayout row = host.ink.row();
            row.setMinimumHeight(host.ink.dp(52));
            row.setBackground(host.ink.listRow(0));
            if (host.store.appIcons()) row.addView(host.appIcon(app, 30),
                    host.ink.m(host.ink.dp(30), host.ink.dp(30), 0, 0, 12, 0));
            LinearLayout label = host.ink.col();
            label.addView(host.ink.oneLine(host.ink.body(host.displayLabel(app))));
            label.addView(host.ink.faint(Library.ago(times.get(app.pkg).getLastTimeUsed()), 13));
            row.addView(label, host.ink.weight(1));
            row.setOnClickListener(v -> host.launch(app));
            row.setOnLongClickListener(v -> { host.appMenu(app); return true; });
            if (canClose) {
                android.view.View close = host.ink.iconButton("close",
                        () -> close(host, manager, Collections.singletonList(app), times, dismissed));
                close.setContentDescription("Close " + host.displayLabel(app));
                row.addView(close);
            }
            rows.addView(MainActivity.item(row));
        }
        body.addView(host.scrollable(rows), new LinearLayout.LayoutParams(-1, 0, 1));
        if (manager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            body.addView(host.ink.faint(Formatter.formatFileSize(host, memory.availMem) + " available memory", 13));
        }
    }

    private static void close(MainActivity host, ActivityManager manager, List<MainActivity.App> apps,
                              Map<String, UsageStats> times, SharedPreferences dismissed) {
        SharedPreferences.Editor editor = dismissed.edit();
        try {
            for (MainActivity.App app : apps) {
                manager.killBackgroundProcesses(app.pkg);
                editor.putLong(app.pkg, times.get(app.pkg).getLastTimeUsed());
            }
        } catch (SecurityException denied) {
            host.toast("Android could not close the app");
        }
        editor.apply();
        host.render();
    }
}

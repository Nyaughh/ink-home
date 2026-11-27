package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.core.TypeScale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AppsScreen implements Screen {
    private String query = "";
    private int tab;
    private boolean grid;

    boolean showingRecent(MainActivity host) {
        return "switcher".equals(host.sub) || (host.sub == null && tab == 1);
    }

    @Override public void build(MainActivity host) {
        if ("switcher".equals(host.sub)) {
            LinearLayout page = host.page();
            page.addView(host.header("Switcher", null, host::onBackPressed));
            Switcher.show(host, page, host.apps, "");
            return;
        }
        if (host.sub != null && !"hidden".equals(host.sub)) { new AppPickerScreen().build(host); return; }
        if ("hidden".equals(host.sub)) { tab = 2; host.sub = null; }
        LinearLayout col = host.page();
        Set<String> hidden = host.store.hidden();
        List<MainActivity.App> apps = new ArrayList<>();
        String filter = query.trim().toLowerCase(Locale.getDefault());
        for (MainActivity.App app : host.apps) {
            if (hidden.contains(app.key) == (tab == 2)
                    && host.displayLabel(app).toLowerCase(Locale.getDefault()).contains(filter)) apps.add(app);
        }
        ImageView search = host.ink.iconButton("search", () -> host.searchApps(query, value -> {
            query = value.trim(); reset(host);
        }));
        search.setContentDescription("Search apps");
        ImageView layout = host.ink.iconButton(grid ? "list" : "grid", () -> { grid = !grid; reset(host); });
        layout.setContentDescription(grid ? "List view" : "Grid view");
        View[] actions = tab == 1 ? new View[]{search} : new View[]{search, layout};
        LinearLayout header = host.header("Apps", tab == 1 ? null : String.valueOf(apps.size()), null, actions);
        col.addView(header);
        if (!query.isEmpty()) {
            TextView clear = host.ink.pill(query + "  ×", false);
            clear.setContentDescription("Clear search for " + query);
            clear.setOnClickListener(v -> { query = ""; reset(host); });
            col.addView(clear, host.ink.m(-2, -2, MainActivity.PAD, 0, 0, 0));
        }
        col.addView(host.tabs(new String[]{"All", "Recent", "Hidden"}, tab, i -> { tab = i; reset(host); }),
                host.ink.m(-1, -2, MainActivity.PAD, 0, MainActivity.PAD, 0));
        if (tab == 1) { Switcher.show(host, col, apps, filter); return; }
        if (tab == 2 && apps.stream().anyMatch(MainActivity::booxShellApp)) {
            TextView group = host.ink.pill(SettingsScreen.BOOX, false);
            group.setOnClickListener(v -> host.openSettingsPage(SettingsScreen.BOOX));
            col.addView(group, host.ink.m(-2, -2, MainActivity.PAD, 0, 0, 0));
            apps.removeIf(MainActivity::booxShellApp);
            if (apps.isEmpty()) return;
        }
        LinearLayout body = host.ink.row();
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        show(host, body, apps);
    }

    private void reset(MainActivity host) {
        host.pages.put("list:APPS:null", 0);
        host.pages.put("appsGrid", 0);
        host.render();
    }

    private void show(MainActivity host, LinearLayout body, List<MainActivity.App> apps) {
        body.setGravity(Gravity.TOP);
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(8), 0);
        if (apps.isEmpty()) {
            body.addView(host.ink.muted(!query.isEmpty() ? "No matches." : tab == 2 ? "No hidden apps."
                    : tab == 1 ? "No recent apps." : "No apps available.", 15));
            return;
        }
        if (grid) {
            body.addView(host.appGrid(apps.size(), i -> entry(host, apps.get(i), true, false)), -1, -1);
            return;
        }
        LinearLayout list = host.ink.col();
        Map<Character, View> initials = new LinkedHashMap<>();
        Set<String> reading = host.store.readingApps();
        for (MainActivity.App app : apps) {
            View row = entry(host, app, false, reading.contains(app.pkg));
            list.addView(MainActivity.item(row), -1, -2);
            String name = host.displayLabel(app);
            char initial = name.isEmpty() ? '#' : Character.toUpperCase(name.charAt(0));
            initials.putIfAbsent(Character.isLetter(initial) ? initial : '#', row);
        }
        body.addView(host.scrollable(list), new LinearLayout.LayoutParams(0, -1, 1));
        if (tab == 1 || initials.size() <= 3) return;
        MainActivity.Jumper jump = host.jumper;
        LinearLayout rail = host.ink.col();
        for (Map.Entry<Character, View> letter : initials.entrySet()) {
            TextView t = host.ink.center(host.ink.text(letter.getKey().toString(), TypeScale.LABEL, host.ink.TEXT));
            t.setOnClickListener(v -> jump.to(letter.getValue()));
            rail.addView(t, new LinearLayout.LayoutParams(host.ink.dp(28), 0, 1));
        }
        body.addView(rail, -2, -1);
    }

    private View entry(MainActivity host, MainActivity.App app, boolean tile, boolean reading) {
        LinearLayout box = tile ? host.ink.col() : host.ink.row();
        box.setMinimumHeight(host.ink.dp(tile ? 88 : 52));
        box.setGravity(tile ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        box.setBackground(tile ? host.ink.pressable() : host.ink.listRow(0));
        if (host.store.appIcons()) {
            int size = host.ink.dp(tile ? 36 : 30);
            box.addView(host.appIcon(app, tile ? 36 : 30), host.ink.m(size, size, 0, 0, tile ? 0 : 12, 0));
        }
        TextView label = host.ink.oneLine(host.ink.body(host.displayLabel(app)));
        if (tile) box.addView(label); else box.addView(label, host.ink.weight(1));
        if (!tile && reading) {
            TextView tag = host.ink.faint("Reading", 12);
            tag.setPadding(host.ink.dp(4), 0, host.ink.dp(4), 0);
            tag.setBackground(host.ink.stroke(host.ink.PAPER, 0, host.ink.TEXT));
            box.addView(tag);
        }
        if (!tile && host.store.isFavorite(app.key)) box.addView(host.ink.icon("star", 16));
        box.setOnClickListener(v -> {
            if (tab == 2) { host.store.setHidden(app.key, false); host.render(); }
            else host.launch(app);
        });
        if (tab == 2) box.setContentDescription("Show " + host.displayLabel(app) + " in Apps");
        box.setOnLongClickListener(v -> { host.appMenu(app); return true; });
        return box;
    }
}

package dev.inkhome;

import dev.inkhome.folio.core.TypeScale;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AppsScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        if (host.sub != null) { new AppPickerScreen().build(host); return; }
        LinearLayout col = host.page();
        Set<String> hidden = host.store.hidden();
        List<MainActivity.App> visible = new ArrayList<>();
        for (MainActivity.App a : host.apps) if (!hidden.contains(a.key)) visible.add(a);
        View[] actions = hidden.isEmpty() ? new View[0] : new View[]{host.ink.pill("Hidden " + hidden.size(), false)};
        if (actions.length > 0) actions[0].setOnClickListener(v -> { host.sub = "hidden"; host.render(); });
        col.addView(host.header("Apps", String.valueOf(visible.size()), null, actions));

        LinearLayout list = host.ink.col();
        list.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(8), host.ink.dp(16));
        Map<Character, View> firstRow = new LinkedHashMap<>();
        Set<String> readingApps = host.store.readingApps();
        list.addView(host.ink.hair());
        for (MainActivity.App a : visible) {
            LinearLayout r = host.ink.row();
            r.setBackground(host.ink.listRow(host.store.appIcons() ? 44 : 0));
            if (host.store.appIcons()) r.addView(host.appIcon(a, 28), host.ink.m(host.ink.dp(28), host.ink.dp(28), 0, 0, 16, 0));
            r.addView(host.ink.oneLine(host.ink.body(host.displayLabel(a))), host.ink.weight(1));
            if (readingApps.contains(a.pkg)) r.addView(host.ink.faint("Reading", 12), host.ink.m(-2, -2, 0, 0, 10, 0));
            if (host.store.isFavorite(a.key)) r.addView(host.ink.icon("star", 14), host.ink.m(host.ink.dp(14), host.ink.dp(14), 0, 0, 8, 0));
            r.setOnClickListener(v -> host.launch(a));
            r.setOnLongClickListener(v -> { host.appMenu(a); return true; });
            list.addView(MainActivity.item(r), new LinearLayout.LayoutParams(-1, host.ink.dp(54)));
            String l = host.displayLabel(a);
            char ch = l.isEmpty() ? '#' : Character.toUpperCase(l.charAt(0));
            if (!Character.isLetter(ch)) ch = '#';
            if (!firstRow.containsKey(ch)) firstRow.put(ch, r);
        }
        TextView hint = host.ink.faint("Long-press an app to add it to favorites, hide it, or count it as reading.", 13);
        hint.setPadding(0, host.ink.dp(16), host.ink.dp(14), 0);
        list.addView(hint);

        View lister = host.scrollable(list);
        MainActivity.Jumper jump = host.jumper;
        LinearLayout body = host.ink.row();
        body.setGravity(Gravity.TOP);
        body.addView(lister, new LinearLayout.LayoutParams(0, -1, 1f));
        if (firstRow.size() > 3) {
            LinearLayout az = host.ink.col();
            az.setPadding(0, 0, host.ink.dp(6), host.ink.dp(8));
            for (Map.Entry<Character, View> e : firstRow.entrySet()) {
                TextView t = host.ink.center(host.ink.text(String.valueOf(e.getKey()), TypeScale.LABEL, host.ink.TEXT3));
                t.setGravity(Gravity.CENTER);
                t.setBackground(host.ink.pressable());
                t.setOnClickListener(v -> jump.to(e.getValue()));
                az.addView(t, new LinearLayout.LayoutParams(host.ink.dp(28), 0, 1f));
            }
            body.addView(az, new LinearLayout.LayoutParams(host.ink.dp(34), -1));
        }
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

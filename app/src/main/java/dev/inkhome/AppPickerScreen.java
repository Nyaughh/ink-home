package dev.inkhome;

import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Set;

final class AppPickerScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        boolean hiddenMode = "hidden".equals(host.sub);
        col.addView(host.header(hiddenMode ? "Hidden apps" : "Reading apps", null, () -> { host.sub = null; host.render(); }));
        LinearLayout list = host.ink.col();
        list.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(16));
        TextView hint = host.ink.muted(hiddenMode ? "Tap an app to show it in Apps again." : "Time in these apps counts as reading. Your book reader always counts.", 14);
        hint.setPadding(0, 0, 0, host.ink.dp(12));
        list.addView(hint);
        list.addView(host.ink.hair());
        Set<String> hidden = host.store.hidden();
        Set<String> reading = host.store.readingApps();
        int shown = 0;
        for (MainActivity.App a : host.apps) {
            if (hiddenMode && !hidden.contains(a.key)) continue;
            shown++;
            LinearLayout r = host.ink.row();
            r.setBackground(host.ink.listRow(host.store.appIcons() ? 44 : 0));
            if (host.store.appIcons()) r.addView(host.appIcon(a, 28), host.ink.m(host.ink.dp(28), host.ink.dp(28), 0, 0, 16, 0));
            r.addView(host.ink.oneLine(host.ink.body(host.displayLabel(a))), host.ink.weight(1));
            if (!hiddenMode) {
                boolean on = reading.contains(a.pkg);
                r.addView(host.ink.pill(on ? "Counts" : "Off", on));
                r.setOnClickListener(v -> {
                    if (a.pkg.equals(MainActivity.NEOREADER_PKG) || a.pkg.equals(host.store.bookReader()) || a.pkg.equals(host.store.pdfReader())) {
                        host.toast("Your book reader always counts");
                        return;
                    }
                    host.store.setReadingApp(a.pkg, !on);
                    host.render();
                });
            } else {
                r.setOnClickListener(v -> { host.store.setHidden(a.key, false); host.render(); });
            }
            list.addView(MainActivity.item(r), new LinearLayout.LayoutParams(-1, host.ink.dp(58)));
        }
        if (shown == 0) {
            TextView t = host.ink.muted("No hidden apps.", 15);
            t.setPadding(0, host.ink.dp(16), 0, 0);
            list.addView(t);
        }
        col.addView(host.scrollable(list), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

package dev.inkhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

class HomeTiles {
    final MainActivity a;
    final Ink ink;
    final HomeLayout.Item item;
    final boolean compact, borders, preview;
    final LinearLayout tile;

    HomeTiles(MainActivity a, HomeLayout.Item item, boolean preview) {
        this.a = a;
        this.item = item;
        this.preview = preview;
        ink = a.ink;
        compact = Boolean.parseBoolean(item.settings.get("compact"));
        borders = Boolean.parseBoolean(item.settings.get("borders"));
        tile = ink.col();
        int pad = ink.dp(borders ? (compact ? 2 : 6) : 0);
        tile.setPadding(pad, pad, pad, pad);
        if (borders) tile.setBackground(ink.card(0, false));
    }

    View content() { return tile; }

    int count() {
        try { return Math.max(0, Integer.parseInt(item.settings.get("count"))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    TextView label(String s, TypeScale role, int lines) {
        return ink.lines(ink.text(s, role, ink.TEXT), lines);
    }

    void heading(String s) {
        tile.addView(ink.oneLine(ink.text(s, TypeScale.LABEL, ink.TEXT2)));
    }

    void action(Runnable click) {
        tile.setBackground(borders ? ink.card(0, true) : ink.pressable());
        tile.setOnClickListener(v -> click.run());
    }

    /** Subscribe only while the tile is visible in the hierarchy. */
    void observe(Runnable update, String... actions) {
        update.run();
        if (preview) return;
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

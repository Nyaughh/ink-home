package dev.inkhome;

import dev.inkhome.folio.widget.LevelBar;
import android.widget.LinearLayout;
import android.widget.TextView;

final class WallpaperScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        build(host, host.page());
    }

    void build(MainActivity host, LinearLayout col) {
        col.addView(host.header(host.getString(R.string.stats_ui_wallpaper), null, host::onBackPressed));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(20));
        boolean has = !host.store.wallpaper().isEmpty();
        TextView intro = host.ink.muted(host.getString(R.string.stats_ui_wallpaper_description), 14);
        intro.setLineSpacing(0, 1.2f);
        body.addView(intro);
        if (has) {
            float[] fades = {0.8f, 0.65f, 0.45f};
            int cur = 1;
            for (int i = 0; i < fades.length; i++) if (Math.abs(fades[i] - host.store.wallpaperFade()) < 0.01f) cur = i;
            String[] levels = {host.getString(R.string.stats_ui_subtle), host.getString(R.string.stats_ui_medium), host.getString(R.string.stats_ui_strong)};
            LinearLayout strength = MainActivity.keep(host.ink.col());
            strength.addView(host.ink.heading(host.getString(R.string.stats_ui_strength_level, levels[cur])));
            strength.addView(MainActivity.item(new LevelBar(host.ink.folio, host.getString(R.string.stats_ui_strength), levels, cur,
                    i -> { host.store.setWallpaperFade(fades[i]); host.render(); })));
            body.addView(strength, host.ink.m(-1, -2, 0, 20, 0, 0));
            TextView remove = host.ink.pill(host.getString(R.string.stats_ui_remove_wallpaper), false);
            remove.setOnClickListener(v -> { host.store.setWallpaper(""); host.render(); });
            LinearLayout r = host.ink.row();
            r.addView(remove);
            body.addView(r, host.ink.m(-1, -2, 0, 14, 0, 0));
        }
        host.pictureGrid(body, host.store.wallpaper(), pic -> { host.store.setWallpaper(pic.getAbsolutePath()); host.toast(host.getString(R.string.stats_ui_wallpaper_set)); host.render(); });
        col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

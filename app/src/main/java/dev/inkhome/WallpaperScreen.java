package dev.inkhome;

import dev.inkhome.folio.widget.LevelBar;
import android.widget.LinearLayout;
import android.widget.TextView;

final class WallpaperScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        col.addView(host.header("Wallpaper", null, host::onBackPressed));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(20));
        boolean has = !host.store.wallpaper().isEmpty();
        TextView intro = host.ink.muted("A picture behind your home screen, in greyscale and faded so text stays sharp.", 14);
        intro.setLineSpacing(0, 1.2f);
        body.addView(intro);
        if (has) {
            float[] fades = {0.8f, 0.65f, 0.45f};
            int cur = 1;
            for (int i = 0; i < fades.length; i++) if (Math.abs(fades[i] - host.store.wallpaperFade()) < 0.01f) cur = i;
            String[] levels = {"Subtle", "Medium", "Strong"};
            LinearLayout strength = MainActivity.keep(host.ink.col());
            strength.addView(host.ink.heading("Strength · " + levels[cur]));
            strength.addView(MainActivity.item(new LevelBar(host.ink.folio, "Strength", levels, cur,
                    i -> { host.store.setWallpaperFade(fades[i]); host.render(); })));
            body.addView(strength, host.ink.m(-1, -2, 0, 20, 0, 0));
            TextView remove = host.ink.pill("Remove wallpaper", false);
            remove.setOnClickListener(v -> { host.store.setWallpaper(""); host.render(); });
            LinearLayout r = host.ink.row();
            r.addView(remove);
            body.addView(r, host.ink.m(-1, -2, 0, 14, 0, 0));
        }
        host.pictureGrid(body, host.store.wallpaper(), pic -> { host.store.setWallpaper(pic.getAbsolutePath()); host.toast("Wallpaper set"); host.render(); });
        col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

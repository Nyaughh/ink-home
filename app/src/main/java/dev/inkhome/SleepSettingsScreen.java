package dev.inkhome;

import dev.inkhome.folio.widget.Segmented;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;

final class SleepSettingsScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        build(host, host.page());
    }

    void build(MainActivity host, LinearLayout col) {
        col.addView(host.header(host.getString(R.string.stats_ui_sleep_screen), null, host::onBackPressed));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(20));
        String mode = host.store.sleepMode();
        String[] keys = {"off", "book", "custom"};
        body.addView(MainActivity.item(new Segmented(host.ink.folio, host.getString(R.string.stats_ui_sleep_screen), new String[]{host.getString(R.string.stats_ui_off), host.getString(R.string.stats_ui_book), host.getString(R.string.stats_ui_picture)}, Arrays.asList(keys).indexOf(mode), i -> {
            host.store.setSleepMode(keys[i]);
            host.sleepSignature = "";
            host.updateSleepPicture("book".equals(keys[i]));
            host.render();
        })));

        if (!mode.equals("off")) {
            LinearLayout prev = host.ink.row();
            prev.setGravity(Gravity.TOP);
            ImageView iv = new ImageView(host);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(1, 1, 1, 1);
            iv.setBackgroundColor(0xFF9A9A9A);
            File f = SleepScreen.file();
            boolean ready = f.exists() && mode.equals(host.store.sleepFileMode());
            SleepSize size = SleepScreen.panel(host);
            float ratio = Math.min(114f / size.width, 154f / size.height);
            int width = Math.max(1, Math.round(size.width * ratio));
            int height = Math.max(1, Math.round(size.height * ratio));
            if (ready) host.thumbs.execute(() -> {
                Bitmap bm = SleepScreen.preview(f, width * 2, height * 2);
                host.ui.post(() -> { if (bm != null) iv.setImageBitmap(bm); });
            });
            prev.addView(iv, new LinearLayout.LayoutParams(host.ink.dp(width), host.ink.dp(height)));
            LinearLayout how = host.ink.col();
            how.setPadding(host.ink.dp(18), 0, 0, 0);
            how.addView(host.ink.medium(ready ? host.getString(R.string.stats_ui_picture_ready) : mode.equals("custom") ? host.getString(R.string.stats_ui_pick_a_picture_below) : host.getString(R.string.stats_ui_making_picture), 16));
            // Boox copies the picture when it's set, so Ink Home can't apply or refresh it itself.
            boolean boox = host.findApp(MainActivity.BOOX_HOME) != null;
            String instructions = boox
                    ? host.getString(R.string.stats_ui_sleep_instructions_boox)
                    : host.getString(R.string.stats_ui_sleep_instructions_generic);
            TextView t = host.ink.muted(instructions, 14);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, host.ink.dp(8), 0, 0);
            how.addView(t);
            prev.addView(how, host.ink.weight(1));
            body.addView(prev, host.ink.m(-1, -2, 0, 20, 0, 0));
        } else {
            TextView t = host.ink.muted(host.getString(R.string.stats_ui_sleep_screen_description), 14);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, host.ink.dp(18), 0, 0);
            body.addView(t);
        }

        if (mode.equals("custom")) {
            host.pictureGrid(body, null, pic -> host.choose(host.getString(R.string.stats_ui_use_this_picture), new String[]{host.getString(R.string.stats_ui_fill_the_screen_crop_edges), host.getString(R.string.stats_ui_show_the_whole_picture)}, -1, w -> {
                host.toast(host.getString(R.string.stats_ui_making_sleep_picture));
                host.writeSleepImage(pic, w == 0, true);
            }));
        }
        col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

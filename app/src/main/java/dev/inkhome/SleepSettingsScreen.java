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
        LinearLayout col = host.page();
        col.addView(host.header("Sleep screen", null, host::onBackPressed));
        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), host.ink.dp(20));
        String mode = host.store.sleepMode();
        String[] keys = {"off", "book", "custom"};
        body.addView(MainActivity.item(new Segmented(host.ink.folio, "Sleep screen", new String[]{"Off", "Book", "Picture"}, Arrays.asList(keys).indexOf(mode), i -> {
            host.store.setSleepMode(keys[i]);
            if (keys[i].equals("book")) { host.sleepSignature = ""; host.updateSleepPicture(true); host.ui.postDelayed(host::render, 900); }
            else host.render();
        })));

        if (!mode.equals("off")) {
            LinearLayout prev = host.ink.row();
            prev.setGravity(Gravity.TOP);
            ImageView iv = new ImageView(host);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setPadding(1, 1, 1, 1);
            iv.setBackgroundColor(0xFF9A9A9A);
            File f = SleepScreen.file();
            boolean ready = f.exists() && mode.equals(host.store.sleepFileMode());
            if (ready) host.thumbs.execute(() -> {
                Bitmap bm = SleepScreen.thumb(f, 228, 308);
                host.ui.post(() -> { if (bm != null) iv.setImageBitmap(bm); });
            });
            prev.addView(iv, new LinearLayout.LayoutParams(host.ink.dp(114), host.ink.dp(154)));
            LinearLayout how = host.ink.col();
            how.setPadding(host.ink.dp(18), 0, 0, 0);
            how.addView(host.ink.medium(ready ? "Picture ready" : mode.equals("custom") ? "Pick a picture below" : "Making picture…", 16));
            // Boox copies the picture when it's set, so Ink Home can't apply or refresh it itself.
            TextView t = host.ink.muted("To use it: in Boox, open Storage › Pictures › Ink Home, long-press “Ink Home sleep” and choose Set as screensaver. "
                    + "Boox keeps its own copy, so set it again whenever you make a new picture.", 14);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, host.ink.dp(8), 0, 0);
            how.addView(t);
            if (ready) {
                TextView open = host.ink.pill("Open Boox", false);
                open.setOnClickListener(v -> { MainActivity.App a = host.findApp(MainActivity.BOOX_HOME); if (a != null) host.launch(a); });
                LinearLayout or = host.ink.row();
                or.addView(open);
                how.addView(or, host.ink.m(-1, -2, 0, 12, 0, 0));
            }
            prev.addView(how, host.ink.weight(1));
            body.addView(prev, host.ink.m(-1, -2, 0, 20, 0, 0));
        } else {
            TextView t = host.ink.muted("Ink Home can make your sleep screen the book you're reading, or any picture on the tablet.", 14);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, host.ink.dp(18), 0, 0);
            body.addView(t);
        }

        if (mode.equals("custom")) {
            host.pictureGrid(body, null, pic -> host.choose("Use this picture", new String[]{"Fill the screen (crop edges)", "Show the whole picture"}, -1, w -> {
                host.toast("Making sleep picture…");
                host.io.execute(() -> {
                    boolean ok = SleepScreen.writeImage(host, pic, w == 0);
                    if (ok) host.store.setSleepFileMode("custom");
                    host.ui.post(() -> { host.toast(ok ? "Sleep picture updated" : "Couldn't read that picture"); host.render(); });
                });
            }));
        }
        col.addView(host.scrollable(body), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

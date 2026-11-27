package dev.inkhome.folio.epd;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

final class FlashEpd implements Epd {
    private View activeFlash;

    @Override public void update(View target, Intent intent) { }

    @Override public void clean(ViewGroup decor, boolean manual) {
        if (!manual || activeFlash != null) return;
        View flash = new View(decor.getContext());
        activeFlash = flash;
        flash.setClickable(true);
        flash.setBackgroundColor(Color.BLACK);
        decor.addView(flash, new ViewGroup.LayoutParams(-1, -1));
        flash.postDelayed(() -> flash.setBackgroundColor(Color.WHITE), 140);
        flash.postDelayed(() -> {
            decor.removeView(flash);
            activeFlash = null;
        }, 300);
    }
}

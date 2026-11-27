package dev.inkhome.folio.epd;

import android.view.View;
import android.view.ViewGroup;

import dev.inkhome.folio.DisplayProbe;

/** The device-specific part of refresh. Android still draws each changed view normally. */
public interface Epd {
    enum Intent { PAGE, IMAGE, FAST }

    void update(View target, Intent intent);
    void clean(ViewGroup decor, boolean manual);

    /** Requests a clean from our attached view without configuring any update mode. */
    static boolean requestFullClean(View view) {
        return view.isAttachedToWindow() && OnyxEpd.requestFullClean(view);
    }

    static Epd forView(View decor) {
        if (!DisplayProbe.displayClass(decor.getContext()).isEink()) return new NoEpd();
        if (OnyxEpd.available()) return new OnyxEpd(DisplayProbe.smoothText(decor.getContext()));
        return new FlashEpd();
    }
}

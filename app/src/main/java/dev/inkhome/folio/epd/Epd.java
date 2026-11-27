package dev.inkhome.folio.epd;

import android.view.View;
import android.view.ViewGroup;

import dev.inkhome.folio.DisplayProbe;

/** The device-specific part of refresh. Android still draws each changed view normally. */
public interface Epd {
    enum Intent { PAGE, IMAGE, FAST }

    void update(View target, Intent intent);
    void clean(ViewGroup decor, boolean manual);

    static Epd forView(View decor) {
        if (!DisplayProbe.displayClass(decor.getContext()).isEink()) return new NoEpd();
        if (OnyxEpd.available()) return new OnyxEpd();
        return new FlashEpd();
    }
}

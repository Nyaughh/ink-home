package dev.inkhome.folio.epd;

import android.view.View;
import android.view.ViewGroup;

final class NoEpd implements Epd {
    @Override public void update(View target, Intent intent) { }
    @Override public void clean(ViewGroup decor, boolean manual) { }
}

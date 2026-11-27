package dev.inkhome.folio.epd;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;

final class OnyxEpd implements Epd {
    private static Method method(String name, Class<?>... parameters) {
        try {
            return View.class.getMethod(name, parameters);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static final Method REFRESH = method("refreshScreen", int.class);
    private static final Method MODE = method("setDefaultUpdateMode", int.class);
    private final FlashEpd fallback = new FlashEpd();
    private final boolean regal = enableRegal();

    static boolean available() {
        return REFRESH != null || MODE != null;
    }

    private static boolean enableRegal() {
        Method supported = method("supportRegal");
        Method enable = method("enableRegal", boolean.class);
        if (supported == null || enable == null) return false;
        try {
            if (!Boolean.TRUE.equals(supported.invoke(null))) return false;
            enable.invoke(null, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override public void update(View target, Intent intent) {
        if (MODE == null) return;
        int mode = 5;
        if (intent == Intent.FAST) mode = 1;
        if (intent == Intent.PAGE && regal) mode = 6;
        try {
            MODE.invoke(target, mode);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // This firmware will draw the view in its own default mode.
        }
    }

    @Override public void clean(ViewGroup decor, boolean manual) {
        if (REFRESH != null) {
            try {
                REFRESH.invoke(decor, 98);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A manual clean still has a visible fallback.
            }
        }
        fallback.clean(decor, manual);
    }
}

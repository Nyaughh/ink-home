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
    private final boolean regal;

    OnyxEpd(boolean smoothText) { regal = configureRegal(smoothText); }

    static boolean available() {
        return REFRESH != null || MODE != null;
    }

    private static boolean configureRegal(boolean enabled) {
        Method supported = method("supportRegal");
        Method enable = method("enableRegal", boolean.class);
        if (supported == null || enable == null) return false;
        try {
            if (!Boolean.TRUE.equals(supported.invoke(null))) return false;
            enable.invoke(null, enabled);
            return enabled;
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

    static boolean requestFullClean(View target) {
        return requestRefresh(target, 98);
    }

    // One-shot firmware request. The panel determines the actual update extent.
    static boolean requestRefresh(View target, int mode) {
        if (REFRESH == null) return false;
        try {
            REFRESH.invoke(target, mode);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override public void clean(ViewGroup decor, boolean manual) {
        if (!requestFullClean(decor)) fallback.clean(decor, manual);
    }
}

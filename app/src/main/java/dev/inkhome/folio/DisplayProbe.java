package dev.inkhome.folio;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;

import dev.inkhome.folio.core.Capabilities;
import dev.inkhome.folio.core.DisplayClass;

import java.io.File;
import java.util.Locale;

public final class DisplayProbe {
    private DisplayProbe() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
    }

    private static boolean hasEpdControl() {
        try {
            View.class.getMethod("refreshScreen", int.class);
            return true;
        } catch (ReflectiveOperationException | SecurityException ignored) { return false; }
    }

    private static boolean supportsRegal() {
        try {
            return Boolean.TRUE.equals(View.class.getMethod("supportRegal").invoke(null));
        } catch (ReflectiveOperationException | SecurityException ignored) { return false; }
    }

    private static boolean colorInkModel(String model) {
        return model.contains("color") || model.contains("colour") || model.contains("kaleido")
                || model.contains("tab ultra c") || model.contains("note air3 c");
    }

    private static DisplayClass hardwareClass() {
        String maker = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String model = Build.MODEL.toLowerCase(Locale.ROOT);
        boolean eink = hasEpdControl() || maker.contains("onyx") || maker.contains("bigme")
                || maker.contains("boyue") || maker.contains("meebook") || maker.contains("tolino")
                || maker.contains("pocketbook") || maker.contains("supernote");
        if (!eink) return DisplayClass.EMISSIVE;
        return colorInkModel(model) ? DisplayClass.COLOR_EINK : DisplayClass.MONO_EINK;
    }

    public static DisplayClass displayClass(Context context) {
        String override = preferences(context).getString("displayClass", "");
        for (DisplayClass candidate : DisplayClass.values()) {
            if (candidate.name().equals(override)) return candidate;
        }
        return hardwareClass();
    }

    public static boolean smoothText(Context context) {
        return preferences(context).getBoolean("smoothText", true);
    }

    public static void setSmoothText(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("smoothText", enabled).apply();
    }

    public static boolean night(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return preferences(context).getBoolean("night",
                displayClass(context) == DisplayClass.EMISSIVE && mode == Configuration.UI_MODE_NIGHT_YES);
    }

    public static Capabilities capabilities(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean pages = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_PAGE_UP)
                && KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_PAGE_DOWN);
        boolean speaker = false;
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio != null) {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) speaker = true;
            }
        }
        // Some readers expose frontlight channels in sysfs. Unknown channels stay unavailable.
        boolean cold = new File("/sys/class/backlight/cold").isDirectory();
        boolean warm = new File("/sys/class/backlight/warm").isDirectory();
        Configuration config = context.getResources().getConfiguration();
        return new Capabilities(hasEpdControl(), supportsRegal(), pages, cold || warm, warm,
                pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
                hardwareClass() != DisplayClass.MONO_EINK, speaker,
                pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                        && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT),
                config.keyboard == Configuration.KEYBOARD_QWERTY);
    }
}

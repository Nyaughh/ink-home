package dev.inkhome.folio;

import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
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

    private static final class Hardware {
        static final DisplayClass DISPLAY = DisplayClass.detect(Build.MANUFACTURER, Build.MODEL, hasEpdControl());
    }

    public static DisplayClass hardwareClass() {
        return Hardware.DISPLAY;
    }

    public static DisplayClass displayClass(Context context) {
        String override = preferences(context).getString("displayClass", "");
        for (DisplayClass candidate : DisplayClass.values()) {
            if (candidate.name().equals(override)) return candidate;
        }
        return hardwareClass();
    }

    static boolean isGalleryPanel() {
        String model = Build.MODEL.toLowerCase(java.util.Locale.ROOT);
        return model.contains("galy") || model.contains("gallery");
    }

    public static int accentIndex(Context context) {
        int index = preferences(context).getInt("accentIndex", 0);
        return index >= 0 && index <= 3 ? index : 0;
    }

    public static void setAccentIndex(Context context, int index) {
        preferences(context).edit().putInt("accentIndex", index).apply();
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

    private static Boolean onyxFlag(Context context, String name) {
        try {
            Class<?> controller = Class.forName("android.onyx.hardware.DeviceController");
            return (Boolean) controller.getMethod(name, Context.class).invoke(null, context);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    public static boolean hasOnyxLightDialog(Context context) {
        try {
            Class.forName("android.onyx.util.PhoneWindowHelper").getDeclaredMethod("openFrontLightDialog");
            return Boolean.TRUE.equals(onyxFlag(context, "hasBrightness"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static boolean hasMediaOutput(Context context, PackageManager pm) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null || audio.isVolumeFixed()
                || audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                <= audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)) return false;
        boolean builtIn = false;
        for (AudioDeviceInfo output : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (output.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                case AudioDeviceInfo.TYPE_HDMI:
                case AudioDeviceInfo.TYPE_HDMI_ARC:
                case AudioDeviceInfo.TYPE_HDMI_EARC:
                case AudioDeviceInfo.TYPE_LINE_ANALOG:
                case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                    return true;
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    builtIn = true;
                    break;
                default:
                    break;
            }
        }
        if (!builtIn || !pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) return false;
        // Some Onyx firmware publishes the generic Qualcomm speaker route even without
        // a transducer. Its hasAudio() query also returns true unconditionally.
        Boolean speaker = onyxFlag(context, "hasSpeaker");
        if (speaker != null) return speaker;
        if (onyxFlag(context, "hasAudio") != null) return false;
        return !"onyx".equalsIgnoreCase(Build.MANUFACTURER) && !"boox".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public static Capabilities capabilities(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean pages = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_PAGE_UP)
                && KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_PAGE_DOWN);
        Boolean front = onyxFlag(context, "hasBrightness");
        Boolean colourTemperature = onyxFlag(context, "hasCTMBrightness");
        boolean warm = colourTemperature != null ? colourTemperature : new File("/sys/class/backlight/warm/brightness").canRead();
        boolean light = front != null ? front : warm || new File("/sys/class/backlight/cold/brightness").canRead();
        Configuration config = context.getResources().getConfiguration();
        BluetoothManager bluetooth = context.getSystemService(BluetoothManager.class);
        SensorManager sensors = context.getSystemService(SensorManager.class);
        return new Capabilities(hasEpdControl(), supportsRegal(), pages, light, light && warm,
                pm.hasSystemFeature(PackageManager.FEATURE_WIFI) && context.getApplicationContext().getSystemService(WifiManager.class) != null,
                pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) && bluetooth != null && bluetooth.getAdapter() != null,
                hardwareClass() != DisplayClass.MONO_EINK, hasMediaOutput(context, pm),
                pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                        && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)
                        && sensors != null && (sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                        || sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null),
                config.keyboard == Configuration.KEYBOARD_QWERTY);
    }
}

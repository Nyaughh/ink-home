package dev.inkhome;

import android.content.SharedPreferences;

/** Converts between window offsets and saved fractions of the available travel. */
final class DotPosition {
    final int x, y;

    DotPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    void save(SharedPreferences prefs, int rotation, int maxX, int maxY) {
        prefs.edit().putFloat("freeX" + rotation, fraction(x, maxX))
                .putFloat("freeY" + rotation, fraction(y, maxY)).apply();
    }

    static DotPosition load(SharedPreferences prefs, int rotation, int maxX, int maxY,
                            boolean defaultLeft, int gap, int legacyTop, int legacyBottom) {
        boolean left = prefs.getBoolean("edgeLeft" + rotation, defaultLeft);
        float oldHeight = prefs.getFloat("edgeHeight" + rotation, .58f);
        int legacyY = legacyTop + Math.round(Math.max(0, maxY - legacyTop - legacyBottom) * oldHeight);
        float x = prefs.getFloat("freeX" + rotation, fraction(left ? gap : maxX - gap, maxX));
        float y = prefs.getFloat("freeY" + rotation, fraction(legacyY, maxY));
        return new DotPosition(pixel(x, maxX), pixel(y, maxY));
    }

    static int clamp(int offset, int travel) {
        return Math.max(0, Math.min(travel, offset));
    }

    static float fraction(int offset, int travel) {
        return travel > 0 ? clamp(offset, travel) / (float) travel : 0f;
    }

    static int pixel(float fraction, int travel) {
        return clamp(Math.round(fraction * travel), travel);
    }
}

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
        prefs.edit().putFloat("refreshEdgeX" + rotation, fraction(x, maxX))
                .putFloat("refreshEdgeY" + rotation, fraction(y, maxY)).apply();
    }

    static DotPosition load(SharedPreferences prefs, int rotation, int maxX, int maxY,
                            boolean defaultLeft) {
        // The old freeX/freeY and edgeHeight keys describe the circular control.
        float x = prefs.getFloat("refreshEdgeX" + rotation, defaultLeft ? 0f : 1f);
        float y = prefs.getFloat("refreshEdgeY" + rotation, .5f);
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

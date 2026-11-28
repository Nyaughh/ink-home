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
        prefs.edit().putFloat("refreshEdgeX" + rotation, fraction(nearestSide(x, maxX), maxX))
                .putFloat("refreshEdgeY" + rotation, fraction(y, maxY)).apply();
    }

    static DotPosition load(SharedPreferences prefs, int rotation, int maxX, int maxY,
                            boolean defaultLeft) {
        boolean left = prefs.getBoolean("edgeLeft" + rotation, defaultLeft);
        float oldX = prefs.getFloat("freeX" + rotation, left ? 0f : 1f);
        float oldY = prefs.getFloat("freeY" + rotation, prefs.getFloat("edgeHeight" + rotation, .5f));
        float x = prefs.getFloat("refreshEdgeX" + rotation, oldX);
        float y = prefs.getFloat("refreshEdgeY" + rotation, oldY);
        return new DotPosition(nearestSide(pixel(x, maxX), maxX), pixel(y, maxY));
    }

    static int nearestSide(int x, int maxX) {
        return x < maxX / 2f ? 0 : maxX;
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

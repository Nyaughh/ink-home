package dev.inkhome;

/** Full panel pixels and the scale of the 497-unit-wide sleep artwork. */
final class SleepSize {
    final int width, height;

    SleepSize(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Empty panel");
        this.width = width;
        this.height = height;
    }

    static SleepSize rotated(int width, int height, boolean quarterTurn) {
        return quarterTurn ? new SleepSize(height, width) : new SleepSize(width, height);
    }

    float artworkScale() {
        return Math.min(width / 497f, height / (1024f * 497f / 758f));
    }

    float imageScale(int sourceWidth, int sourceHeight, boolean fill) {
        float horizontal = width / (float) sourceWidth;
        float vertical = height / (float) sourceHeight;
        return fill ? Math.max(horizontal, vertical) : Math.min(horizontal, vertical);
    }

    String key() { return width + "x" + height; }
}

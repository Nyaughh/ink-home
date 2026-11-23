package dev.inkhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Line icons drawn in code on a 24×24 grid, so they stay razor sharp on e-ink at any size. */
final class Icons extends Drawable {

    private final String name;
    private final int level;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** level: battery percent, wifi bars 0..4, or -1 when unused. */
    Icons(String name, int level) {
        this.name = name;
        this.level = level;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFF000000);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xFF000000);
    }

    Icons(String name) { this(name, -1); }

    @Override
    public void draw(Canvas c) {
        float s = Math.min(getBounds().width(), getBounds().height()) / 24f;
        c.save();
        c.translate(getBounds().left + (getBounds().width() - 24 * s) / 2f, getBounds().top + (getBounds().height() - 24 * s) / 2f);
        c.scale(s, s);
        Path p = new Path();
        switch (name) {
            case "home":
                p.moveTo(3.5f, 11f); p.lineTo(12f, 4f); p.lineTo(20.5f, 11f);
                p.moveTo(6f, 9.5f); p.lineTo(6f, 20f); p.lineTo(18f, 20f); p.lineTo(18f, 9.5f);
                p.moveTo(10f, 20f); p.lineTo(10f, 14f); p.lineTo(14f, 14f); p.lineTo(14f, 20f);
                c.drawPath(p, stroke);
                break;
            case "library":
                c.drawRect(4f, 4f, 8f, 20f, stroke);
                c.drawRect(9.5f, 6f, 13.5f, 20f, stroke);
                p.moveTo(15f, 7.2f); p.lineTo(18.8f, 6.2f); p.lineTo(22f, 19f); p.lineTo(18.2f, 20f); p.close();
                c.drawPath(p, stroke);
                c.drawLine(2.5f, 20f, 21.5f, 20f, stroke);
                break;
            case "stats":
                c.drawLine(3f, 20.5f, 21f, 20.5f, stroke);
                c.drawRect(5f, 13f, 8f, 20.5f, stroke);
                c.drawRect(10.5f, 6f, 13.5f, 20.5f, stroke);
                c.drawRect(16f, 10f, 19f, 20.5f, stroke);
                break;
            case "apps":
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++)
                    c.drawRoundRect(new RectF(4f + i * 9f, 4f + j * 9f, 11f + i * 9f, 11f + j * 9f), 1.5f, 1.5f, stroke);
                break;
            case "settings": {
                // Classic gear: eight square-ish teeth around a ring, with a hole in the middle.
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    double[] offs = {-17, -10, 10, 17};
                    double[] rads = {7.2, 9.6, 9.6, 7.2};
                    for (int k = 0; k < 4; k++) {
                        double aa = a + Math.toRadians(offs[k]);
                        float x = 12f + (float) (rads[k] * Math.cos(aa)), y = 12f + (float) (rads[k] * Math.sin(aa));
                        if (i == 0 && k == 0) p.moveTo(x, y); else p.lineTo(x, y);
                    }
                }
                p.close();
                c.drawPath(p, stroke);
                c.drawCircle(12f, 12f, 3f, stroke);
                break;
            }
            case "clean": {
                // Browser-style refresh: an open circle with an arrowhead going clockwise.
                c.drawArc(new RectF(4.5f, 4.5f, 19.5f, 19.5f), 15, 300, false, stroke);
                double end = Math.toRadians(315);
                float ex = 12f + (float) (7.5 * Math.cos(end)), ey = 12f + (float) (7.5 * Math.sin(end));
                float dx = (float) -Math.sin(end), dy = (float) Math.cos(end);
                Path head = new Path();
                head.moveTo(ex + dx * 3.2f, ey + dy * 3.2f);
                head.lineTo(ex - dx * 1.2f - dy * 3f, ey - dy * 1.2f + dx * 3f);
                head.lineTo(ex - dx * 1.2f + dy * 3f, ey - dy * 1.2f - dx * 3f);
                head.close();
                c.drawPath(head, fill);
                break;
            }
            case "search":
                c.drawCircle(10.5f, 10.5f, 6f, stroke);
                c.drawLine(15f, 15f, 20.5f, 20.5f, stroke);
                break;
            case "grid":
                for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) c.drawRect(3.5f + i * 6f, 4f + j * 8.5f, 8.5f + i * 6f, 11f + j * 8.5f, stroke);
                break;
            case "list":
                for (int i = 0; i < 3; i++) {
                    c.drawRect(3.5f, 4.5f + i * 5.5f, 7f, 8f + i * 5.5f, stroke);
                    c.drawLine(10f, 6.25f + i * 5.5f, 20.5f, 6.25f + i * 5.5f, stroke);
                }
                break;
            case "back":
                p.moveTo(15f, 4.5f); p.lineTo(7.5f, 12f); p.lineTo(15f, 19.5f);
                c.drawPath(p, stroke);
                break;
            case "star":
                star(p);
                c.drawPath(p, fill);
                break;
            case "bell":
                p.moveTo(6f, 17f); p.lineTo(6f, 11f);
                p.cubicTo(6f, 7f, 8.5f, 4.5f, 12f, 4.5f);
                p.cubicTo(15.5f, 4.5f, 18f, 7f, 18f, 11f);
                p.lineTo(18f, 17f); p.lineTo(19.5f, 18.5f); p.lineTo(4.5f, 18.5f); p.close();
                c.drawPath(p, stroke);
                c.drawLine(10.5f, 21f, 13.5f, 21f, stroke);
                break;
            case "wifi":
                drawWifi(c);
                break;
            case "battery":
                drawBattery(c);
                break;
            case "storage":
                c.drawRoundRect(new RectF(4f, 5f, 20f, 19f), 2f, 2f, stroke);
                c.drawLine(4f, 12f, 20f, 12f, stroke);
                fillCircle(c, 16.5f, 8.5f, 1.2f);
                fillCircle(c, 16.5f, 15.5f, 1.2f);
                break;
            case "book":
                p.moveTo(12f, 6f); p.cubicTo(9f, 4.5f, 6f, 4.5f, 3f, 5.5f); p.lineTo(3f, 19f);
                p.cubicTo(6f, 18f, 9f, 18f, 12f, 19.5f); p.cubicTo(15f, 18f, 18f, 18f, 21f, 19f);
                p.lineTo(21f, 5.5f); p.cubicTo(18f, 4.5f, 15f, 4.5f, 12f, 6f); p.lineTo(12f, 19.5f);
                c.drawPath(p, stroke);
                break;
            case "plus":
                c.drawLine(12f, 5f, 12f, 19f, stroke);
                c.drawLine(5f, 12f, 19f, 12f, stroke);
                break;
            case "up":
                p.moveTo(5f, 15f); p.lineTo(12f, 8f); p.lineTo(19f, 15f);
                c.drawPath(p, stroke);
                break;
            case "down":
                p.moveTo(5f, 9f); p.lineTo(12f, 16f); p.lineTo(19f, 9f);
                c.drawPath(p, stroke);
                break;
            case "image":
                c.drawRoundRect(new RectF(3.5f, 5f, 20.5f, 19f), 2f, 2f, stroke);
                p.moveTo(3.5f, 16f); p.lineTo(9f, 11f); p.lineTo(13f, 14.5f); p.lineTo(16f, 12f); p.lineTo(20.5f, 16f);
                c.drawPath(p, stroke);
                c.drawCircle(15.5f, 8.8f, 1.4f, stroke);
                break;
            case "chevron":
                p.moveTo(9f, 5f); p.lineTo(16f, 12f); p.lineTo(9f, 19f);
                c.drawPath(p, stroke);
                break;
            default:
                c.drawCircle(12f, 12f, 8f, stroke);
        }
        c.restore();
    }

    private void drawWifi(Canvas c) {
        int bars = Math.max(0, level);
        Paint faint = new Paint(stroke);
        faint.setColor(0xFFBBBBBB);
        for (int i = 0; i < 3; i++) {
            float r = 5f + i * 5f;
            RectF o = new RectF(12f - r, 20f - r, 12f + r, 20f + r);
            c.drawArc(o, 225, 90, false, bars > i + 1 ? stroke : faint);
        }
        if (level < 0) {
            c.drawLine(4f, 4f, 20f, 20f, stroke);
        } else {
            fillCircle(c, 12f, 19f, 1.8f);
        }
    }

    private void drawBattery(Canvas c) {
        c.drawRoundRect(new RectF(2.5f, 7f, 19.5f, 17f), 2f, 2f, stroke);
        c.drawRect(20.5f, 10f, 22f, 14f, fill);
        float pct = Math.max(0, Math.min(100, level)) / 100f;
        if (pct > 0) c.drawRect(4.5f, 9f, 4.5f + 13f * pct, 15f, fill);
    }

    private void star(Path p) {
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? 9f : 4f;
            float x = 12f + (float) (r * Math.cos(a)), y = 12.5f - (float) (r * Math.sin(a));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }

    private void fillCircle(Canvas c, float x, float y, float r) {
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, r, white);
        c.drawCircle(x, y, r, stroke);
    }

    @Override public void setAlpha(int a) { stroke.setAlpha(a); fill.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { stroke.setColorFilter(cf); fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

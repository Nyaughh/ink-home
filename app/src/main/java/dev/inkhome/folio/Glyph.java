package dev.inkhome.folio;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Folio's square-ended glyphs on a 24-unit grid. */
public class Glyph extends Drawable {
    private final String name;
    private final int level;
    private final boolean selected;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public Glyph(String name) { this(name, -1); }
    public Glyph(String name, int level) { this(name, level, false); }
    public Glyph(String name, int level, boolean selected) {
        this.name = name;
        this.level = level;
        this.selected = selected;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2);
        line.setStrokeCap(Paint.Cap.SQUARE);
        line.setStrokeJoin(Paint.Join.MITER);
        setTint(Color.BLACK);
    }

    @Override public void draw(Canvas c) {
        int w = getBounds().width(), h = getBounds().height();
        int size = Math.min(w, h);
        if (size <= 0) return;
        c.save();
        c.translate(getBounds().left + (w - size) / 2, getBounds().top + (h - size) / 2);
        c.scale(size / 24f, size / 24f);
        Paint dest = selected ? solid : line;
        switch (name) {
            case "home":
                polygon(c, dest, 2,11, 12,3, 22,11, 19,11, 19,21, 14,21, 14,15, 10,15, 10,21, 5,21, 5,11);
                break;
            case "library":
                c.drawRect(3,5,7,20,dest);
                c.drawRect(10,3,14,20,dest);
                polygon(c,dest, 16,7, 19,6, 22,19, 19,20);
                break;
            case "apps":
                for (int x : new int[]{4,14}) for (int y : new int[]{4,14})
                    c.drawRect(x,y,x+6,y+6,dest);
                break;
            case "stats":
                c.drawRect(3,14,7,21,dest);
                c.drawRect(10,4,14,21,dest);
                c.drawRect(17,9,21,21,dest);
                break;
            case "settings":
                path.reset();
                for (int tooth = 0; tooth < 8; tooth++) {
                    for (int step = 0; step < 4; step++) {
                        double a = Math.toRadians(tooth * 45 + new int[]{-16,-10,10,16}[step]);
                        int radius = step == 1 || step == 2 ? 10 : 8;
                        float x = Math.round(12 + radius * Math.cos(a));
                        float y = Math.round(12 + radius * Math.sin(a));
                        if (tooth == 0 && step == 0) path.moveTo(x,y); else path.lineTo(x,y);
                    }
                }
                path.close();
                path.addCircle(12,12,3,Path.Direction.CCW);
                path.setFillType(Path.FillType.EVEN_ODD);
                c.drawPath(path,dest);
                break;
            case "search":
                c.drawCircle(10,10,6,line); segment(c,15,15,21,21); break;
            case "back": lines(c,15,5, 8,12, 15,19); break;
            case "forward": case "chevron": lines(c,9,5, 16,12, 9,19); break;
            case "up": lines(c,5,15, 12,8, 19,15); break;
            case "down": lines(c,5,9, 12,16, 19,9); break;
            case "close": segment(c,5,5,19,19); segment(c,19,5,5,19); break;
            case "more":
                for (int x = 5; x <= 19; x += 7) c.drawRect(x-1,11,x+1,13,solid);
                break;
            case "plus": segment(c,12,5,12,19); segment(c,5,12,19,12); break;
            case "minus": segment(c,5,12,19,12); break;
            case "check": lines(c,3,12, 9,18, 21,5); break;
            case "filter": polygon(c,line, 3,4, 21,4, 14,12, 14,19, 10,21, 10,12); break;
            case "grid":
                for (int x = 3; x <= 17; x += 7) for (int y = 5; y <= 14; y += 9)
                    c.drawRect(x,y,x+4,y+5,line);
                break;
            case "contents": case "list":
                for (int y = 5; y <= 19; y += 7) {
                    c.drawRect(3,y-1,6,y+1,solid); segment(c,10,y,21,y);
                }
                break;
            case "star": polygon(c,solid, 12,2, 15,9, 22,9, 17,14, 19,21, 12,17, 5,21, 7,14, 2,9, 9,9); break;
            case "clean": case "refresh":
                c.drawArc(4,4,20,20,15,305,false,line); lines(c,20,3, 20,9, 14,9); break;
            case "bookmark": polygon(c,line, 6,3, 18,3, 18,21, 12,17, 6,21); break;
            case "wifi": case "wifi-off":
                wifi(c, name.equals("wifi-off") ? -1 : level); break;
            case "battery": case "battery-charging":
                boolean charging = name.equals("battery-charging");
                c.drawRect(2,7,19,17,line); c.drawRect(20,10,22,14,solid);
                int end = 4 + Math.round(Math.max(0,Math.min(100,level)) * 13 / 100f);
                c.drawRect(4,9,end,15,solid);
                if (charging) lines(c,13,1, 10,4, 14,4, 11,7);
                break;
            case "bell":
                path.reset(); path.moveTo(4,18); path.lineTo(6,15); path.lineTo(6,10);
                path.cubicTo(6,2,18,2,18,10); path.lineTo(18,15); path.lineTo(20,18);
                path.close(); c.drawPath(path,line); segment(c,11,21,13,21); break;
            case "bluetooth": lines(c,5,6, 17,17, 12,21, 12,3, 17,7, 5,18); break;
            case "quiet": case "volume":
                polygon(c,line, 3,9, 7,9, 12,5, 12,19, 7,15, 3,15);
                if (name.equals("quiet")) {
                    segment(c,16,9,21,15); segment(c,21,9,16,15);
                } else c.drawArc(7,4,21,20,-55,110,false,line);
                break;
            case "tune":
                for (int y = 5; y <= 19; y += 7) {
                    int x = y == 12 ? 15 : 8;
                    segment(c,3,y,x-2,y); segment(c,x+2,y,21,y);
                    c.drawRect(x-2,y-2,x+2,y+2,line);
                }
                break;
            case "rotate":
                polygon(c,line, 5,12, 12,5, 19,12, 12,19);
                lines(c,3,8, 3,3, 8,3); lines(c,21,16, 21,21, 16,21); break;
            case "moon":
                path.reset(); path.moveTo(15,3); path.cubicTo(1,1,0,21,13,21);
                path.quadTo(19,21,21,15); path.cubicTo(13,18,8,8,15,3);
                path.close(); c.drawPath(path,line); break;
            case "power": segment(c,12,2,12,12); c.drawArc(4,4,20,21,-53,286,false,line); break;
            case "switcher": c.drawRect(3,8,16,21,line); lines(c,8,4, 21,4, 21,16); break;
            case "light":
                c.drawCircle(12,12,4,line);
                for (int i=0;i<8;i++) { c.save(); c.rotate(45*i,12,12); segment(c,12,2,12,4); c.restore(); }
                break;
            case "book":
                polygon(c,line, 3,5, 8,5, 12,7, 16,5, 21,5, 21,19, 16,19, 12,21, 8,19, 3,19);
                segment(c,12,7,12,20); break;
            case "folder": polygon(c,line, 2,5, 9,5, 12,8, 22,8, 22,20, 2,20); break;
            case "image":
                c.drawRect(3,4,21,20,line); c.drawCircle(16,9,2,solid);
                lines(c,3,16, 9,10, 15,16, 18,13, 21,16); break;
            case "clock": c.drawCircle(12,12,9,line); lines(c,12,6, 12,12, 17,15); break;
            case "storage":
                c.drawRect(4,4,20,20,line); segment(c,4,12,20,12);
                c.drawRect(15,7,17,9,solid); c.drawRect(15,15,17,17,solid); break;
            default: c.drawCircle(12,12,8,line);
        }
        c.restore();
    }

    private void wifi(Canvas c, int bars) {
        for (int i = 1; i <= 3; i++) {
            if (bars > i || bars < 0) {
                int r = 5*i;
                c.drawArc(12-r,20-r,12+r,20+r,225,90,false,line);
            }
        }
        c.drawCircle(12,19,2,bars > 0 ? solid : line);
        if (bars < 0) segment(c,4,4,20,20);
    }

    private void segment(Canvas c, int x, int y, int endX, int endY) {
        c.drawLine(x,y,endX,endY,line);
    }

    private void lines(Canvas c, int... points) { drawPath(c,line,false,points); }
    private void polygon(Canvas c, Paint paint, int... points) { drawPath(c,paint,true,points); }

    private void drawPath(Canvas c, Paint paint, boolean closed, int[] points) {
        path.reset();
        path.moveTo(points[0],points[1]);
        for (int i=2;i<points.length;i+=2) path.lineTo(points[i],points[i+1]);
        if (closed) path.close();
        c.drawPath(path,paint);
    }

    @Override public void setTint(int color) { line.setColor(color); solid.setColor(color); invalidateSelf(); }
    @Override public void setAlpha(int alpha) { line.setAlpha(alpha); solid.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {
        line.setColorFilter(filter); solid.setColorFilter(filter); invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

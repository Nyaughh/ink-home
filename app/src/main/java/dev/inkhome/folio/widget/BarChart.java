package dev.inkhome.folio.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.Tokens;

public final class BarChart extends View {
    private final Folio folio;
    private final String[] days;
    private final int[] minutes;
    private final int goal, maximum;
    private final Paint paint = new Paint();
    private final float fontSize;

    public BarChart(Folio f, String[] days, int[] minutes, int goal) {
        super(f.context);
        if (days.length != minutes.length) throw new IllegalArgumentException("Each day needs minutes");
        this.folio = f;
        this.days = days.clone();
        this.minutes = minutes.clone();
        this.goal = Math.max(0, goal);
        int max = Math.max(1, goal);
        StringBuilder summary = new StringBuilder("Reading time");
        if (goal > 0) summary.append(", goal ").append(goal).append(" minutes");
        for (int i = 0; i < minutes.length; i++) {
            max = Math.max(max, minutes[i]);
            summary.append(", ").append(days[i]).append(" ").append(minutes[i]).append(" minutes");
        }
        maximum = max;
        setContentDescription(summary.toString());
        fontSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                Tokens.Type.MIN_CHART_SIZE * f.uiScale * f.textScale, getResources().getDisplayMetrics());
        setMinimumHeight(Math.max(f.u(92), Math.round(fontSize * 5)));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(folio.u(280), widthSpec),
                resolveSize(getSuggestedMinimumHeight(), heightSpec));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (days.length == 0) return;
        float column = getWidth() / (float) days.length;
        float baseline = getHeight() - fontSize * 1.5f;
        float plot = Math.max(1, baseline - fontSize * 1.7f);
        paint.setTypeface(Type.sans(getContext(), 600));
        paint.setTextSize(fontSize);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(folio.palette.ink);
        if (goal > 0) {
            float y = baseline - plot * ((float) goal / maximum);
            int dash = Math.max(1, folio.u(4));
            for (int x = 0; x < getWidth(); x += 2 * dash) c.drawRect(x, y, x + dash, y + folio.hairline, paint);
        }
        for (int i = 0; i < days.length; i++) {
            float center = (i + .5f) * column;
            float top = baseline - plot * (Math.max(0, minutes[i]) / (float) maximum);
            float left = center - column * .28f, right = center + column * .28f;
            paint.setAntiAlias(false);
            if (minutes[i] > 0) {
                boolean met = goal == 0 || minutes[i] >= goal;
                paint.setColor(met ? folio.palette.accent : folio.palette.ink);
                c.drawRect(left, top, right, baseline, paint);
                if (!met) {
                    paint.setColor(folio.palette.paper);
                    c.drawRect(left + folio.hairline, top + folio.hairline,
                            right - folio.hairline, baseline - folio.hairline, paint);
                }
            }
            paint.setAntiAlias(true);
            paint.setColor(folio.palette.ink);
            c.drawText(String.valueOf(minutes[i]), center, top - folio.u(2), paint);
            c.drawText(days[i], center, getHeight() - folio.u(3), paint);
        }
    }
}

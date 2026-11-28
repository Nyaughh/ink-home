package dev.inkhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

final class ReadingGoalTile extends DataTile {
    static final HomeComponent COMPONENT = new BuiltinTile("goal", R.string.home_settings_goal, 1, 1,
            ReadingGoalTile::new, 0, "default", "percentage") {
        @Override boolean squareFace() { return true; }
    };

    private ReadingGoalTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
        goal();
    }

    private void goal() {
        boolean hasAccess = Stats.hasAccess(a);
        if (a.stats == null || !hasAccess) {
            readingTimeMessage(hasAccess);
        } else {
            int target = a.store.dailyGoal();
            int today = a.stats.today();
            boolean percentage = "percentage".equals(item.variant) && target > 0;
            String total = percentage ? a.getString(R.string.launcher_percent, Math.round(100f * today / target))
                    : Stats.hm(a, today);
            int side = Math.min(width, height);
            TextView value = label(total, compact ? TypeScale.BODY : TypeScale.TITLE_SM, 1);
            value.setIncludeFontPadding(false);
            float scale = Math.min((float) side / Math.max(1, textWidth(value)),
                    side * .38f / Math.max(1, measuredHeight(value)));
            if (scale < 1f) value.setTextSize(TypedValue.COMPLEX_UNIT_PX, value.getTextSize() * scale);
            int caption = Math.min(side, measuredHeight(value));
            int gap = Math.min(ink.dp(4), side / 20);
            int diameter = Math.max(0, side - caption - gap);
            float fraction = target > 0 ? Math.min(1f, (float) today / target) : 0;
            View ring = new View(a) {
                private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    float diameter = getWidth();
                    float stroke = Math.max(1, diameter / 12);
                    float inset = stroke / 2;
                    pen.setStyle(Paint.Style.STROKE);
                    pen.setStrokeWidth(stroke);
                    pen.setColor(ink.folio.palette.rule);
                    canvas.drawCircle(diameter / 2, diameter / 2, (diameter - stroke) / 2, pen);
                    pen.setColor(ink.TEXT);
                    canvas.drawArc(inset, inset, diameter - inset, diameter - inset,
                            -90, 360 * fraction, false, pen);
                }
            };
            tile.setGravity(Gravity.CENTER);
            tile.addView(ring, diameter, diameter);
            value.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, caption);
            params.topMargin = gap;
            tile.addView(value, params);
            tile.setContentDescription(a.getString(R.string.launcher_daily_goal) + ", " + total);
        }
        action(() -> { a.statsTab = "overview"; a.go(MainActivity.Screen.STATS); });
    }
}

package dev.inkhome;

import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

final class ReadingGoalTile extends DataTile {
    static final HomeComponent COMPONENT = new BuiltinTile("goal", R.string.home_settings_goal, 1, 1,
            ReadingGoalTile::new, 0, "default", "percentage");

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
            LinearLayout numbers = ink.row();
            tile.addView(numbers);
            boolean percentage = "percentage".equals(item.variant) && target > 0;
            String total = percentage ? a.getString(R.string.launcher_percent, Math.round(100f * today / target))
                    : Stats.hm(a, today);
            TextView value = label(total, compact ? TypeScale.BODY : TypeScale.TITLE_SM, 1);
            if (textWidth(value) > width || measuredHeight(value) > height) value = label(total, TypeScale.META, 1);
            numbers.addView(value);
            if (target > 0 && !percentage) {
                TextView detail = label(a.getString(R.string.stats_ui_goal_minutes, target), TypeScale.META, 1);
                if (textWidth(value) + ink.dp(8) + textWidth(detail) <= width && measuredHeight(detail) <= height) {
                    numbers.addView(detail, ink.m(-2, -2, 8, 0, 0, 0));
                } else if (measuredHeight(value) + measuredHeight(detail) + ink.dp(8) <= height) {
                    tile.addView(detail);
                }
            }
            if (target > 0 && measuredHeight(tile) + ink.dp(8) <= height) {
                tile.addView(ink.progress((float) today / target, 4), ink.m(-1, ink.dp(4), 0, 4, 0, 0));
            }
        }
        prependHeading(a.getString(R.string.launcher_daily_goal));
        action(() -> { a.statsTab = "overview"; a.go(MainActivity.Screen.STATS); });
    }
}

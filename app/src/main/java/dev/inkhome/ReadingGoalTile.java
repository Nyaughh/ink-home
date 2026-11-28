package dev.inkhome;

import android.widget.LinearLayout;

import dev.inkhome.folio.core.TypeScale;

final class ReadingGoalTile extends HomeTiles {
    static final HomeComponent COMPONENT = new BuiltinTile("goal", R.string.home_settings_goal, 1, 1,
            ReadingGoalTile::new, 0, "default", "percentage");

    private ReadingGoalTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        goal();
    }

    private void goal() {
        if (item.h > 1) heading(a.getString(R.string.launcher_daily_goal));
        if (a.stats == null || !Stats.hasAccess(a)) {
            tile.addView(label(a.getString(a.stats == null && Stats.hasAccess(a)
                    ? R.string.stats_ui_loading_reading_time : R.string.stats_ui_turn_on_reading_time), TypeScale.META, 2));
        } else {
            int target = a.store.dailyGoal();
            int today = a.stats.today();
            LinearLayout numbers = ink.row();
            tile.addView(numbers);
            boolean percentage = "percentage".equals(item.variant) && target > 0;
            String total = percentage ? a.getString(R.string.launcher_percent, Math.round(100f * today / target))
                    : Stats.hm(a, today);
            numbers.addView(label(total, item.h == 1 || compact ? TypeScale.BODY : TypeScale.TITLE_SM, 1));
            if (target > 0 && !percentage && item.w > 1) numbers.addView(label(a.getString(R.string.stats_ui_goal_minutes, target),
                    TypeScale.META, 1), ink.m(-2, -2, 8, 0, 0, 0));
            if (target > 0) tile.addView(ink.progress((float) today / target, 4), ink.m(-1, -2, 0, 4, 0, 0));
        }
        action(() -> { a.statsTab = "overview"; a.go(MainActivity.Screen.STATS); });
    }
}

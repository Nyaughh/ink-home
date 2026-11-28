package dev.inkhome;

import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

final class BatteryTile extends DataTile {
    static final HomeComponent COMPONENT = new BuiltinTile("battery", R.string.home_settings_battery, 1, 1,
            BatteryTile::new, 0, "default", "meter");

    private BatteryTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
        battery();
    }

    private void battery() {
        observe(() -> {
            MainActivity.Battery state = a.battery();
            String percentage = a.getString(R.string.launcher_percent, state.percent);
            String caption = a.getString(!state.charging ? R.string.launcher_on_battery :
                    state.percent == 100 ? R.string.launcher_charged : R.string.launcher_charging);
            String description = percentage + ", " + caption;
            if (description.equals(tile.getContentDescription())) return;
            tile.setContentDescription(description);
            tile.removeAllViews();
            TextView percent = label(percentage, compact ? TypeScale.BODY : TypeScale.TITLE_SM, 1);
            if (textWidth(percent) > width || measuredHeight(percent) > height) {
                percent = label(percentage, TypeScale.META, 1);
            }
            TextView status = label(caption, TypeScale.META, 1);
            LinearLayout body = ink.row();
            tile.addView(body);
            int gap = ink.dp(8);
            int icon = compact ? 16 : 24;
            int valueWidth = textWidth(percent);
            boolean meter = "meter".equals(item.variant) && width >= valueWidth + ink.dp(32);
            int required = valueWidth + (meter ? ink.dp(32) : 0);
            if (width >= required + ink.dp(icon) + gap && height >= ink.dp(icon)) {
                body.addView(ink.icon("battery", icon), ink.m(ink.dp(icon), ink.dp(icon), 0, 0, 8, 0));
                required += ink.dp(icon) + gap;
            }
            if (meter) {
                body.addView(ink.progress(state.percent / 100f, 5), ink.weight(1));
                body.addView(percent, ink.m(-2, -2, 8, 0, 0, 0));
            } else body.addView(percent, ink.weight(1));
            if (width >= required + gap + textWidth(status) && measuredHeight(status) <= height) {
                body.addView(status, ink.m(-2, -2, 8, 0, 0, 0));
            } else if (measuredHeight(body) + measuredHeight(status) <= height) {
                tile.addView(status);
            }
        }, Intent.ACTION_BATTERY_CHANGED);
        action(() -> a.start(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
    }
}

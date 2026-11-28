package dev.inkhome;

import android.content.Intent;
import android.provider.AlarmClock;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

import java.util.Date;

final class ClockTile extends DataTile {
    static final HomeComponent COMPONENT = new BuiltinTile("clock", R.string.settings_ui_clock, 1, 1,
            ClockTile::new, 0, "default", "time only");

    private ClockTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
        clock();
    }

    private void clock() {
        boolean timeOnly = "time only".equals(item.variant);
        observe(() -> {
            Date now = new Date();
            String value = a.timeText(now);
            String day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(now);
            String description = timeOnly ? value : value + ", " + day;
            if (description.equals(tile.getContentDescription())) return;
            tile.setContentDescription(description);
            tile.removeAllViews();
            TextView time = fittedTime(value, timeOnly);
            TextView date = label(day, TypeScale.META, 1);
            if (!timeOnly && textWidth(time) + ink.dp(8) + textWidth(date) <= width
                    && measuredHeight(date) <= height) {
                LinearLayout face = ink.row();
                tile.addView(face);
                face.addView(time, ink.weight(1));
                face.addView(date);
            } else {
                if (timeOnly) time.setGravity(android.view.Gravity.CENTER);
                tile.addView(time);
                if (!timeOnly && measuredHeight(time) + measuredHeight(date) <= height) tile.addView(date);
            }
        }, Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
        action(() -> a.start(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
    }

    private TextView fittedTime(String value, boolean timeOnly) {
        TypeScale[] sizes = {timeOnly ? TypeScale.DISPLAY : TypeScale.TITLE, TypeScale.BODY_LG, TypeScale.META};
        for (TypeScale size : sizes) {
            if (compact && size == sizes[0]) continue;
            TextView time = label(value, size, 1);
            if (textWidth(time) <= width && measuredHeight(time) <= height) return time;
        }
        return label(value, TypeScale.CHART, 1);
    }
}

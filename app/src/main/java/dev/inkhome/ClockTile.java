package dev.inkhome;

import android.content.Intent;
import android.provider.AlarmClock;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

import java.util.Date;

final class ClockTile extends HomeTiles {
    static final HomeComponent COMPONENT = new BuiltinTile("clock", R.string.settings_ui_clock, 1, 1,
            ClockTile::new, 0, "default", "time only");

    private ClockTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        clock();
    }

    private void clock() {
        boolean timeOnly = "time only".equals(item.variant);
        TextView time = label("", item.h == 1 || compact ? TypeScale.BODY_LG
                : timeOnly ? TypeScale.DISPLAY : TypeScale.TITLE, 1);
        if (timeOnly) time.setGravity(android.view.Gravity.CENTER);
        LinearLayout face = item.w == 1 ? ink.col() : ink.row();
        tile.addView(face);
        if (item.w == 1) face.addView(time, -1, -2);
        else face.addView(time, ink.weight(1));
        TextView date = label("", TypeScale.META, 1);
        if (!timeOnly && (item.w > 1 || item.h > 1)) face.addView(date);
        observe(() -> {
            Date now = new Date();
            String value = a.timeText(now);
            if (!value.contentEquals(time.getText())) time.setText(value);
            String day = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(now);
            if (!day.contentEquals(date.getText())) date.setText(day);
        }, Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED);
        action(() -> a.start(new Intent(AlarmClock.ACTION_SHOW_ALARMS)));
    }
}

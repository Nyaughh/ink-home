package dev.inkhome;

import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

final class BatteryTile extends HomeTiles {
    static final HomeComponent COMPONENT = new BuiltinTile("battery", R.string.home_settings_battery, 1, 1,
            BatteryTile::new, 0, "default", "meter");

    private BatteryTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        battery();
    }

    private void battery() {
        TextView percent = label("", compact || item.h == 1 ? TypeScale.BODY : TypeScale.TITLE_SM, 1);
        TextView status = label("", TypeScale.META, 1);
        LinearLayout body = ink.row();
        tile.addView(body);
        boolean showMeter = "meter".equals(item.variant);
        dev.inkhome.folio.widget.Progress meter = new dev.inkhome.folio.widget.Progress(ink.folio, 5, true, 0);
        int size = compact || item.w == 1 ? 16 : 24;
        body.addView(ink.icon("battery", size), ink.m(ink.dp(size), ink.dp(size), 0, 0, 8, 0));
        if (showMeter) body.addView(meter, ink.weight(1));
        else body.addView(percent, ink.weight(1));
        if (item.w > 1) body.addView(status);
        observe(() -> {
            MainActivity.Battery state = a.battery();
            if (showMeter) meter.setPercent(state.percent);
            String percentage = a.getString(R.string.launcher_percent, state.percent);
            if (!percentage.contentEquals(percent.getText())) percent.setText(percentage);
            String caption = a.getString(!state.charging ? R.string.launcher_on_battery :
                    state.percent == 100 ? R.string.launcher_charged : R.string.launcher_charging);
            if (!caption.contentEquals(status.getText())) status.setText(caption);
        }, Intent.ACTION_BATTERY_CHANGED);
        action(() -> a.start(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
    }
}

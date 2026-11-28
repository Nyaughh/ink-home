package dev.inkhome;

import android.view.View;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;

/** Measures candidate faces with the active Folio type and display scale. */
abstract class DataTile extends HomeTiles {
    DataTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
    }

    int textWidth(TextView view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return view.getMeasuredWidth();
    }

    int measuredHeight(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);
        return view.getMeasuredHeight();
    }

    void prependHeading(String title) {
        TextView heading = ink.oneLine(ink.text(title, TypeScale.LABEL, ink.TEXT2));
        if (measuredHeight(tile) + measuredHeight(heading) <= height) tile.addView(heading, 0);
    }

    void readingTimeMessage(boolean hasAccess) {
        TextView message = label(a.getString(hasAccess ? R.string.stats_ui_loading_reading_time
                : R.string.stats_ui_turn_on_reading_time), TypeScale.META, 3);
        while (message.getMaxLines() > 1 && measuredHeight(message) > height) {
            message.setMaxLines(message.getMaxLines() - 1);
        }
        tile.addView(message);
    }
}

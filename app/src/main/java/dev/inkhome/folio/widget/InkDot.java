package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import dev.inkhome.folio.Folio;
import android.widget.FrameLayout;

public final class InkDot {
    private InkDot() {}

    public static View create(Folio f, View.OnClickListener refresh, View.OnLongClickListener options) {
        FrameLayout dot = IconButton.create(f, "refresh", f.context.getString(R.string.reader_ui_clean_screen), refresh);
        View disc = new View(f.context);
        disc.setBackground(DataWidgets.shape(f, f.palette.paper, f.rule, true));
        dot.addView(disc, 0, new FrameLayout.LayoutParams(f.u(42), f.u(42), Gravity.CENTER));
        dot.setOnLongClickListener(options);
        dot.setTooltipText(f.context.getString(R.string.reader_ui_hold_for_refresh_options));
        return dot;
    }
}

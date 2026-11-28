package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import dev.inkhome.folio.Folio;
import android.widget.FrameLayout;

public final class InkDot {
    private InkDot() {}

    public static View create(Folio f, View.OnClickListener refresh, View.OnLongClickListener options) {
        FrameLayout dot = new FrameLayout(f.context);
        dot.setMinimumWidth(f.u(48));
        dot.setMinimumHeight(f.u(48));
        View tab = DataWidgets.icon(f, "refresh", f.palette.ink);
        tab.setPadding(f.u(3), 0, f.u(3), 0);
        tab.setBackground(DataWidgets.shape(f, f.palette.paper, f.rule, false));
        dot.addView(tab, new FrameLayout.LayoutParams(f.u(24), f.u(42), Gravity.CENTER));
        dot.setContentDescription(f.context.getString(R.string.service_ui_ink_dot_clean_screen));
        dot.setForeground(DataWidgets.focus(f));
        DataWidgets.action(dot, refresh);
        dot.setOnLongClickListener(options);
        dot.setTooltipText(f.context.getString(R.string.reader_ui_hold_for_refresh_options));
        return dot;
    }
}

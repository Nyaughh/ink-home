package dev.inkhome.folio.widget;

import android.view.Gravity;
import android.view.View;
import dev.inkhome.folio.Folio;
import android.widget.FrameLayout;

public final class InkDot {
    private InkDot() {}

    public static View create(Folio f, View.OnClickListener refresh, View.OnLongClickListener options) {
        FrameLayout dot = IconButton.create(f, "refresh", "Clean screen", refresh);
        View disc = new View(f.context);
        disc.setBackground(DataWidgets.shape(f, f.palette.paper, f.rule, true));
        dot.addView(disc, 0, new FrameLayout.LayoutParams(f.u(42), f.u(42), Gravity.CENTER));
        dot.setOnLongClickListener(options);
        dot.setTooltipText("Hold for refresh options");
        return dot;
    }
}

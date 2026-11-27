package dev.inkhome.folio.widget;

import android.view.Gravity;
import android.view.View;
import dev.inkhome.folio.Folio;
import android.widget.FrameLayout;

public final class IconButton {
    private IconButton() {}

    public static FrameLayout create(Folio f, String glyph, String description, View.OnClickListener action) {
        FrameLayout target = new FrameLayout(f.context);
        target.setMinimumWidth(f.u(48));
        target.setMinimumHeight(f.u(48));
        target.addView(DataWidgets.icon(f, glyph, f.palette.ink),
                new FrameLayout.LayoutParams(f.u(24), f.u(24), Gravity.CENTER));
        target.setContentDescription(description);
        target.setForeground(DataWidgets.focus(f));
        DataWidgets.action(target, action);
        return target;
    }
}

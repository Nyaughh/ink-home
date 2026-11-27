package dev.inkhome.folio.widget;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;

public final class Button {
    public enum Style { PRIMARY, SECONDARY, TEXT }

    private Button() {}

    public static TextView create(Folio f, String label, Style style, View.OnClickListener action) {
        boolean primary = style == Style.PRIMARY;
        TextView view = Type.text(f, label, TypeScale.BODY_LG, primary ? f.palette.onInverse : f.palette.ink);
        view.setTypeface(Type.sans(f.context, 600));
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(f.u(44));
        view.setPadding(f.u(12), 0, f.u(12), 0);
        view.setBackground(DataWidgets.shape(f, primary ? f.palette.inverse : f.palette.paper,
                style == Style.SECONDARY ? f.rule : 0, false));
        if (style == Style.TEXT) view.setPaintFlags(view.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        DataWidgets.action(view, action);
        view.setForeground(DataWidgets.focus(f));
        return view;
    }
}

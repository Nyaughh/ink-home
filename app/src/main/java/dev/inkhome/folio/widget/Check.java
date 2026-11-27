package dev.inkhome.folio.widget;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import android.widget.CheckBox;
import android.graphics.drawable.*;
import dev.inkhome.folio.Glyph;

public final class Check {
    private Check() {}

    public static CheckBox create(Folio f, String label, boolean checked) {
        CheckBox view = new CheckBox(f.context);
        StateListDrawable states = new StateListDrawable();
        Drawable border = DataWidgets.shape(f, f.palette.paper, f.hairline, false);
        Drawable mark = new Glyph("check");
        mark.setTint(f.palette.ink);
        LayerDrawable selected = new LayerDrawable(new Drawable[]{
                DataWidgets.shape(f, f.palette.paper, f.hairline, false), mark});
        int inset = f.u(3);
        selected.setLayerInset(1, inset, inset, inset, inset);
        ((GradientDrawable) border).setSize(f.u(20), f.u(20));
        ((GradientDrawable) selected.getDrawable(0)).setSize(f.u(20), f.u(20));
        states.addState(new int[]{android.R.attr.state_checked}, selected);
        states.addState(new int[]{}, border);
        view.setButtonTintList(null);
        view.setButtonDrawable(states);
        view.setPaddingRelative(f.u(8), 0, 0, 0);
        view.setMinHeight(f.u(48));
        view.setText(label);
        view.setTypeface(Type.sans(f.context, TypeScale.BODY.weight(f.displayClass)));
        view.setTextSize(TypeScale.BODY.size * f.uiScale * f.textScale);
        view.setTextColor(f.palette.ink);
        view.setBackgroundColor(f.palette.paper);
        view.setForeground(DataWidgets.focus(f));
        view.setChecked(checked);
        return view;
    }
}

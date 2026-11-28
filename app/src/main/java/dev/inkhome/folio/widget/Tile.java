package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Glyph;
import dev.inkhome.folio.core.TypeScale;

public final class Tile extends LinearLayout {
    private final Folio folio;
    private final String name;
    private final TextView title, state;

    public Tile(Folio f, String glyph, String name, String value, boolean active, OnClickListener action) {
        this(f, new Glyph(glyph), name, value, active, action);
    }

    public Tile(Folio f, Glyph glyph, String name, String value, boolean active, OnClickListener action) {
        super(f.context);
        this.folio = f;
        this.name = name;
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(f.u(12), 0, f.u(12), 0);
        setMinimumHeight(f.u(54));
        addView(DataWidgets.icon(f, glyph, f.palette.ink), f.u(24), f.u(24));
        LinearLayout labels = DataWidgets.column(f);
        labels.setPadding(f.u(10), 0, 0, 0);
        title = DataWidgets.label(f, name, TypeScale.LABEL);
        state = DataWidgets.label(f, value, TypeScale.META);
        labels.addView(title);
        labels.addView(state);
        addView(labels);
        DataWidgets.action(this, action);
        setForeground(DataWidgets.focus(f));
        setState(value, active);
    }

    public void setState(String value, boolean active) {
        int color = active ? folio.palette.onInverse : folio.palette.ink;
        setSelected(active);
        setBackground(DataWidgets.shape(folio, active ? folio.palette.inverse : folio.palette.paper,
                folio.hairline, false));
        ((android.widget.ImageView) getChildAt(0)).getDrawable().setTint(color);
        title.setTextColor(color);
        state.setTextColor(color);
        state.setText(value);
        setContentDescription(getContext().getString(R.string.reader_ui_label_value, name, value));
    }
}

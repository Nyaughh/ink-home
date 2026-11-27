package dev.inkhome.folio.widget;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.Tokens;
import dev.inkhome.folio.core.TypeScale;

/** The supplied cover view owns its image binding. Both layouts preserve a 7:10 cover. */
public final class BookViews {
    private BookViews() {}

    public static View row(Folio f, View cover, String title, String meta, int percent,
                           boolean last, Runnable open) {
        Structure s = new Structure(f);
        LinearLayout block = s.column();
        LinearLayout row = s.row(Tokens.Row.BOOK);
        row.addView(cover, f.u(40), f.u(40 * 10f / 7));
        LinearLayout info = s.column();
        info.setPadding(f.u(12), 0, f.u(8), 0);
        info.addView(s.text(title, TypeScale.BODY_LG));
        info.addView(s.text(meta, TypeScale.META));
        Progress bar = new Progress(f, 3, false, percent);
        LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(-1, -2);
        track.topMargin = f.u(4);
        info.addView(bar, track);
        s.flex(row, info);
        row.addView(s.text(Math.max(0, Math.min(100, percent)) + "%", TypeScale.META));
        s.action(row, null, open);
        block.addView(row);
        if (!last) s.rule(block, false);
        return block;
    }

    public static View cell(Folio f, View cover, String title, int percent, Runnable open) {
        Structure s = new Structure(f);
        LinearLayout cell = s.column();
        FrameLayout frame = new FrameLayout(f.context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(width * 10f / 7),
                        MeasureSpec.EXACTLY));
            }
        };
        frame.addView(cover, -1, -1);
        cell.addView(frame, -1, -2);
        cell.addView(new Progress(f, 3, false, percent));
        TextView caption = s.text(title, TypeScale.BODY_LG);
        caption.setSingleLine(false);
        caption.setMaxLines(2);
        caption.setPadding(0, f.u(4), 0, 0);
        cell.addView(caption);
        s.action(cell, null, open);
        return cell;
    }
}

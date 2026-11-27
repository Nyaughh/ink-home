package dev.inkhome.folio.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;

/** A centred hairline track with a square-ended fill and optional percentage. */
public final class Progress extends LinearLayout {
    private final View track;
    private final TextView label;
    private int percent;

    public Progress(Folio f, int thickness, boolean showPercent, int initialPercent) {
        super(f.context);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        track = new View(f.context) {
            final Paint paint = new Paint();
            @Override protected void onDraw(Canvas canvas) {
                int top = (getHeight() - f.hairline) / 2;
                paint.setColor(f.palette.rule);
                canvas.drawRect(0, top, getWidth(), top + f.hairline, paint);
                int fillWidth = Math.round(getWidth() * percent / 100f);
                paint.setColor(f.progressInk(fillWidth, getHeight()));
                canvas.drawRect(0, 0, fillWidth, getHeight(), paint);
            }
        };
        addView(track, new LayoutParams(0, f.u(thickness), 1));
        label = showPercent ? new Structure(f).text("", TypeScale.META) : null;
        if (label != null) {
            label.setPadding(f.u(8), 0, 0, 0);
            addView(label);
        }
        setPercent(initialPercent);
    }

    public void setPercent(int value) {
        percent = Math.min(100, Math.max(0, value));
        String description = percent + "%";
        if (label != null) label.setText(description);
        setContentDescription(description);
        track.invalidate();
    }
}

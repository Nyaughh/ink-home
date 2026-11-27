package dev.inkhome.folio.widget;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.widget.CompoundButton;
import dev.inkhome.folio.Folio;
import java.util.function.Consumer;

/** Native checkable semantics with a square Folio track and thumb. */
public final class FolioSwitch extends CompoundButton {
    private final Folio f;
    private final Paint paint = new Paint();

    public FolioSwitch(Folio f, String label, boolean checked, Consumer<Boolean> changed) {
        super(f.context);
        this.f = f;
        setButtonDrawable(null);
        setBackground(null);
        setForeground(DataWidgets.focus(f));
        setMinWidth(f.u(48));
        setMinHeight(f.u(48));
        setGravity(Gravity.CENTER);
        setContentDescription(label);
        setChecked(checked);
        setOnCheckedChangeListener((view, on) -> { invalidate(); changed.accept(on); });
    }

    @Override public CharSequence getAccessibilityClassName() { return "android.widget.Switch"; }

    @Override protected void onDraw(Canvas c) {
        int w = f.u(44), h = f.u(24), inset = f.u(4);
        int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;
        paint.setColor(f.palette.ink);
        c.drawRect(x, y, x + w, y + h, paint);
        if (!isChecked()) {
            int edge = f.hairline;
            paint.setColor(f.palette.paper);
            c.drawRect(x + edge, y + edge, x + w - edge, y + h - edge, paint);
        }
        int side = h - inset * 2;
        int thumb = isChecked() ? x + w - inset - side : x + inset;
        paint.setColor(isChecked() ? f.palette.paper : f.palette.ink);
        c.drawRect(thumb, y + inset, thumb + side, y + h - inset, paint);
    }
}

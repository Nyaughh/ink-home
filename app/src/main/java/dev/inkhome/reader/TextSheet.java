package dev.inkhome.reader;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.Shapes;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.widget.LevelBar;
import java.util.function.IntConsumer;

/** Bottom panel with a fixed header and page-key-accessible settings. */
public final class TextSheet extends LinearLayout {
    private final Folio f;
    private final LinearLayout body;
    private final ScrollView scroll;
    private final int maxHeight;

    public TextSheet(Folio f, String title, int maxHeight, Runnable close) {
        super(f.context);
        this.f = f;
        this.maxHeight = maxHeight;
        setOrientation(VERTICAL);
        setClickable(true);
        setBackground(new Shapes(f).stroke(f.palette.paper, f.palette.ink));
        setPadding(f.u(12), f.hairline, f.u(12), f.hairline);
        LinearLayout header = row();
        header.addView(Type.text(f, title, TypeScale.TITLE_SM, f.palette.ink), weight());
        TextView done = label("Done");
        done.setGravity(Gravity.CENTER);
        done.setMinHeight(f.u(44));
        done.setMinWidth(f.u(52));
        done.setOnClickListener(v -> close.run());
        header.addView(done);
        addView(header);
        scroll = new ScrollView(f.context);
        body = new LinearLayout(f.context);
        body.setOrientation(VERTICAL);
        body.setPadding(0, 0, 0, f.u(8));
        scroll.addView(body);
        addView(scroll, new LayoutParams(-1, -2));
    }

    @Override protected void onMeasure(int width, int height) {
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(
                Math.min(maxHeight, MeasureSpec.getSize(height)), MeasureSpec.AT_MOST));
    }

    public void pageScroll(boolean forward) {
        scroll.pageScroll(forward ? FOCUS_DOWN : FOCUS_UP);
    }

    public void heading(String text) {
        TextView heading = label(text);
        heading.setPadding(0, f.u(6), 0, f.u(3));
        body.addView(heading);
    }

    public void control(View view) { body.addView(view); }

    private TextView label(String text) {
        return Type.text(f, text, TypeScale.LABEL, f.palette.ink);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(f.context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LayoutParams weight() { return new LayoutParams(0, -2, 1); }

    public void sizes(int size, IntConsumer changed) {
        TextView title = label("Text size · " + size + " sp");
        body.addView(title);
        String[] labels = new String[29];
        for (int i = 0; i < labels.length; i++) labels[i] = (12 + i) + " sp";
        LinearLayout sizes = row();
        sizes.addView(label("A"));
        sizes.addView(new LevelBar(f, "Text size", labels, size - 12, i -> {
            title.setText("Text size · " + (12 + i) + " sp");
            changed.accept(12 + i);
        }), weight());
        sizes.addView(Type.text(f, "A", TypeScale.TITLE_SM, f.palette.ink));
        body.addView(sizes);
    }

    public void fonts(String[] names, Typeface[] faces, int selected, IntConsumer changed) {
        heading("Font");
        TextView[] buttons = new TextView[names.length];
        LinearLayout group = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 2 == 0) {
                group = row();
                body.addView(group);
            }
            TextView b = label(names[i]);
            buttons[i] = b;
            b.setTypeface(faces[i]);
            b.setGravity(Gravity.CENTER);
            b.setMinHeight(f.u(36));
            int index = i;
            b.setOnClickListener(v -> {
                for (int j = 0; j < buttons.length; j++) style(buttons[j], j == index);
                changed.accept(index);
            });
            style(b, i == selected);
            group.addView(b, weight());
        }
    }

    private void style(TextView button, boolean selected) {
        button.setSelected(selected);
        button.setTextColor(selected ? f.palette.onInverse : f.palette.ink);
        button.setBackground(new Shapes(f).stroke(selected ? f.palette.inverse : f.palette.paper, f.palette.ink));
    }

    public void pictured(String title, boolean margins, String[] names, int selected, IntConsumer changed) {
        heading(title);
        LinearLayout group = row();
        body.addView(group);
        for (int i = 0; i < names.length; i++) {
            int index = i;
            View b = new View(f.context) {
                private final Paint pen = new Paint();
                @Override protected void onDraw(Canvas canvas) {
                    int w = getWidth(), h = getHeight();
                    float gap = margins ? f.u(4) : f.u(2 + 2 * index);
                    float inset = w * (margins ? .12f + .10f * index : .22f);
                    pen.setColor(isSelected() ? f.palette.onInverse : f.palette.ink);
                    for (int line = -1; line <= 1; line++) {
                        float y = h / 2f + gap * line;
                        canvas.drawRect(inset, y, w - inset - (line == 1 ? w * .12f : 0),
                                y + f.rule, pen);
                    }
                }
            };
            b.setSelected(i == selected);
            b.setBackground(new Shapes(f).stroke(i == selected ? f.palette.inverse : f.palette.paper, f.palette.ink));
            b.setContentDescription(title + ", " + names[i]);
            b.setFocusable(true);
            b.setOnClickListener(v -> {
                for (int j = 0; j < group.getChildCount(); j++) {
                    View option = group.getChildAt(j);
                    option.setSelected(j == index);
                    option.setBackground(new Shapes(f).stroke(j == index ? f.palette.inverse : f.palette.paper, f.palette.ink));
                }
                changed.accept(index);
            });
            group.addView(b, new LayoutParams(0, f.u(36), 1));
        }
    }
}

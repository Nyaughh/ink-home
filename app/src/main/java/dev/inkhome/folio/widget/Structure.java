package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import dev.inkhome.folio.*;
import dev.inkhome.folio.core.*;
import java.util.function.IntConsumer;

import static android.view.ViewGroup.LayoutParams.*;
import static dev.inkhome.folio.core.TypeScale.*;

/** Navigation and content layouts. Callers own the data and supply destination actions. */
public final class Structure {
    final Folio f;
    final Shapes shapes;

    public Structure(Folio folio) {
        f = folio;
        shapes = new Shapes(folio);
    }

    LinearLayout row(int height) {
        LinearLayout row = new LinearLayout(f.context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(f.u(height));
        return row;
    }

    LinearLayout column() {
        LinearLayout column = new LinearLayout(f.context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    TextView text(String value, TypeScale role) {
        TextView text = Type.text(f, value, role, role == META ? f.palette.ink2 : f.palette.ink);
        text.setSingleLine();
        text.setEllipsize(TextUtils.TruncateAt.END);
        return text;
    }

    void flex(LinearLayout row, View child) {
        row.addView(child, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
    }

    void rule(LinearLayout column, boolean strong) {
        View line = new View(f.context);
        line.setBackgroundColor(strong ? f.palette.strong : f.palette.rule);
        column.addView(line, MATCH_PARENT, strong ? f.rule : f.hairline);
    }

    void action(View target, String label, Runnable callback) {
        if (callback == null) return;
        if (label != null) target.setContentDescription(label);
        target.setForeground(DataWidgets.focus(f));
        target.setFocusable(true);
        target.setOnClickListener(v -> callback.run());
        target.setBackground(shapes.pressable());
    }

    ImageView glyph(String name, int level, boolean selected) {
        ImageView image = new ImageView(f.context);
        Glyph glyph = new Glyph(name, level, selected);
        glyph.setTint(f.palette.ink);
        image.setImageDrawable(glyph);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    FrameLayout icon(String name, String label, Runnable callback, int height) {
        FrameLayout target = new FrameLayout(f.context);
        target.setMinimumWidth(f.u(48));
        target.setMinimumHeight(f.u(height));
        target.addView(glyph(name, -1, false), new FrameLayout.LayoutParams(f.u(24), f.u(24), Gravity.CENTER));
        action(target, label, callback);
        return target;
    }

    public View statusStrip(String time, int notifications, int wifi, int battery,
                            boolean charging, Runnable openPanel) {
        return statusStrip(time, notifications, wifi, battery, charging, openPanel, null);
    }

    public View statusStrip(String time, int notifications, int wifi, int battery,
                            boolean charging, Runnable openPanel, Runnable editHome) {
        LinearLayout strip = column();
        LinearLayout row = row(Tokens.Bar.STATUS);
        row.setPadding(f.u(8), 0, f.u(8), 0);
        TextView clock = text(time, BODY);
        clock.setTypeface(Type.sans(f.context, 600));
        flex(row, clock);
        if (notifications > 0) {
            inlineGlyph(row, "bell", -1);
            row.addView(text(String.valueOf(notifications), META));
        }
        inlineGlyph(row, wifi < 0 ? "wifi-off" : "wifi", wifi);
        row.addView(text(f.context.getString(R.string.reader_ui_percent, battery), META));
        inlineGlyph(row, charging ? "battery-charging" : "battery", battery);
        if (editHome == null) inlineGlyph(row, "tune", -1);
        else {
            FrameLayout edit = new FrameLayout(f.context);
            edit.addView(glyph("grid", -1, false), new FrameLayout.LayoutParams(f.u(18), f.u(18), Gravity.CENTER));
            action(edit, f.context.getString(R.string.home_edit_layout), editHome);
            row.addView(edit, f.u(44), f.u(Tokens.Bar.STATUS));
        }
        strip.addView(row);
        rule(strip, false);
        String notificationCount = f.context.getResources().getQuantityString(
                R.plurals.reader_ui_notifications, notifications, notifications);
        String wifiState = f.context.getString(wifi < 0 ? R.string.reader_ui_wifi_off : R.string.reader_ui_wifi_connected);
        action(strip, f.context.getString(charging ? R.string.reader_ui_status_charging : R.string.reader_ui_status,
                time, notificationCount, wifiState, battery), openPanel);
        return strip;
    }

    private void inlineGlyph(LinearLayout row, String name, int level) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(f.u(18), f.u(18));
        lp.setMargins(f.u(4), 0, f.u(4), 0);
        row.addView(glyph(name, level, false), lp);
    }

    public String[] menuLabels() {
        return f.context.getResources().getStringArray(R.array.reader_ui_menu_labels);
    }

    /** Opens the eight-entry menu against the dock's upper edge. */
    public View dock(boolean inSettings, String readingToday, int destination,
                     Runnable openSettingsOrHome, Runnable openStats, IntConsumer select) {
        LinearLayout dock = column();
        rule(dock, true);
        LinearLayout row = row(0);
        row.addView(icon(inSettings ? "home" : "settings", inSettings ? f.context.getString(R.string.reader_ui_home) : f.context.getString(R.string.reader_ui_settings),
                openSettingsOrHome, 50));
        TextView reading = text(readingToday, BODY);
        reading.setGravity(Gravity.CENTER);
        reading.setMinHeight(f.u(50));
        action(reading, f.context.getString(R.string.reader_ui_open_stats, readingToday), openStats);
        flex(row, reading);
        PopupWindow[] popup = new PopupWindow[1];
        FrameLayout toggle = icon("up", f.context.getString(R.string.reader_ui_open_menu), null, 50);
        row.addView(toggle);
        dock.addView(row);
        dock.setMinimumHeight(f.u(Tokens.Bar.DOCK));
        action(toggle, f.context.getString(R.string.reader_ui_open_menu), () -> {
            if (popup[0] != null) {
                popup[0].dismiss();
                return;
            }
            popup[0] = showMenu(dock, destination, select);
            ImageView arrow = (ImageView) toggle.getChildAt(0);
            arrow.setRotation(180f);
            toggle.setContentDescription(f.context.getString(R.string.reader_ui_close_menu));
            popup[0].setOnDismissListener(() -> {
                popup[0] = null;
                arrow.setRotation(0f);
                toggle.setContentDescription(f.context.getString(R.string.reader_ui_open_menu));
            });
        });
        return dock;
    }

    public PopupWindow showMenu(View dock, int current, IntConsumer select) {
        LinearLayout menu = column();
        menu.setBackgroundColor(f.palette.paper);
        rule(menu, true);
        String[] labels = menuLabels();
        String[] icons = {"home","library","stats","apps","search","clean","wifi","tune"};
        int width = dock.getWidth();
        if (f.windowClass() == WindowClass.EXPANDED) width = Math.min(width, f.u(480));
        PopupWindow popup = new PopupWindow(menu, width, WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(f.palette.paper));
        popup.setAnimationStyle(0);
        for (int first = 0; first < labels.length; first += 4) {
            LinearLayout line = row(0);
            for (int i = first; i < first + 4; i++) {
                final int index = i;
                LinearLayout entry = column();
                entry.setGravity(Gravity.CENTER);
                entry.setPadding(f.u(2), f.u(4), f.u(2), f.u(4));
                entry.setMinimumHeight(f.u(58));
                entry.addView(glyph(icons[i], -1, i == current), f.u(24), f.u(24));
                TextView label = text(labels[i], META);
                label.setSingleLine(false);
                label.setGravity(Gravity.CENTER);
                entry.addView(label);
                action(entry, labels[i], () -> { popup.dismiss(); select.accept(index); });
                entry.setSelected(i == current);
                if (i == current) entry.setBackground(shapes.stroke(f.palette.paper, f.palette.strong));
                flex(line, entry);
            }
            menu.addView(line);
        }
        menu.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popup.showAsDropDown(dock, 0, -dock.getHeight() - menu.getMeasuredHeight(), Gravity.END);
        return popup;
    }

    public static final class Action {
        public final String glyph, label;
        public final Runnable run;
        public Action(String glyph, String label, Runnable run) {
            this.glyph = glyph;
            this.label = label;
            this.run = run;
        }
    }

    public View appBar(String title, String count, Runnable back, Action... actions) {
        LinearLayout row = row(Tokens.Bar.APP);
        if (back != null) row.addView(icon("back", f.context.getString(R.string.reader_ui_back), back, 48));
        LinearLayout heading = row(0);
        TextView name = text(title, back == null ? TITLE_LG : TITLE_MD);
        name.setAccessibilityHeading(true);
        heading.addView(name, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
        if (count != null) {
            TextView total = text(count, META);
            total.setPadding(f.u(8), 0, 0, 0);
            heading.addView(total);
            name.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1));
        }
        flex(row, heading);
        for (Action a : actions) row.addView(icon(a.glyph, a.label, a.run, 48));
        return row;
    }

    public View tabs(String[] names, int current, IntConsumer select) {
        LinearLayout block = column();
        HorizontalScrollView scroller = new HorizontalScrollView(f.context);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = row(0);
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout tab = column();
            TextView caption = text(names[i], BODY);
            caption.setGravity(Gravity.CENTER);
            caption.setMinHeight(f.u(Tokens.Bar.TABS - 3));
            if (i == current) caption.setTypeface(Type.sans(f.context, 700));
            tab.addView(caption);
            View mark = new View(f.context);
            if (i == current) mark.setBackgroundColor(f.palette.ink);
            tab.addView(mark, MATCH_PARENT, f.u(3));
            action(tab, names[i], () -> select.accept(index));
            tab.setSelected(i == current);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(f.u(22));
            row.addView(tab, lp);
            if (i == current) tab.post(() -> scroller.scrollTo(Math.max(0, tab.getRight() - scroller.getWidth()), 0));
        }
        scroller.addView(row);
        block.addView(scroller);
        rule(block, false);
        return block;
    }

    public View sectionHead(String title, boolean dense, View end) {
        return sectionHead(title, dense, end, true);
    }

    public View sectionHead(String title, boolean dense, View end, boolean topRule) {
        LinearLayout block = column();
        if (topRule) rule(block, true);
        LinearLayout row = row(dense ? Tokens.Bar.DATA_SECTION : Tokens.Bar.SECTION);
        TextView heading = text(title, LABEL);
        heading.setAccessibilityHeading(true);
        flex(row, heading);
        if (end != null) row.addView(end);
        block.addView(row);
        return block;
    }

    public View listRow(View leading, String title, String meta, View end, int height,
                        boolean last, Runnable activate) {
        LinearLayout block = column();
        LinearLayout row = row(height);
        if (leading != null) row.addView(leading);
        LinearLayout labels = column();
        labels.setPadding(leading == null ? 0 : f.u(12), 0, end == null ? 0 : f.u(8), 0);
        labels.addView(text(title, BODY_LG));
        if (meta != null) labels.addView(text(meta, META));
        flex(row, labels);
        if (end != null) row.addView(end);
        action(row, null, activate);
        block.addView(row);
        if (!last) rule(block, false);
        return block;
    }
}


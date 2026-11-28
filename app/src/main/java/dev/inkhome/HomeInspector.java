package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkhome.folio.widget.Stepper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nonmodal controls over the editor canvas. */
final class HomeInspector extends LinearLayout {
    static final class Position {
        String page;
        boolean expanded;
        void clear() { page = null; expanded = false; }
    }

    private final MainActivity host;
    private final Ink ink;
    private final HomeLayout layout;
    private final HomeLayout.Item item;
    private final HomeComponent component;
    private final Runnable refresh;
    private final Position position;
    private final HomeGrid grid;
    private final ScrollView scroll;
    private final LinearLayout body;
    private final TextView title;
    private final View back;

    HomeInspector(MainActivity host, HomeLayout layout, HomeLayout.Item item, HomeGrid grid,
                  Position position, Runnable refresh, Runnable close, Runnable remove) {
        super(host);
        this.host = host;
        this.ink = host.ink;
        this.layout = layout;
        this.item = item;
        this.grid = grid;
        this.component = HomeComponents.get(item.type);
        this.refresh = refresh;
        this.position = position;
        setOrientation(VERTICAL);
        setBackground(ink.stroke(ink.PAPER, 0, ink.TEXT));
        setClickable(true);
        int edge = ink.folio.rule;
        setPadding(edge, 0, edge, 0);
        LinearLayout header = ink.row();
        header.setPadding(ink.dp(12), ink.dp(2), ink.dp(6), ink.dp(2));
        back = ink.iconButton("back", this::back);
        back.setContentDescription(host.getString(R.string.reader_ui_back));
        header.addView(back, new LayoutParams(ink.dp(48), -1));
        title = ink.oneLine(ink.medium("", 17));
        header.addView(title, ink.weight(1));
        View info = ink.iconButton("info", () -> expand(null));
        info.setVisibility(position.expanded ? GONE : VISIBLE);
        info.setContentDescription(host.getString(R.string.editor_widget_details));
        header.addView(info, new LayoutParams(ink.dp(48), -1));
        View trash = ink.iconButton("trash", remove);
        trash.setContentDescription(host.getString(R.string.editor_remove));
        header.addView(trash, new LayoutParams(ink.dp(48), -1));
        View dismiss = ink.iconButton("close", close);
        dismiss.setContentDescription(host.getString(R.string.service_ui_close));
        header.addView(dismiss, new LayoutParams(ink.dp(48), -1));
        addView(header, new LayoutParams(-1, ink.dp(50)));
        if (!position.expanded) {
            title.setText(component.name(host));
            back.setVisibility(GONE);
            body = null;
            scroll = null;
            return;
        }
        addView(ink.hair());
        body = ink.col();
        body.setPadding(ink.dp(12), ink.dp(8), ink.dp(12), ink.dp(8));
        scroll = ink.scroller(body);
        addView(scroll, new LayoutParams(-1, 0, 1));
        show(position.page);
    }

    boolean back() {
        if (!position.expanded) return false;
        if (position.page != null) show(null);
        else {
            position.clear();
            refresh.run();
        }
        return true;
    }

    void sizes() {
        expand("size");
    }

    private void expand(String page) {
        if (position.expanded) {
            show(page);
        } else {
            position.expanded = true;
            position.page = page;
            refresh.run();
        }
    }

    private void show(String page) {
        position.page = page;
        body.removeAllViews();
        scroll.scrollTo(0, 0);
        back.setVisibility(VISIBLE);
        if (page == null) {
            title.setText(component.name(host));
            movement();
            setting(host.getString(R.string.editor_size), dims(item.w, item.h), true, this::sizes);
            if (component.variants().length > 1)
                setting(host.getString(R.string.editor_style), display(item.variant), true, () -> show("style"));
            for (Map.Entry<String,String[]> opt : component.options().entrySet()) option(opt.getKey(), opt.getValue());
        } else if (page.equals("size")) {
            title.setText(R.string.editor_size);
            dimensions();
        } else if (page.equals("style")) {
            title.setText(R.string.editor_style);
            for (String variant : component.variants()) {
                HomeLayout.Item example = item.copy();
                example.variant = variant;
                Runnable choose = () -> {
                    item.variant = variant;
                    refresh.run();
                };
                LinearLayout row = ink.row();
                padRow(row);
                row.addView(preview(host, grid, layout.cols, component, example, display(variant), ink.dp(52), choose),
                        ink.m(ink.dp(84), ink.dp(52), 0, 3, 12, 3));
                row.addView(ink.body(display(variant)), ink.weight(1));
                if (variant.equals(item.variant)) row.addView(ink.icon("check", 20));
                row.setOnClickListener(v -> choose.run());
                body.addView(row);
                body.addView(ink.hair());
            }
        } else {
            title.setText(component.optionName(host, page));
            String[] values = component.options().get(page);
            String value = item.settings.getOrDefault(page, values[0]);
            boolean shortValues = Arrays.stream(values).allMatch(v -> v.length() <= 2);
            if (shortValues && values.length > 4) {
                LinearLayout row = null;
                int columns = Math.max(1, Math.min(5, (placement(grid, item).width - ink.dp(24)) / ink.dp(62)));
                for (int i = 0; i < values.length; i++) {
                    if (i % columns == 0) {
                        row = ink.row();
                        padRow(row);
                        row.setWeightSum(columns);
                        body.addView(row);
                    }
                    String v = values[i];
                    row.addView(choice(display(v), v.equals(value), true, () -> save(page, v)), ink.weight(1));
                }
            } else {
                for (String v : values) setting(display(v), v.equals(value) ? "✓" : "", false, () -> save(page, v));
            }
        }
    }

    private void save(String key, String value) {
        if (key.equals("borders") && value.equals("default")) item.settings.remove(key);
        else item.settings.put(key, value);
        refresh.run();
    }

    private void option(String key, String[] values) {
        if (values.length == 0) return;
        String label = component.optionName(host, key);
        String value = item.settings.getOrDefault(key, values[0]);
        if (Arrays.asList(values).contains("true") && Arrays.asList(values).contains("false")) {
            int next = (Arrays.asList(values).indexOf(value) + 1) % values.length;
            String current = "borders".equals(key) && "default".equals(value)
                    ? host.getString(R.string.editor_global_default) : display(value);
            setting(label, current, false, () -> save(key, values[next]));
        } else setting(label, display(value), true, () -> show(key));
    }

    private void setting(String name, String value, boolean navigate, Runnable click) {
        LinearLayout row = ink.row();
        padRow(row);
        row.setMinimumHeight(ink.dp(48));
        row.addView(ink.medium(name, 15), ink.weight(1));
        row.addView(ink.body(value));
        if (navigate) row.addView(ink.icon("chevron", 18));
        row.setBackground(ink.pressable());
        row.setOnClickListener(v -> click.run());
        body.addView(row);
        body.addView(ink.hair());
    }

    private String dims(int w, int h) { return host.getString(R.string.editor_dims, w, h); }

    private void padRow(View row) {
        row.setPadding(ink.dp(6), ink.dp(4), ink.dp(6), ink.dp(4));
    }

    private void dimensions() {
        int capacity = layout.cols * layout.rows;
        for (HomeLayout.Item other : layout.items) if (other != item) capacity -= other.w * other.h;
        boolean sideBySide = placement(grid, item).width >= ink.dp(304);
        LinearLayout columns = sideBySide ? ink.row() : ink.col();
        body.addView(columns);
        dimensionColumn(columns, true, capacity, sideBySide);
        View divider = ink.hair();
        columns.addView(divider, new LayoutParams(sideBySide ? ink.folio.hairline : -1,
                sideBySide ? -1 : ink.folio.hairline));
        dimensionColumn(columns, false, capacity, sideBySide);
    }

    private void dimensionColumn(LinearLayout parent, boolean width, int capacity, boolean sideBySide) {
        int current = width ? item.w : item.h;
        List<String> choices = new ArrayList<>();
        int selected = 0;
        for (int n = 1; n <= (width ? layout.cols : layout.rows); n++) {
            int w = width || component.squareFace() ? n : item.w;
            int h = !width || component.squareFace() ? n : item.h;
            if (n != current && (w > layout.cols || h > layout.rows || w * h > capacity || !component.supports(w, h)))
                continue;
            if (n == current) selected = choices.size();
            choices.add(Integer.toString(n));
        }
        String name = host.getString(width ? R.string.editor_columns : R.string.editor_rows);
        LinearLayout column = sideBySide ? ink.col() : ink.row();
        column.setGravity(Gravity.CENTER);
        column.setPadding(ink.dp(4), ink.dp(6), ink.dp(4), ink.dp(6));
        parent.addView(column, sideBySide ? ink.weight(1) : new LayoutParams(-1, -2));
        TextView heading = ink.medium(name, 15);
        if (sideBySide) heading.setPadding(0, 0, 0, ink.dp(6));
        column.addView(heading, sideBySide ? new LayoutParams(-2, -2) : ink.weight(1));
        String[] values = choices.toArray(new String[0]);
        Stepper stepper = new Stepper(ink.folio, name, values, selected, index -> {
            int n = Integer.parseInt(values[index]);
            int w = width || component.squareFace() ? n : item.w;
            int h = !width || component.squareFace() ? n : item.h;
            apply(layout.resize(item, w, h), true);
        });
        column.addView(stepper);
    }

    private int collapsedHeight(int width) {
        measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return getMeasuredHeight();
    }

    boolean needsToolbar(HomeGrid grid, HomeLayout.Item item) {
        if (position.expanded) return false;
        int above = grid.rowSpan(0, item.y);
        int below = grid.getHeight() - grid.rowSpan(0, item.y + item.h);
        return Math.max(above, below) < collapsedHeight(panelWidth(grid));
    }

    private int panelWidth(HomeGrid grid) {
        return Math.min(grid.getWidth(), ink.dp(420));
    }

    FrameLayout.LayoutParams placement(HomeGrid grid, HomeLayout.Item item) {
        int above = grid.rowSpan(0, item.y);
        int below = grid.getHeight() - grid.rowSpan(0, item.y + item.h);
        int gravity = above > below ? Gravity.TOP : Gravity.BOTTOM;
        int width = panelWidth(grid);
        int minExpanded = Math.min(grid.getHeight(), ink.dp(220));
        int height = position.expanded ? Math.min(ink.dp(336), Math.max(minExpanded, grid.getHeight() / 2))
                : collapsedHeight(width);
        if (position.expanded) {
            int room = Math.max(above, below);
            if (room >= minExpanded) height = Math.min(height, room);
            else {
                int left = grid.getWidth() * item.x / layout.cols;
                int right = grid.getWidth() - grid.getWidth() * (item.x + item.w) / layout.cols;
                if (Math.max(left, right) >= ink.dp(300)) {
                    width = Math.min(width, Math.max(left, right));
                    return new FrameLayout.LayoutParams(width, height,
                            gravity | (left > right ? Gravity.LEFT : Gravity.RIGHT));
                }
            }
        }
        return new FrameLayout.LayoutParams(width, height, gravity | Gravity.CENTER_HORIZONTAL);
    }

    private void movement() {
        LinearLayout row = ink.row();
        padRow(row);
        row.addView(caption(host.getString(R.string.editor_move)));
        arrow(row, "\u2190", R.string.editor_left, -1, 0);
        arrow(row, "\u2192", R.string.editor_right, 1, 0);
        arrow(row, "\u2191", R.string.editor_up, 0, -1);
        arrow(row, "\u2193", R.string.editor_down, 0, 1);
        body.addView(row);
        body.addView(ink.hair());
    }

    private void arrow(LinearLayout row, String glyph, int label, int dx, int dy) {
        boolean inside = item.x + dx >= 0 && item.x + item.w + dx <= layout.cols
                && item.y + dy >= 0 && item.y + item.h + dy <= layout.rows;
        TextView button = choice(glyph, false, inside,
                () -> apply(layout.move(item, dx, dy), false));
        button.setContentDescription(host.getString(label));
        row.addView(button, new LayoutParams(0, -2, 1));
    }

    private void apply(HomeLayout.Placement change, boolean resize) {
        if (change == null) {
            host.toast(host.getString(R.string.editor_blocked));
            refresh.run();
            return;
        }
        change.apply();
        if (resize) item.settings.remove("heightMode");
        refresh.run();
    }

    private TextView caption(String label) {
        TextView view = ink.medium(label, 15);
        view.setLayoutParams(new LayoutParams(ink.dp(60), -2));
        return view;
    }

    private TextView choice(String label, boolean selected, boolean enabled, Runnable click) {
        TextView button = ink.pill(label, selected);
        button.setMinWidth(ink.dp(43));
        button.setMinHeight(ink.dp(42));
        button.setSelected(selected);
        button.setEnabled(enabled);
        if (enabled && !selected) button.setOnClickListener(v -> click.run());
        if (!enabled) {
            button.setTextColor(ink.TEXT3);
            button.setBackgroundColor(ink.PAPER);
        }
        return button;
    }

    private String display(String value) {
        switch (value) {
            case "0": return host.getString(R.string.home_settings_auto);
            case "true": return host.getString(R.string.editor_enabled);
            case "false": return host.getString(R.string.editor_disabled);
            default: return value.isEmpty() ? value :
                    value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
        }
    }

    static View preview(MainActivity host, HomeGrid grid, int cols, HomeComponent component, HomeLayout.Item item,
                        String title, int maxHeight, Runnable select) {
        int width = Math.max(1, grid.getWidth() * item.w / cols);
        int height = Math.max(1, grid.rowSpan(item.y, item.h));
        FrameLayout frame = new FrameLayout(host) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int inset = host.ink.dp(6);
                int padding = 2 * inset;
                int available = Math.max(0, Math.min(width, MeasureSpec.getSize(widthSpec) - padding));
                float scale = available / (float) width;
                if (maxHeight > 0) scale = Math.min(scale, Math.max(0, maxHeight - padding) / (float) height);
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                    scale = Math.min(scale, Math.max(0, MeasureSpec.getSize(heightSpec) - padding) / (float) height);
                int w = resolveSize(Math.round(width * scale) + padding, widthSpec);
                int h = resolveSize(Math.round(height * scale) + padding, heightSpec);
                setMeasuredDimension(w, h);
                getChildAt(1).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                View content = getChildAt(0);
                content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                content.setPivotX(0);
                content.setPivotY(0);
                content.setScaleX(scale);
                content.setScaleY(scale);
                content.setTranslationX((w - width * scale) / 2f);
                content.setTranslationY((h - height * scale) / 2f);
            }
        };
        frame.setBackground(host.ink.card(0, true));
        View content = component.render(host, item, true);
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        View pick = new View(host);
        pick.setFocusable(true);
        pick.setContentDescription(title);
        pick.setOnClickListener(v -> select.run());
        frame.addView(pick, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }
}

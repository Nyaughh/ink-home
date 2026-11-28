package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Nonmodal controls over the editor canvas. The canvas retains its live dimensions. */
final class HomeInspector extends LinearLayout {
    static final class Position {
        int scrollY;
        final Map<String, Integer> scrollX = new HashMap<>();

        void clear() {
            scrollY = 0;
            scrollX.clear();
        }
    }

    private final MainActivity host;
    private final Ink ink;
    private final HomeLayout layout;
    private final HomeLayout.Item item;
    private final HomeComponent component;
    private final Runnable refresh;
    private final Position position;
    private final Map<String, HorizontalScrollView> strips = new HashMap<>();
    private final ScrollView scroll;
    private final LinearLayout body;

    HomeInspector(MainActivity host, HomeLayout layout, HomeLayout.Item item, HomeGrid grid,
                  Position position, Runnable refresh, Runnable close, Runnable remove) {
        super(host);
        this.host = host;
        this.ink = host.ink;
        this.layout = layout;
        this.item = item;
        this.component = HomeComponents.get(item.type);
        this.refresh = refresh;
        this.position = position;
        setOrientation(VERTICAL);
        setBackground(ink.stroke(ink.PAPER, 0, ink.TEXT));
        setClickable(true);
        int edge = ink.folio.rule;
        setPadding(edge, edge, edge, edge);

        LinearLayout header = ink.row();
        header.setPadding(ink.dp(12), 0, 0, 0);
        TextView title = ink.oneLine(ink.medium(component.name(host), 17));
        header.addView(title, ink.weight(1));
        header.addView(ink.medium(host.getString(R.string.editor_dims, item.w, item.h), 15));
        View dismiss = ink.iconButton("close", close);
        dismiss.setContentDescription(host.getString(R.string.service_ui_close));
        header.addView(dismiss);
        addView(header);
        addView(ink.hair());

        body = ink.col();
        body.setPadding(ink.dp(8), 0, ink.dp(8), ink.dp(4));
        scroll = ink.scroller(body);
        addView(scroll, new LayoutParams(-1, 0, 1));
        movement();
        dimensions(true);
        dimensions(false);
        styles(grid);
        options(grid);
        TextView removeButton = ink.body(host.getString(R.string.editor_remove));
        removeButton.setMinHeight(ink.dp(44));
        removeButton.setGravity(Gravity.CENTER);
        removeButton.setBackground(ink.pressable());
        removeButton.setOnClickListener(v -> remove.run());
        body.addView(removeButton);
        int previousY = position.scrollY;
        scroll.post(() -> scroll.scrollTo(0, previousY));
    }

    FrameLayout.LayoutParams placement(HomeGrid grid, HomeLayout.Item item) {
        int width = grid.getWidth(), height = grid.getHeight();
        boolean landscape = width >= height && width >= ink.dp(460);
        if (landscape) {
            int edge = item.x * 2 + item.w > HomeLayout.COLS ? Gravity.LEFT : Gravity.RIGHT;
            return new FrameLayout.LayoutParams(Math.min(ink.dp(440), width / 2), -1, edge);
        }
        int center = grid.rowSpan(0, item.y) + grid.rowSpan(item.y, item.h) / 2;
        int gravity = center > height / 2 ? Gravity.TOP : Gravity.BOTTOM;
        return new FrameLayout.LayoutParams(-1, Math.min(height / 2, ink.dp(350)), gravity);
    }

    void remember(Position target) {
        target.scrollY = scroll.getScrollY();
        for (Map.Entry<String, HorizontalScrollView> strip : strips.entrySet()) {
            target.scrollX.put(strip.getKey(), strip.getValue().getScrollX());
        }
    }

    private void movement() {
        LinearLayout row = ink.row();
        row.addView(caption(host.getString(R.string.editor_move)));
        arrow(row, "\u2190", R.string.editor_left, -1, 0);
        arrow(row, "\u2192", R.string.editor_right, 1, 0);
        arrow(row, "\u2191", R.string.editor_up, 0, -1);
        arrow(row, "\u2193", R.string.editor_down, 0, 1);
        body.addView(row);
    }

    private void arrow(LinearLayout row, String glyph, int label, int dx, int dy) {
        boolean inside = item.x + dx >= 0 && item.x + item.w + dx <= HomeLayout.COLS
                && item.y + dy >= 0 && item.y + item.h + dy <= HomeLayout.ROWS;
        TextView button = choice(glyph, false, inside,
                () -> apply(layout.move(item, dx, dy), false));
        button.setContentDescription(host.getString(label));
        row.addView(button, new LayoutParams(0, -2, 1));
    }

    private void apply(HomeLayout.Placement change, boolean resize) {
        if (change == null) {
            host.toast(host.getString(R.string.editor_blocked));
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

    private void dimensions(boolean horizontal) {
        String label = host.getString(horizontal ? R.string.editor_columns : R.string.editor_rows);
        int maximum = horizontal ? HomeLayout.COLS : HomeLayout.ROWS;
        LinearLayout values = ink.row();
        View current = null;
        int capacity = HomeLayout.COLS * HomeLayout.ROWS;
        for (HomeLayout.Item other : layout.items) {
            if (other != item) capacity -= other.w * other.h;
        }
        for (int n = 1; n <= maximum; n++) {
            int w = horizontal ? n : item.w;
            int h = horizontal ? item.h : n;
            boolean selected = item.w == w && item.h == h;
            boolean possible = w * h <= capacity && component.supports(w, h);
            TextView button = choice(Integer.toString(n), selected, possible,
                    () -> apply(layout.resize(item, w, h), true));
            values.addView(button);
            if (selected) current = button;
        }
        LinearLayout line = ink.row();
        line.addView(caption(label));
        line.addView(strip(label, values, current), ink.weight(1));
        body.addView(line);
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

    private HorizontalScrollView strip(String key, LinearLayout values, View selected) {
        HorizontalScrollView rail = new HorizontalScrollView(host);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setHorizontalFadingEdgeEnabled(false);
        rail.setSmoothScrollingEnabled(false);
        rail.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rail.addView(values);
        strips.put(key, rail);
        Integer previousX = position.scrollX.get(key);
        rail.post(() -> {
            if (previousX != null) rail.scrollTo(previousX, 0);
            else if (selected != null) rail.scrollTo(Math.max(0, selected.getRight() - rail.getWidth()), 0);
        });
        return rail;
    }

    private void styles(HomeGrid grid) {
        String[] variants = component.variants();
        if (variants.length < 2) return;
        String label = host.getString(R.string.editor_style);
        body.addView(ink.medium(label, 15));
        LinearLayout styles = ink.row();
        View current = null;
        for (String variant : variants) {
            boolean selected = variant.equals(item.variant);
            HomeLayout.Item example = item.copy();
            example.variant = variant;
            String name = display(variant);
            Runnable choose = () -> {
                if (selected) return;
                item.variant = variant;
                refresh.run();
            };
            View card = previewChoice(grid, example, name, selected, 64, choose);
            styles.addView(card, ink.m(ink.dp(118), -2, 0, 2, 4, 4));
            if (selected) current = card;
        }
        body.addView(strip(label, styles, current));
    }

    private View previewChoice(HomeGrid grid, HomeLayout.Item example, String name,
                               boolean selected, int height, Runnable choose) {
        LinearLayout card = ink.col();
        card.setGravity(Gravity.CENTER);
        card.setBackground(ink.card(0, true));
        card.addView(preview(host, grid, component, example, name, ink.dp(height), choose),
                new LayoutParams(-1, ink.dp(height)));
        card.addView(choice(name, selected, true, choose), new LayoutParams(-1, -2));
        return card;
    }

    private void options(HomeGrid grid) {
        for (Map.Entry<String, String[]> option : component.options().entrySet()) {
            String key = option.getKey();
            String[] values = option.getValue();
            if (values.length == 0) continue;
            String value = item.settings.getOrDefault(key, values[0]);
            String label = component.optionName(host, key);
            LinearLayout choices = ink.row();
            View current = null;
            for (String optionValue : values) {
                boolean selected = optionValue.equals(value);
                Runnable choose = () -> {
                    if (selected) return;
                    item.settings.put(key, optionValue);
                    refresh.run();
                };
                View choice;
                if (values.length <= 2) {
                    HomeLayout.Item example = item.copy();
                    example.settings.put(key, optionValue);
                    choice = previewChoice(grid, example, display(optionValue), selected, 38, choose);
                    choices.addView(choice, ink.m(ink.dp(80), -2, 4, 3, 0, 3));
                } else {
                    choice = choice(display(optionValue), selected, true, choose);
                    choices.addView(choice);
                }
                if (selected) current = choice;
            }
            LinearLayout row = ink.row();
            row.addView(ink.medium(label, 15), ink.weight(1));
            if (values.length <= 2) row.addView(choices);
            else row.addView(strip(key, choices, current), ink.weight(1));
            body.addView(row);
        }
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

    static View preview(MainActivity host, HomeGrid grid, HomeComponent component, HomeLayout.Item item,
                        String title, int maxHeight, Runnable select) {
        int width = Math.max(1, grid.getWidth() * item.w / HomeLayout.COLS);
        int height = Math.max(1, grid.rowSpan(item.y, item.h));
        FrameLayout frame = new FrameLayout(host) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.min(width, MeasureSpec.getSize(widthSpec));
                float scale = available / (float) width;
                if (maxHeight > 0) scale = Math.min(scale, maxHeight / (float) height);
                int w = resolveSize(Math.round(width * scale), widthSpec);
                int h = resolveSize(Math.round(height * scale), heightSpec);
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

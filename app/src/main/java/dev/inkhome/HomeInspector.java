package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkhome.folio.widget.Pager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Nonmodal controls over the editor canvas. */
final class HomeInspector extends LinearLayout {
    static final class Position {
        String page;
        void clear() { page = null; }
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
        setPadding(edge, edge, edge, edge);
        LinearLayout header = ink.row();
        header.setPadding(ink.dp(12), 0, 0, 0);
        back = ink.iconButton("back", this::back);
        back.setContentDescription(host.getString(R.string.reader_ui_back));
        header.addView(back);
        title = ink.oneLine(ink.medium("", 17));
        header.addView(title, ink.weight(1));
        View trash = ink.iconButton("trash", remove);
        trash.setContentDescription(host.getString(R.string.editor_remove));
        header.addView(trash);
        View dismiss = ink.iconButton("close", close);
        dismiss.setContentDescription(host.getString(R.string.service_ui_close));
        header.addView(dismiss);
        addView(header);
        addView(ink.hair());
        body = ink.col();
        body.setPadding(ink.dp(12), 0, ink.dp(12), ink.dp(4));
        scroll = ink.scroller(body);
        addView(scroll, new LayoutParams(-1, 0, 1));
        show(position.page);
    }

    boolean back() {
        if (position.page == null) return false;
        show(null);
        return true;
    }

    void sizes() {
        sizePage = -1;
        show("size");
    }

    private void show(String page) {
        position.page = page;
        body.removeAllViews();
        scroll.scrollTo(0, 0);
        back.setVisibility(page == null ? GONE : VISIBLE);
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
                    changed();
                };
                LinearLayout row = ink.row();
                row.addView(preview(host, grid, layout.cols, component, example, display(variant), ink.dp(52), choose),
                        ink.m(ink.dp(84), ink.dp(52), 0, 3, 12, 3));
                row.addView(ink.body(display(variant)), ink.weight(1));
                if (variant.equals(item.variant)) row.addView(ink.icon("check", 20));
                row.setOnClickListener(v -> choose.run());
                body.addView(row);
            }
        } else {
            title.setText(component.optionName(host, page));
            String[] values = component.options().get(page);
            String value = item.settings.getOrDefault(page, values[0]);
            boolean shortValues = Arrays.stream(values).allMatch(v -> v.length() <= 2);
            if (shortValues && values.length > 4) {
                LinearLayout row = null;
                int columns = Math.max(1, Math.min(5, (getWidth() - ink.dp(24)) / ink.dp(62)));
                for (int i = 0; i < values.length; i++) {
                    if (i % columns == 0) {
                        row = ink.row();
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

    private void changed() {
        position.clear();
        refresh.run();
    }

    private void save(String key, String value) {
        if (key.equals("borders") && value.equals("default")) item.settings.remove(key);
        else item.settings.put(key, value);
        changed();
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
        row.setMinimumHeight(ink.dp(36));
        row.addView(ink.medium(name, 15), ink.weight(1));
        row.addView(ink.body(value));
        if (navigate) row.addView(ink.icon("chevron", 18));
        row.setBackground(ink.pressable());
        row.setOnClickListener(v -> click.run());
        body.addView(row);
        body.addView(ink.hair());
    }

    private String dims(int w, int h) { return host.getString(R.string.editor_dims, w, h); }

    private int sizePage = -1;

    private void dimensions() {
        int capacity = layout.cols * layout.rows;
        for (HomeLayout.Item other : layout.items) if (other != item) capacity -= other.w * other.h;
        List<int[]> sizes = new ArrayList<>();
        for (int w = 1; w <= layout.cols; w++) {
            for (int h = 1; h <= layout.rows; h++) {
                if (component.supports(w, h) && w * h <= capacity) sizes.add(new int[]{w, h});
            }
        }
        int width = getWidth() > 0 ? getWidth() : placement(grid, item).width;
        if (width < 1) width = grid.getWidth();
        int columns = Math.max(1, Math.min(5, (width - ink.dp(24)) / ink.dp(76)));
        int height = placement(grid, item).height;
        if (height < 1) height = grid.getHeight();
        int rows = Math.max(1, (height - ink.dp(100)) / ink.dp(46));
        int perPage = rows * columns;
        int last = Math.max(0, (sizes.size() - 1) / perPage);
        if (sizePage < 0) {
            sizePage = 0;
            for (int i = 0; i < sizes.size(); i++) {
                int[] size = sizes.get(i);
                if (size[0] == item.w && size[1] == item.h) sizePage = i / perPage;
            }
        }
        sizePage = Math.min(sizePage, last);
        LinearLayout row = null;
        for (int i = sizePage * perPage; i < Math.min(sizes.size(), (sizePage + 1) * perPage); i++) {
            if (i % columns == 0) {
                row = ink.row();
                row.setWeightSum(columns);
                body.addView(row);
            }
            int w = sizes.get(i)[0], h = sizes.get(i)[1];
            row.addView(choice(dims(w, h), w == item.w && h == item.h, true,
                    () -> apply(layout.resize(item, w, h), true)), ink.weight(1));
        }
        if (last > 0) {
            Pager pager = new Pager(ink.folio, false,
                    () -> turnSizePage(-1), () -> turnSizePage(1));
            pager.bind(sizePage, last + 1);
            body.addView(pager);
        }
    }

    private void turnSizePage(int step) {
        sizePage = Math.max(0, sizePage + step);
        show("size");
    }

    FrameLayout.LayoutParams placement(HomeGrid grid, HomeLayout.Item item) {
        int width = grid.getWidth(), height = grid.getHeight();
        boolean landscape = width >= height && width >= ink.dp(460);
        if (landscape) {
            int edge = (item.x + item.w) * width / layout.cols - ink.dp(22) > width / 2 ? Gravity.LEFT : Gravity.RIGHT;
            return new FrameLayout.LayoutParams(Math.min(ink.dp(440), width / 2), -1, edge);
        }
        int center = grid.rowSpan(0, item.y + item.h) - ink.dp(22);
        int gravity = center > height / 2 ? Gravity.TOP : Gravity.BOTTOM;
        // Keep the selected corner reachable. On portrait screens borrow the spare
        // space on the other side so the normal settings list fits without scrolling.
        int room = gravity == Gravity.TOP ? center - ink.dp(22) : height - center - ink.dp(22);
        int panelHeight = Math.min(ink.dp(350), Math.min(height * 2 / 3, Math.max(height / 2, room)));
        return new FrameLayout.LayoutParams(-1, panelHeight, gravity);
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
            return;
        }
        change.apply();
        if (resize) item.settings.remove("heightMode");
        changed();
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
                int available = Math.min(width, MeasureSpec.getSize(widthSpec));
                float scale = available / (float) width;
                if (maxHeight > 0) scale = Math.min(scale, maxHeight / (float) height);
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                    scale = Math.min(scale, MeasureSpec.getSize(heightSpec) / (float) height);
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

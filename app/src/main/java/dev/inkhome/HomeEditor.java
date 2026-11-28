package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/** The grid and all controls operate on a draft until the user saves. */
final class HomeEditor {
    private boolean sizing;
    private boolean adjusting;
    private final MainActivity host;
    private HomeLayout original;
    private HomeLayout draft;
    private String selected;
    private HomeGrid grid;

    HomeEditor(MainActivity host) {
        this.host = host;
        for (HomeComponent component : HomeComponents.all()) component.editorStarted(host);
    }

    boolean active() {
        return draft != null;
    }

    void open() {
        if (!active()) {
            original = HomeLayout.load(host);
            draft = original.copy();
            selected = null;
        }
        host.render();
    }

    void show(LinearLayout col) {
        grid = new HomeGrid(host, draft, item -> {
            if (item == null) gallery();
            else inspect(item);
        }, this::gallery, selected);
        grid.setResizeAction(item -> adjust(item, true));
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    View toolbar() {
        LinearLayout bar = host.ink.row();
        if (adjusting) {
            for (HomeLayout.Item item : draft.items) {
                if (item.key.equals(selected)) {
                    adjustToolbar(bar, item);
                    return bar;
                }
            }
        }
        toolbarAction(bar, R.string.editor_add, this::gallery);
        toolbarAction(bar, R.string.editor_reset, () -> host.sheets.message(
                label(R.string.editor_reset_title), label(R.string.editor_reset_note),
                label(R.string.editor_reset), this::reset));
        toolbarAction(bar, R.string.library_ui_cancel, this::cancel);
        toolbarAction(bar, R.string.service_ui_save, this::save);
        return bar;
    }

    private TextView toolbarAction(LinearLayout bar, int title, Runnable action) {
        TextView button = host.ink.medium(label(title), 15);
        button.setGravity(Gravity.CENTER);
        button.setBackground(host.ink.pressable());
        button.setOnClickListener(v -> action.run());
        bar.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
        return button;
    }

    private String label(int res, Object... args) {
        return host.getString(res, args);
    }

    void cancel() {
        discard();
        host.render();
    }

    void discard() {
        if (!active()) return;
        for (HomeComponent module : HomeComponents.all()) module.finish(host, original);
        finish();
    }

    private void finish() {
        selected = null;
        adjusting = false;
        grid = null;
        original = null;
        draft = null;
    }

    private void save() {
        // Record old allocations before replacing the layout. A process restart can
        // finish deleting removed widgets using whichever layout actually persisted.
        for (HomeComponent module : HomeComponents.all()) module.beforeSave(host, original);
        draft.save(host);
        for (HomeComponent module : HomeComponents.all()) module.finish(host, draft);
        finish();
        host.render();
    }

    private void reset() {
        HomeLayout preset = HomeLayout.preset(host);
        for (HomeComponent module : HomeComponents.all()) module.reset(host, original, preset);
        for (HomeComponent module : HomeComponents.all()) module.prune(host, original, preset);
        draft = preset;
        selected = null;
        host.render();
    }

    private void gallery() {
        LinearLayout gallery = panel();
        for (HomeComponent component : HomeComponents.all()) {
            for (String variant : component.variants()) {
                HomeLayout.Item candidate = placement(component, variant);
                boolean available = candidate != null;
                HomeLayout.Item preview = available ? candidate :
                        new HomeLayout.Item(component.id(), variant, 0, 0, HomeLayout.COLS, 3);
                String title = component.name(host);
                if (component.variants().length > 1) title += " / " + pretty(variant);
                card(gallery, component, preview, title, () -> {
                    if (available) add(component, preview, null);
                    else replacements(component, preview, false);
                });
            }
        }
        for (HomeComponent component : HomeComponents.all()) {
            for (HomeLayout.Item saved : component.restorable(host, draft)) {
                action(gallery, label(R.string.editor_restore, component.restoredName(host, saved)), () -> {
                    HomeLayout.Item place = placement(component, saved.variant);
                    if (place == null) {
                        replacements(component, saved, true);
                    } else {
                        occupy(saved, place);
                        component.restored(draft, saved);
                        insert(saved, null);
                    }
                });
            }
        }
        host.sheets.custom(label(R.string.editor_gallery), gallery);
    }

    private void add(HomeComponent component, HomeLayout.Item item, HomeLayout.Item replacement) {
        host.sheets.dismiss();
        HomeLayout session = draft;
        component.configureNew(host, item, () -> {
            if (session != draft || !draft.fits(replacement, item.x, item.y, item.w, item.h)) {
                component.release(host, item);
                return;
            }
            component.added(host, item);
            insert(item, replacement);
        });
    }

    private void insert(HomeLayout.Item item, HomeLayout.Item replacement) {
        adjusting = false;
        if (replacement != null) draft.items.remove(replacement);
        draft.items.add(item);
        for (HomeComponent module : HomeComponents.all()) module.prune(host, original, draft);
        selected = item.key;
        host.sheets.dismiss();
        host.render();
    }

    private HomeLayout.Item placement(HomeComponent component, String variant) {
        // Prefer a full-width, three-row preview. Smaller gaps can still accept
        // the component, and modules with larger minimums get their first fit.
        HomeLayout.Item best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int w = HomeLayout.COLS; w > 0; w--) {
            for (int h = 1; h <= HomeLayout.ROWS; h++) {
                int score = HomeLayout.COLS - w + Math.abs(3 - h);
                if (score >= bestScore || !component.supports(w, h)) continue;
                HomeLayout.Item match = firstSpace(component, variant, w, h);
                if (match != null) {
                    best = match;
                    bestScore = score;
                    if (score == 0) return best;
                }
            }
        }
        return best;
    }

    private HomeLayout.Item firstSpace(HomeComponent component, String variant, int w, int h) {
        for (int y = 0; y <= HomeLayout.ROWS - h; y++) {
            for (int x = 0; x <= HomeLayout.COLS - w; x++) {
                if (draft.fits(null, x, y, w, h))
                    return new HomeLayout.Item(component.id(), variant, x, y, w, h);
            }
        }
        return null;
    }

    private LinearLayout panel() {
        LinearLayout panel = host.ink.col();
        int p = host.ink.dp(12);
        panel.setPadding(p, p, p, p);
        return panel;
    }

    private void action(LinearLayout panel, String title, Runnable run) {
        TextView view = host.ink.body(title);
        view.setMinHeight(host.ink.dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackground(host.ink.pressable());
        view.setOnClickListener(v -> run.run());
        panel.addView(view);
        panel.addView(host.ink.hair());
    }

    private String pretty(String id) {
        return id.isEmpty() ? id : id.substring(0, 1).toUpperCase(Locale.getDefault()) + id.substring(1);
    }

    private void inspect(HomeLayout.Item item) {
        selected = item.key;
        adjusting = false;
        host.render();
        HomeComponent component = HomeComponents.get(item.type);
        LinearLayout panel = panel();
        panel.addView(host.ink.muted(label(R.string.editor_location,
                label(R.string.editor_cell, item.x + 1, item.y + 1),
                label(R.string.editor_dims, item.w, item.h)), 15));
        action(panel, label(R.string.editor_move), () -> adjust(item, false));
        action(panel, label(R.string.editor_size), () -> adjust(item, true));
        String[] variants = component.variants();
        if (variants.length > 1) {
            action(panel, label(R.string.editor_style) + " / " + pretty(item.variant), () -> styles(component, item));
        }
        for (Map.Entry<String,String[]> entry : component.options().entrySet()) {
            String key = entry.getKey();
            String[] choices = entry.getValue();
            if (choices.length == 0) continue;
            String current = item.settings.get(key);
            if (current == null) current = choices[0];
            String option = component.optionName(host, key);
            String value = current;
            action(panel, option + " / " + display(value), () -> choose(option, choices, value, choice -> {
                item.settings.put(key, choice);
                host.render();
            }));
        }
        action(panel, label(R.string.editor_remove), () -> {
            draft.items.remove(item);
            for (HomeComponent module : HomeComponents.all()) module.prune(host, original, draft);
            selected = null;
            host.sheets.dismiss();
            host.render();
        });
        host.sheets.custom(component.name(host), panel);
    }

    private String display(String value) {
        switch (value) {
            case "0": return label(R.string.home_settings_auto);
            case "true": return label(R.string.editor_enabled);
            case "false": return label(R.string.editor_disabled);
            default: return pretty(value);
        }
    }

    private void choose(String title, String[] values, String current, java.util.function.Consumer<String> pick) {
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = display(values[i]);
        host.choose(title, labels, Arrays.asList(values).indexOf(current), i -> pick.accept(values[i]));
    }

    private static void occupy(HomeLayout.Item target, HomeLayout.Item location) {
        target.x = location.x;
        target.y = location.y;
        target.w = location.w;
        target.h = location.h;
    }

    private void replacements(HomeComponent component, HomeLayout.Item candidate, boolean restore) {
        LinearLayout choices = panel();
        for (HomeLayout.Item current : draft.items) {
            if (!component.supports(current.w, current.h)) continue;
            String name = HomeComponents.get(current.type).name(host);
            String title = label(R.string.editor_location, name,
                    label(R.string.editor_cell, current.x + 1, current.y + 1));
            action(choices, title, () -> {
                occupy(candidate, current);
                if (restore) {
                    component.restored(draft, candidate);
                    insert(candidate, current);
                } else add(component, candidate, current);
            });
        }
        if (choices.getChildCount() == 0) {
            host.toast(label(R.string.editor_no_space));
        } else host.sheets.custom(label(R.string.editor_replace), choices);
    }

    private void styles(HomeComponent component, HomeLayout.Item current) {
        LinearLayout styles = panel();
        for (String variant : component.variants()) {
            HomeLayout.Item example = current.copy();
            example.variant = variant;
            card(styles, component, example, pretty(variant), () -> {
                current.variant = variant;
                host.sheets.dismiss();
                host.render();
            });
        }
        host.sheets.custom(label(R.string.editor_style), styles);
    }

    private void card(LinearLayout panel, HomeComponent component, HomeLayout.Item item,
                      String title, Runnable select) {
        panel.addView(host.ink.medium(title, 17));
        final int width = Math.max(1, grid.getWidth() * item.w / HomeLayout.COLS);
        final int height = Math.max(1, grid.rowSpan(item.y, item.h));
        FrameLayout frame = new FrameLayout(host) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.min(width, MeasureSpec.getSize(widthSpec));
                float scale = available / (float) width;
                int displayedHeight = Math.max(1, Math.round(height * scale));
                super.onMeasure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(displayedHeight, MeasureSpec.EXACTLY));
                View content = getChildAt(0);
                content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                content.setPivotX(0);
                content.setPivotY(0);
                content.setScaleX(scale);
                content.setScaleY(scale);
            }
        };
        frame.setBackground(host.ink.card(0, true));
        View preview = component.render(host, item, true);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        View pick = new View(host);
        pick.setFocusable(true);
        pick.setBackground(host.ink.pressable());
        pick.setContentDescription(title);
        pick.setOnClickListener(v -> select.run());
        frame.addView(pick, new FrameLayout.LayoutParams(-1, -1));
        panel.addView(frame, host.ink.m(-2, -2, 0, 4, 0, 12));
    }

    private void adjust(HomeLayout.Item item, boolean resize) {
        selected = item.key;
        adjusting = true;
        sizing = resize;
        host.sheets.dismiss();
        host.render();
    }

    private void adjustToolbar(LinearLayout bar, HomeLayout.Item item) {
        step(bar, item, -1, 0, sizing ? R.string.editor_narrow : R.string.editor_left);
        step(bar, item, 1, 0, sizing ? R.string.editor_wide : R.string.editor_right);
        step(bar, item, 0, -1, sizing ? R.string.editor_short : R.string.editor_up);
        step(bar, item, 0, 1, sizing ? R.string.editor_tall : R.string.editor_down);
        toolbarAction(bar, R.string.service_ui_done, () -> {
            adjusting = false;
            host.render();
        });
    }

    private void step(LinearLayout bar, HomeLayout.Item item, int dx, int dy, int title) {
        int x = item.x, y = item.y, w = item.w, h = item.h;
        if (sizing) {
            w += dx;
            h += dy;
        } else {
            x += dx;
            y += dy;
        }
        boolean free = HomeComponents.get(item.type).supports(w, h) && draft.fits(item, x, y, w, h);
        HomeLayout.Item swap = sizing || free ? null : neighbor(item, dx, dy);
        boolean enabled = free || swap != null;
        TextView button = toolbarAction(bar, title, () -> {
            if (sizing) {
                item.w += dx;
                item.h += dy;
                item.settings.remove("heightMode");
            } else if (swap != null) {
                item.x += dx * swap.w;
                item.y += dy * swap.h;
                swap.x -= dx * item.w;
                swap.y -= dy * item.h;
            } else {
                item.x += dx;
                item.y += dy;
            }
            host.render();
        });
        button.setEnabled(enabled);
        if (!enabled) button.setTextColor(host.ink.TEXT3);
    }

    // Adjacent widgets that occupy the same band can exchange places
    // without changing their sizes or intersecting any other widget.
    private HomeLayout.Item neighbor(HomeLayout.Item item, int dx, int dy) {
        for (HomeLayout.Item other : draft.items) {
            if (dx != 0 && item.y == other.y && item.h == other.h &&
                    (dx < 0 ? other.x + other.w == item.x : item.x + item.w == other.x)) return other;
            if (dy != 0 && item.x == other.x && item.w == other.w &&
                    (dy < 0 ? other.y + other.h == item.y : item.y + item.h == other.y)) return other;
        }
        return null;
    }
}

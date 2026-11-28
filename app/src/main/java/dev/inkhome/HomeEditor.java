package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** The grid and all controls operate on a draft until the user saves. */
final class HomeEditor {
    private FrameLayout canvas;
    private HomeInspector inspector;
    private final HomeInspector.Position position = new HomeInspector.Position();
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

    void open() { open(null); }

    void open(String key) {
        if (!active()) {
            original = HomeLayout.load(host);
            draft = original.copy();
        }
        selected = key;
        inspector = null;
        position.clear();
        host.render();
    }

    void back() {
        if (selected == null) cancel();
        else select(null);
    }

    void show(LinearLayout col) {
        canvas = new FrameLayout(host);
        col.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        rebuildCanvas();
    }

    private void rememberPosition() {
        if (inspector != null) inspector.remember(position);
        inspector = null;
    }

    private void select(HomeLayout.Item item) {
        inspector = null;
        position.clear();
        selected = item == null ? null : item.key;
        rebuildCanvas();
    }

    private void rebuildCanvas() {
        rememberPosition();
        canvas.removeAllViews();
        grid = new HomeGrid(host, draft, item -> {
            if (item != null || selected != null) select(item);
            else gallery();
        }, this::gallery, selected);
        grid.setResizeAction(this::select);
        canvas.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        if (selected == null) return;
        for (HomeLayout.Item item : draft.items) {
            if (!item.key.equals(selected)) continue;
            HomeGrid pending = grid;
            // Build thumbnails from the measured live tracks and draw the sheet with the
            // updated canvas in one frame, without an intermediate e-ink flash.
            ViewTreeObserver observer = host.getWindow().getDecorView().getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                    if (grid != pending || !active()) return true;
                    showInspector(item);
                    return false;
                }
            });
            break;
        }
    }

    private void showInspector(HomeLayout.Item item) {
        inspector = new HomeInspector(host, draft, item, grid, position, this::rebuildCanvas,
                () -> select(null), () -> {
                    draft.items.remove(item);
                    for (HomeComponent module : HomeComponents.all()) module.prune(host, original, draft);
                    select(null);
                });
        canvas.addView(inspector, inspector.placement(grid, item));
    }

    View toolbar() {
        LinearLayout bar = host.ink.row();
        toolbarAction(bar, R.string.editor_add, this::gallery);
        toolbarAction(bar, R.string.editor_reset, () -> host.sheets.message(
                label(R.string.editor_reset_title), label(R.string.editor_reset_note),
                label(R.string.editor_reset), this::reset));
        toolbarAction(bar, R.string.library_ui_cancel, this::cancel);
        toolbarAction(bar, R.string.service_ui_save, this::save);
        return bar;
    }

    private void toolbarAction(LinearLayout bar, int title, Runnable action) {
        TextView button = host.ink.medium(label(title), 15);
        button.setGravity(Gravity.CENTER);
        button.setBackground(host.ink.pressable());
        button.setOnClickListener(v -> action.run());
        bar.addView(button, new LinearLayout.LayoutParams(0, -1, 1));
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
        inspector = null;
        canvas = null;
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
        if (replacement != null) draft.items.remove(replacement);
        draft.items.add(item);
        for (HomeComponent module : HomeComponents.all()) module.prune(host, original, draft);
        inspector = null;
        position.clear();
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

    private void card(LinearLayout panel, HomeComponent component, HomeLayout.Item item,
                      String title, Runnable select) {
        panel.addView(host.ink.medium(title, 17));
        View frame = HomeInspector.preview(host, grid, component, item, title, 0, select);
        panel.addView(frame, host.ink.m(-2, -2, 0, 4, 0, 12));
    }

}

package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.RefreshBudget.Change;
import dev.inkhome.folio.paging.PagedList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** The grid and all controls operate on a draft until the user saves. */
final class HomeEditor {
    private FrameLayout canvas;
    private FrameLayout toolbarSlot;
    private LinearLayout actions;
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
        else if (inspector == null || !inspector.back()) select(null);
    }

    void show(LinearLayout col) {
        canvas = new FrameLayout(host);
        col.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        rebuildCanvas();
    }

    private void select(HomeLayout.Item item) {
        inspector = null;
        position.clear();
        selected = item == null ? null : item.key;
        rebuildCanvas();
    }

    private void rebuildCanvas() {
        if (toolbarSlot != null && toolbarSlot.getChildCount() > 1) {
            toolbarSlot.removeViewAt(1);
            actions.setVisibility(View.VISIBLE);
        }
        inspector = null;
        canvas.removeAllViews();
        grid = new HomeGrid(host, draft, item -> {
            if (item != null || selected != null) select(item);
            else gallery();
        }, this::gallery, selected);
        grid.setDropAction(() -> select(null));
        grid.setResizeAction(item -> {
            if (inspector != null) inspector.sizes();
        });
        grid.setGestureActive(active -> {
            if (inspector != null) inspector.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
        });
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
        if (toolbarSlot != null && inspector.needsToolbar(grid, item)) {
            actions.setVisibility(View.GONE);
            toolbarSlot.addView(inspector, new FrameLayout.LayoutParams(-1, -1));
        } else {
            canvas.addView(inspector, inspector.placement(grid, item));
        }
    }

    View toolbar() {
        toolbarSlot = new FrameLayout(host);
        actions = host.ink.row();
        toolbarAction(actions, R.string.editor_add, this::gallery);
        toolbarAction(actions, R.string.editor_reset, () -> host.sheets.message(
                label(R.string.editor_reset_title), label(R.string.editor_reset_note),
                label(R.string.editor_reset), this::reset));
        toolbarAction(actions, R.string.library_ui_cancel, this::cancel);
        toolbarAction(actions, R.string.service_ui_save, this::save);
        toolbarSlot.addView(actions, new FrameLayout.LayoutParams(-1, -1));
        return toolbarSlot;
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
        toolbarSlot = null;
        actions = null;
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
        if (!preset.changeGrid(draft.cols, draft.rows)) {
            host.toast(label(R.string.editor_blocked));
            return;
        }
        for (HomeComponent module : HomeComponents.all()) module.reset(host, original, preset);
        for (Integer id : draft.hiddenWidgetIds) {
            if (!preset.hiddenWidgetIds.contains(id)) preset.hiddenWidgetIds.add(id);
        }
        for (HomeComponent module : HomeComponents.all()) module.prune(host, original, preset);
        draft = preset;
        selected = null;
        host.render();
    }

    private void gallery() {
        List<Supplier<View>> groups = new ArrayList<>();
        for (HomeComponent component : HomeComponents.all()) {
            groups.add(() -> group(component));
            for (HomeLayout.Item saved : component.restorable(host, draft)) {
                groups.add(() -> {
                    LinearLayout restore = panel();
                    action(restore, label(R.string.editor_restore, component.restoredName(host, saved)), () -> {
                        HomeLayout.Item place = placement(component, saved.variant);
                        if (place == null) replacements(component, saved, true);
                        else {
                            occupy(saved, place);
                            component.restored(draft, saved);
                            insert(saved, null);
                        }
                    });
                    return restore;
                });
            }
        }
        int height = host.ink.dp(144 * Math.max(1f, host.ink.textOnly));
        if (host.store.listScroll()) {
            LinearLayout gallery = host.ink.col();
            for (Supplier<View> group : groups) gallery.addView(group.get(), new LinearLayout.LayoutParams(-1, height));
            host.sheets.custom(label(R.string.editor_gallery), gallery);
        } else {
            PagedList gallery = new PagedList(host.ink.folio, null, groups.size(), height,
                    i -> groups.get(i).get(), 0, ignored -> {}, () -> host.refreshController.changed(canvas, Change.PAGE)) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int limit = host.getWindow().getDecorView().getHeight() * 2 / 3;
                    if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
                        limit = Math.min(limit, MeasureSpec.getSize(heightSpec));
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.min(limit, groups.size() * height),
                            MeasureSpec.EXACTLY));
                }
            };
            gallery.setSwipes(host.store.swipePages(), gallery::next, gallery::previous, gallery::next, gallery::previous);
            host.sheets.paged(label(R.string.editor_gallery), gallery);
        }
    }

    private View group(HomeComponent component) {
        LinearLayout group = panel();
        String name = component.name(host);
        group.addView(host.ink.medium(name, 17));
        LinearLayout variants = host.ink.row();
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(-1, 0, 1);
        strip.topMargin = host.ink.dp(6);
        group.addView(variants, strip);
        for (String variant : component.variants()) {
            HomeLayout.Item place = placement(component, variant);
            HomeLayout.Item example = place == null ? preferredSize(component, variant, false) : place;
            if (example == null) continue;
            Runnable select = () -> {
                if (place == null) replacements(component, example, false);
                else add(component, place, null);
            };
            String style = pretty(variant);
            LinearLayout card = host.ink.col();
            card.setGravity(Gravity.CENTER);
            View preview = HomeInspector.preview(host, grid, draft.cols, component, example, name + " / " + style,
                    host.ink.dp(56), select);
            card.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
            if (component.variants().length > 1) {
                TextView caption = host.ink.oneLine(host.ink.medium(style, 14));
                caption.setGravity(Gravity.CENTER);
                caption.setPadding(host.ink.dp(4), host.ink.dp(3), host.ink.dp(4), 0);
                card.addView(caption);
            }
            card.setOnClickListener(v -> select.run());
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
            if (variants.getChildCount() > 0) cell.setMarginStart(host.ink.dp(8));
            variants.addView(card, cell);
        }
        group.addView(host.ink.hair(), host.ink.m(-1, host.ink.folio.hairline, 0, 8, 0, 0));
        return group;
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
        return preferredSize(component, variant, true);
    }

    private HomeLayout.Item preferredSize(HomeComponent component, String variant, boolean requireSpace) {
        // Prefer a full-width, three-row preview. Smaller gaps can still accept
        // the component, and modules with larger minimums get their first fit.
        HomeLayout.Item best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int w = draft.cols; w > 0; w--) {
            for (int h = 1; h <= draft.rows; h++) {
                int score = draft.cols - w + Math.abs(3 - h);
                if (score >= bestScore || !component.supports(w, h)) continue;
                HomeLayout.Item match = requireSpace ? firstSpace(component, variant, w, h) :
                        new HomeLayout.Item(component.id(), variant, 0, 0, w, h);
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
        for (int y = 0; y <= draft.rows - h; y++) {
            for (int x = 0; x <= draft.cols - w; x++) {
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
        view.setPadding(host.ink.dp(8), host.ink.dp(6), host.ink.dp(8), host.ink.dp(6));
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

    private HomeLayout.Item replacementSpace(HomeComponent component, String variant, HomeLayout.Item current) {
        int width = 0, height = 0;
        for (int w = current.w; w > 0; w--) {
            for (int h = current.h; h > 0; h--) {
                if (w * h > width * height && component.supports(w, h)) {
                    width = w;
                    height = h;
                }
            }
        }
        return width == 0 ? null : new HomeLayout.Item(component.id(), variant, current.x, current.y, width, height);
    }

    private void replacements(HomeComponent component, HomeLayout.Item candidate, boolean restore) {
        LinearLayout choices = panel();
        for (HomeLayout.Item current : draft.items) {
            HomeLayout.Item place = replacementSpace(component, candidate.variant, current);
            if (place == null) continue;
            String name = HomeComponents.get(current.type).name(host);
            String title = label(R.string.editor_location, name,
                    label(R.string.editor_cell, current.x + 1, current.y + 1));
            action(choices, title, () -> {
                occupy(candidate, place);
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

}

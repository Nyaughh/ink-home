package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;
import java.util.List;

final class FavoriteAppsTile extends HomeTiles {
    static final HomeComponent COMPONENT = new BuiltinTile("apps", R.string.home_settings_apps, 1, 2,
            FavoriteAppsTile::new, R.string.home_settings_app_count, "row", "column", "grid");

    private FavoriteAppsTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);

        boolean preset = Boolean.parseBoolean(item.settings.get("readingPreset"));
        boolean stockWidth = item.w == HomeLayout.COLS || item.x == 2 && item.w == 2;
        if (preset && (naturalHeight() || item.x == 2 && item.w == 2) && stockWidth && !compact && !borders && count() == 0 && !"column".equals(item.variant)
                && fits(160, 0)) {
            presetApps();
            tile.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), 0);
            if (tile.getMeasuredHeight() <= height) return;
            tile.removeAllViews();
        }
        List<MainActivity.App> apps = a.favoriteApps();
        int gutter = preset && item.x == 2 && item.w == 2 ? ink.dp(ink.folio.windowClass().gutter) : 0;
        tile.setPadding(gutter, 0, 0, 0);
        int availableWidth = Math.max(1, width - gutter);
        View head = sectionHead(a.getString(R.string.launcher_favorites), compact);
        head.measure(View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY), 0);
        int remaining = height;
        if (naturalHeight() || height >= head.getMeasuredHeight() + ink.dp(36)) {
            tile.addView(head);
            remaining -= head.getMeasuredHeight();
        }
        if (remaining <= 0 || width <= gutter) return;
        if (apps.isEmpty()) {
            TextView hint = label(a.getString(R.string.launcher_empty_favorites_hint), TypeScale.META, 3);
            hint.setMaxLines(Math.max(1, Math.min(3, remaining / Math.max(1, hint.getLineHeight()))));
            if (preset && !compact) hint.setPadding(0, ink.dp(14), 0, ink.dp(10));
            tile.addView(hint);
            action(() -> a.go(MainActivity.Screen.APPS));
            return;
        }
        boolean vertical = "column".equals(item.variant);
        boolean icons = a.store.appIcons();
        int columns = vertical ? 1 : Math.max(1, availableWidth / Math.max(1, ink.dp(compact ? 64 : 84)));
        int requested = Math.min(12, count());
        if (requested > 0) columns = Math.min(columns, requested);
        int size = Math.min(ink.dp(compact ? 28 : 40), Math.min(remaining, availableWidth / columns));
        int gap = ink.dp(compact ? 2 : preset ? 8 : 4);
        TextView sample = label("Ag", TypeScale.APP_NAME, 1);
        sample.measure(0, 0);
        int labelHeight = sample.getMeasuredHeight();
        boolean names = !icons || (vertical ? availableWidth >= size + ink.dp(52)
                : availableWidth / columns >= ink.dp(54) && remaining >= size + gap + labelHeight);
        int rowHeight = vertical ? Math.max(icons ? size : 0, names ? labelHeight : 0)
                : (icons ? size : 0) + (names ? labelHeight : 0) + (icons && names ? gap : 0);
        rowHeight = Math.max(1, Math.min(remaining, Math.max(ink.dp(48), rowHeight + ink.dp(2))));
        int rows = naturalHeight() ? (requested > 0 ? requested : "grid".equals(item.variant) ? 2 : 1)
                : Math.max(1, remaining / rowHeight);
        if ("row".equals(item.variant)) rows = 1;
        int limit = (int) Math.min(apps.size(), (long) columns * rows);
        if (requested > 0) limit = Math.min(limit, requested);
        for (int first = 0; first < limit; first += columns) {
            LinearLayout row = ink.row();
            tile.addView(row, -1, rowHeight);
            for (int i = first; i < first + columns; i++) {
                if (i >= limit) {
                    row.addView(new View(a), ink.weight(1));
                    continue;
                }
                MainActivity.App app = apps.get(i);
                LinearLayout cell = vertical ? ink.row() : ink.col();
                cell.setGravity(vertical ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
                cell.setBackground(ink.pressable());
                cell.setContentDescription(a.displayLabel(app));
                if (icons) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    if (names && vertical) lp.setMarginEnd(gap);
                    if (names && !vertical) lp.bottomMargin = gap;
                    cell.addView(a.appIcon(app, compact ? 28 : 40), lp);
                }
                if (names) {
                    TextView name = label(a.displayLabel(app), TypeScale.APP_NAME, 1);
                    if (!vertical) name.setGravity(Gravity.CENTER);
                    if (vertical) cell.addView(name, ink.weight(1));
                    else cell.addView(name, -1, -2);
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
                cell.setOnClickListener(v -> a.launch(app));
                cell.setOnLongClickListener(v -> { a.appMenu(app); return true; });
            }
        }
    }

    private void presetApps() {
        List<MainActivity.App> favs = a.favoriteApps();
        boolean right = item.x == 2 && item.w == 2;
        if (right) tile.setPadding(tile.getPaddingLeft() + ink.dp(ink.folio.windowClass().gutter),
                tile.getPaddingTop(), tile.getPaddingRight(), tile.getPaddingBottom());
        tile.addView(sectionHead(a.getString(R.string.launcher_favorites), false));
        if (favs.isEmpty()) {
            TextView h = ink.muted(a.getString(R.string.launcher_empty_favorites_hint), 14);
            h.setPadding(0, ink.dp(14), 0, ink.dp(10));
            tile.addView(h);
            return;
        }
        boolean icons = a.store.appIcons();
        float width = a.getResources().getConfiguration().screenWidthDp / ink.folio.uiScale;
        if (right) width = (width - ink.folio.windowClass().gutter) / 2;

        int columns = ink.folio.windowClass().columns(width, 88);
        int cellSpace = this.width - tile.getPaddingLeft();
        columns = Math.min(columns, Math.max(1, cellSpace / Math.max(1, ink.dp(60))));
        float height = a.getResources().getConfiguration().screenHeightDp / ink.folio.uiScale;
        int max = columns * (height >= 780 && "grid".equals(item.variant) ? 2 : 1);

        LinearLayout line = null;
        for (int i = 0; i < Math.min(max, favs.size()); i++) {
            if (i % columns == 0) {
                line = ink.row();
                line.setGravity(Gravity.TOP);
                tile.addView(line, ink.m(-1, -2, 0, 2, 0, 0));
            }
            MainActivity.App app = favs.get(i);
            LinearLayout cell = ink.col();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(ink.dp(2), 0, ink.dp(2), 0);
            cell.setBackground(ink.pressable());
            if (icons) cell.addView(a.appIcon(app, 40));
            TextView t = ink.center(ink.oneLine(ink.text(a.displayLabel(app), TypeScale.APP_NAME, ink.TEXT)));
            t.setPadding(0, icons ? ink.dp(8) : ink.dp(12), 0, icons ? 0 : ink.dp(12));
            cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            cell.setOnClickListener(v -> a.launch(app));
            cell.setOnLongClickListener(v -> { a.appMenu(app); return true; });
            line.addView(cell, ink.weight(1));
        }
        if (line != null) while (line.getChildCount() < columns) line.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
    }

}

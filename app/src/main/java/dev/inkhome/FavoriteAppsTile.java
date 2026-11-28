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

    private FavoriteAppsTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        apps();
    }

    @Override View content() {
        return ink.scroller(tile);
    }

    private void apps() {
        List<MainActivity.App> favorites = a.favoriteApps();
        tile.addView(new dev.inkhome.folio.widget.Structure(ink.folio).sectionHead(
                a.getString(R.string.launcher_favorites), compact, null));
        if (favorites.isEmpty()) {
            tile.addView(label(a.getString(R.string.launcher_empty_favorites_hint), TypeScale.META, 3));
            action(() -> a.go(MainActivity.Screen.APPS));
            return;
        }
        float width = a.getResources().getConfiguration().screenWidthDp / ink.folio.uiScale;
        width = (width - 2 * ink.folio.windowClass().margin) * item.w / HomeLayout.COLS
                + 2 * ink.folio.windowClass().margin;
        boolean vertical = "column".equals(item.variant);
        int columns = vertical ? 1 : ink.folio.windowClass().columns(width, compact ? 72 : 88);
        int specified = Math.min(12, count());
        int limit = specified > 0 ? specified : Math.max(columns, columns * item.h / 2);
        if ("row".equals(item.variant)) {
            if (specified > 0) columns = specified;
            limit = columns;
        }
        LinearLayout row = null;
        for (int i = 0; i < Math.min(limit, favorites.size()); i++) {
            if (i % columns == 0) {
                row = ink.row();
                tile.addView(row, ink.m(-1, -2, 0, compact ? 0 : 2, 0, 0));
            }
            MainActivity.App app = favorites.get(i);
            LinearLayout cell = vertical ? ink.row() : ink.col();
            cell.setGravity(vertical ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            cell.setMinimumHeight(ink.dp(48));
            cell.setBackground(ink.pressable());
            row.addView(cell, ink.weight(1));
            if (a.store.appIcons()) {
                int size = compact ? 28 : 40;
                cell.addView(a.appIcon(app, size), ink.m(ink.dp(size), ink.dp(size), 0, 0,
                        vertical ? 12 : 0, vertical ? 0 : 4));
            }
            TextView name = label(a.displayLabel(app), TypeScale.APP_NAME, 1);
            if (!vertical) name.setGravity(Gravity.CENTER);
            cell.addView(name, vertical ? ink.weight(1) : new LinearLayout.LayoutParams(-1, -2));
            cell.setOnClickListener(v -> a.launch(app));
            cell.setOnLongClickListener(v -> { a.appMenu(app); return true; });
        }
        while (row != null && row.getChildCount() < columns) row.addView(new View(a), ink.weight(1));
    }
}

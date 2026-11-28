package dev.inkhome;

import android.widget.LinearLayout;

import dev.inkhome.folio.core.TypeScale;

import java.util.List;

final class NextBooksTile extends HomeBookTile {
    static final HomeComponent COMPONENT = new BuiltinTile("next", R.string.launcher_up_next, 2, 2,
            NextBooksTile::new, R.string.home_settings_next_count, "list", "covers");

    private NextBooksTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        next();
    }

    private void next() {
        boolean preset = "list".equals(item.variant) && Boolean.parseBoolean(item.settings.get("readingPreset"));
        List<Library.Book> books = orderedBooks();
        if (!books.isEmpty()) books.remove(0);
        if (books.isEmpty() || !a.hasFileAccess()) {
            if (preset) return;
            heading(a.getString(R.string.launcher_up_next));
            if (a.hasFileAccess()) {
                tile.addView(label(a.getString(R.string.launcher_library), TypeScale.BODY, 1));
                action(() -> a.go(MainActivity.Screen.LIBRARY));
            } else emptyBook();
            return;
        }
        if (preset) {
            boolean rightPane = item.x == 2 && item.w == 2;
            if (rightPane) tile.setPadding(tile.getPaddingLeft() + ink.dp(ink.folio.windowClass().gutter),
                    tile.getPaddingTop(), tile.getPaddingRight(), tile.getPaddingBottom());
            tile.addView(ink.hair(), ink.m(-1, ink.folio.rule, 0, rightPane || compact ? 0 : 14, 0, 0));
        }
        if (!preview) a.coverPage = true;
        int limit = count();
        if (limit > 0 && limit < books.size()) books = books.subList(0, limit);
        if ("covers".equals(item.variant)) {
            heading(a.getString(R.string.launcher_up_next));
            if (preview) covers(books);
            else tile.addView(a.bookGrid(item.key, books), new LinearLayout.LayoutParams(-1, 0, 1));
        } else if (preview) {
            heading(a.getString(R.string.launcher_up_next));
            for (int i = 0; i < Math.min(item.h, books.size()); i++) tile.addView(a.homeBookRow(books.get(i)));
        } else {
            tile.addView(a.homeBookList(item.key, books), new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }
}

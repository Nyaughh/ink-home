package dev.inkhome;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.core.TypeScale;
import java.util.List;

final class NextBooksTile extends HomeBookTile {
    static final HomeComponent COMPONENT = new BuiltinTile("next", R.string.launcher_up_next, 2, 2,
            NextBooksTile::new, R.string.home_settings_next_count, "list", "covers") {
        @Override public boolean displaysCovers(MainActivity a, HomeLayout.Item item) {
            return a.hasFileAccess() && a.books.size() > 1;
        }
    };

    private NextBooksTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
        boolean list = "list".equals(item.variant);
        boolean preset = list && Boolean.parseBoolean(item.settings.get("readingPreset"));
        List<Library.Book> books = orderedBooks();
        if (!books.isEmpty()) books.remove(0);
        if (books.isEmpty() || !a.hasFileAccess()) {
            if (preset) return;
            if (fits(72, 72)) heading(a.getString(R.string.launcher_up_next));
            if (a.hasFileAccess()) {
                tile.addView(label(a.getString(R.string.launcher_library), TypeScale.BODY, 1));
                action(() -> a.go(MainActivity.Screen.LIBRARY));
            } else emptyBook();
            return;
        }
        if (count() > 0 && count() < books.size()) books = books.subList(0, count());
        int remaining = height;
        boolean right = preset && item.x == 2 && item.w == 2;
        int gutter = right ? ink.dp(ink.folio.windowClass().gutter) : 0;
        tile.setPadding(gutter, 0, 0, 0);
        if (preset && !borders && fits(0, 68)) {
            int margin = right || compact ? 0 : 14;
            tile.addView(ink.hair(), ink.m(-1, ink.folio.rule, 0, margin, 0, 0));
            remaining -= ink.folio.rule + ink.dp(margin);
        }
        int innerWidth = width - gutter;
        if (list && !naturalHeight() && !preview && innerWidth >= ink.dp(180)
                && remaining >= ink.dp(40 + 52 * Math.max(1f, ink.textOnly))) {
            tile.addView(a.homeBookList(item.key, books), new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }
        TextView head = label(a.getString(R.string.launcher_up_next), TypeScale.LABEL, 1);
        int headHeight = measuredHeight(head, innerWidth);
        if (remaining >= headHeight + ink.dp(list ? 44 : 56)) {
            tile.addView(head);
            remaining -= headHeight;
        }
        if (!list) {
            if (!preview && !naturalHeight() && item.w == HomeLayout.COLS && remaining >= ink.dp(300)) {
                tile.addView(a.bookGrid(item.key, books), new LinearLayout.LayoutParams(-1, 0, 1));
            } else covers(books, remaining);
            return;
        }
        int limit = naturalHeight() ? Math.min(books.size(), Math.max(1, item.h / 2)) : books.size();
        for (int i = 0; i < limit; i++) {
            Library.Book book = books.get(i);
            View row;
            if (innerWidth >= ink.dp(160) && remaining >= ink.dp(48)) {
                row = a.homeBookRow(book);
            } else {
                row = label(book.title, TypeScale.BODY, 1);
                row.setMinimumHeight(Math.min(ink.dp(40), remaining));
                ((TextView) row).setGravity(android.view.Gravity.CENTER_VERTICAL);
                bookAction(row, book);
            }
            int rowHeight = measuredHeight(row, innerWidth);
            if (rowHeight > remaining && i > 0) break;
            tile.addView(row);
            remaining -= rowHeight;
            if (remaining < ink.dp(24)) break;
        }
    }
}

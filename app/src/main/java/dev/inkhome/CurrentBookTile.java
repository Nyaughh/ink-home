package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.core.WindowClass;
import java.util.List;

final class CurrentBookTile extends HomeBookTile {
    static final HomeComponent COMPONENT = new BuiltinTile("current", R.string.settings_ui_current_book, 2, 3,
            CurrentBookTile::new, 0, "cover", "shelf", "minimal") {
        @Override public boolean displaysCovers(MainActivity a, HomeLayout.Item item) {
            return !"minimal".equals(item.variant) && a.hasFileAccess() && !a.books.isEmpty();
        }
    };

    private CurrentBookTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
        boolean preset = Boolean.parseBoolean(item.settings.get("readingPreset"));
        if (!a.hasFileAccess()) {
            if (!preset) emptyBook();
            return;
        }
        if (a.books.isEmpty()) {
            View empty = a.emptyHero();
            if (measuredHeight(empty, width) <= height) tile.addView(empty);
            else emptyBook();
            return;
        }
        List<Library.Book> reading = a.reading();
        if ("shelf".equals(item.variant)) {
            if (!preview && !naturalHeight() && item.w == HomeLayout.COLS && fits(240, 300)) {
                a.homeShelf(tile, reading, item.key);
            } else {
                List<Library.Book> shelf = orderedBooks();
                shelf.removeIf(book -> book.finished && !reading.contains(book));
                if (shelf.isEmpty()) emptyBook();
                else covers(shelf, height);
            }
            return;
        }
        Library.Book book = reading.isEmpty() ? a.newest().get(0) : reading.get(0);
        boolean minimal = "minimal".equals(item.variant);
        if (minimal && !compact && fits(200, 144)) {
            LinearLayout candidate = ink.col();
            a.homeMinimal(candidate, java.util.Collections.singletonList(book));
            if (measuredHeight(candidate, width) <= height) {
                tile.addView(candidate);
                return;
            }
        }
        if (!minimal && fits(232, 136) && !compact) {
            int cover = 104;
            boolean splitPreset = item.x == 0 && item.w == 2 && item.h == HomeLayout.ROWS;
            boolean presetHero = preset && (naturalHeight() || splitPreset);
            if (presetHero) {
                int screen = Math.round(a.getResources().getConfiguration().screenHeightDp / ink.folio.uiScale);
                WindowClass window = ink.folio.windowClass();
                cover = window == WindowClass.EXPANDED ? 168
                        : screen < 500 && window == WindowClass.MEDIUM ? 104
                        : Math.min(a.homeSetupShown ? 100 : 116, Math.max(72, (screen - 320) / 2));
            }
            boolean stock = presetHero && !borders && width >= ink.dp(cover + 132);
            if (!stock) cover = Math.min(cover, Math.round(width * 38f / ink.dp(100)));
            View hero = a.homeBookHero(book, reading.isEmpty(), cover, !stock);
            if (measuredHeight(hero, width) <= height) {
                tile.addView(hero);
                return;
            }
        }
        summary(book, minimal);
    }

    private void summary(Library.Book book, boolean minimal) {
        LinearLayout row = ink.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        int textWidth = width;
        if (!minimal && fits(96, 24)) {
            int coverWidth = Math.min(width / 3, Math.min(ink.dp(compact ? 52 : 72), Math.round(height * .7f)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(coverWidth, Math.round(coverWidth * 10f / 7));
            lp.setMarginEnd(ink.dp(8));
            row.addView(ink.cover(Covers.get(a, book, a.store.realCovers())), lp);
            textWidth -= coverWidth + lp.getMarginEnd();
        }
        LinearLayout info = ink.col();
        TextView title = label(book.title, compact || !fits(160, 70) ? TypeScale.BODY : TypeScale.TITLE_SM, 2);
        if (measuredHeight(title, textWidth) > height) title.setMaxLines(1);
        info.addView(title);
        TextView status = label(a.bookStatus(book), TypeScale.META, 1);
        if (measuredHeight(title, textWidth) + measuredHeight(status, textWidth) <= height) info.addView(status);
        row.addView(info, ink.weight(1));
        bookAction(row, book);
        tile.addView(row);
    }
}

package dev.inkhome;

import android.view.View;
import java.util.List;

final class CurrentBookTile extends HomeBookTile {
    static final HomeComponent COMPONENT = new BuiltinTile("current", R.string.settings_ui_current_book, 2, 3,
            CurrentBookTile::new, 0, "cover", "shelf", "minimal");

    private CurrentBookTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
        current();
    }

    @Override View content() {
        return !"shelf".equals(item.variant) && item.h < 5 ? ink.scroller(tile) : tile;
    }

    private void current() {
        if (!a.hasFileAccess()) {
            emptyBook();
            return;
        }
        if (!preview && !"minimal".equals(item.variant)) a.coverPage |= !a.books.isEmpty();
        List<Library.Book> reading = a.reading();
        if ("minimal".equals(item.variant) && !a.books.isEmpty()) {
            a.homeMinimal(tile, reading.isEmpty() ? a.newest() : reading);
            return;
        }
        if ("shelf".equals(item.variant) && !preview) {
            a.homeShelf(tile, reading, item.key);
            return;
        }
        if (a.books.isEmpty()) {
            tile.addView(a.emptyHero());
            return;
        }
        if ("shelf".equals(item.variant)) {
            covers(orderedBooks());
            return;
        }
        Library.Book book = reading.isEmpty() ? a.newest().get(0) : reading.get(0);
        float gridHeight = a.getResources().getConfiguration().screenHeightDp / ink.folio.uiScale - 110;
        int coverWidth = Math.max(40, Math.min(compact ? 72 : 116,
                (int) (gridHeight * item.h / HomeLayout.ROWS * .7f)));
        tile.addView(a.homeBookHero(book, reading.isEmpty(), coverWidth, item.h < 5));
    }
}

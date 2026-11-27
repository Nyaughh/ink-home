package dev.inkhome;

import android.widget.LinearLayout;
import java.util.List;

final class HomeScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        col.setPadding(host.ink.dp(MainActivity.PAD), host.ink.dp(20), host.ink.dp(MainActivity.PAD), host.ink.dp(16));
        host.applyWallpaper();
        String layout = host.store.homeLayout();
        List<Library.Book> reading = host.reading();

        if (!host.hasFileAccess()) {
            col.addView(host.note("Show your books", "Let Ink Home read your book files for covers, titles and progress.", host::requestFileAccess));
        } else if (layout.equals("minimal")) {
            host.homeMinimal(col, reading);
        } else if (layout.equals("shelf")) {
            host.coverPage = !host.books.isEmpty();
            host.homeShelf(col, reading);
        } else {
            host.coverPage = !host.books.isEmpty();
            host.homeCover(col, reading);
        }
        // The book list (if any) takes the free space; otherwise a spacer pushes favorites down.
        if (col.findViewWithTag("list") == null) col.addView(host.ink.vflex());
        host.homeFavorites(col);
    }
}

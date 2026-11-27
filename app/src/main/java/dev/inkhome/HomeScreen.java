package dev.inkhome;

import android.widget.LinearLayout;
import java.util.List;

final class HomeScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        int margin = host.ink.dp(host.ink.folio.windowClass().margin);
        col.setPadding(margin, host.ink.dp(16), margin, host.ink.dp(8));
        host.applyWallpaper();
        String layout = host.store.homeLayout();
        List<Library.Book> reading = host.reading();

        boolean setupShown = SetupScreen.addHomeCallout(host, col);
        if (!host.hasFileAccess()) {
            if (!setupShown) col.addView(dev.inkhome.folio.widget.Callout.create(host.ink.folio,
                    "Show your books", "Let Ink Home read your book files for covers, titles and progress.",
                    "Allow file access", v -> host.requestFileAccess()));
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
        if (col.findViewWithTag("list") == null) {
            LinearLayout right = col.findViewWithTag("homeRight");
            (right == null ? col : right).addView(host.ink.vflex());
        }
        host.homeFavorites(col);
    }
}

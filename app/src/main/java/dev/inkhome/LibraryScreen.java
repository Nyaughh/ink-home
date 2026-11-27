package dev.inkhome;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import android.view.View;
import dev.inkhome.folio.widget.Chip;
import java.util.List;


final class LibraryScreen implements Screen {
    static final String[] SORT_KEYS = {"recent", "title", "author", "added", "progress"};
    static final String[] SORT_LABELS = {"Recently read", "Title", "Author", "Recently added", "Progress"};
    static final String[] TYPE_KEYS = {"all", "book", "comic", "pdf"};
    static final String[] TYPE_LABELS = {"All types", "Books", "Comics", "PDFs"};

    @Override
    public void build(MainActivity host) {
        if (host.libSearch != null) { new SearchScreen().build(host); return; }

        LinearLayout col = host.page();
        ImageView search = host.ink.iconButton("search", () -> { host.libSearch = ""; host.pages.put("lib", 0); host.render(); });
        ImageView view = host.ink.iconButton(host.store.libraryGrid() ? "list" : "grid", () -> { host.store.setLibraryGrid(!host.store.libraryGrid()); host.pages.put("lib", 0); host.render(); });
        Runnable back = host.libCollection != null ? () -> { host.libCollection = null; host.render(); } : null;
        boolean collections = host.libCollection == null && host.libTab.equals("collections");
        ImageView options = host.ink.iconButton(collections ? "plus" : "filter", () -> {
            if (collections) host.newCollection(); else host.libraryOptions();
        });
        search.setContentDescription("Search books");
        options.setContentDescription(collections ? "New collection" : "Sort and filter");
        view.setContentDescription(host.store.libraryGrid() ? "List view" : "Grid view");
        String title = host.libCollection != null ? host.libCollection : "Library";
        String count = host.booksLoaded ? String.valueOf(host.books.size()) : null;
        View[] actions = collections ? new View[]{search, options} : new View[]{search, options, view};
        col.addView(host.header(title, count, back, actions));

        LinearLayout body = host.ink.col();
        int margin = host.ink.dp(host.ink.folio.windowClass().margin);
        body.setPadding(margin, 0, margin, 0);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (host.libCollection == null) {
            String[] keys = {"reading", "all", "finished", "collections"};
            body.addView(host.tabs(new String[]{"Reading", "All", "Finished", "Collections"}, Arrays.asList(keys).indexOf(host.libTab),
                    i -> { host.libTab = keys[i]; host.pages.put("lib", 0); host.render(); }));
        }
        if (!host.hasFileAccess()) {
            body.addView(host.note("Allow file access", "Ink Home needs it to list your books.", host::requestFileAccess), host.ink.m(-1, -2, 0, 16, 0, 0));
            return;
        }
        if (host.libCollection == null && host.libTab.equals("collections")) {
            body.addView(host.collectionsList(), host.ink.m(-1, 0, 0, 12, 0, 0));
            ((LinearLayout.LayoutParams) body.getChildAt(body.getChildCount() - 1).getLayoutParams()).weight = 1;
            return;
        }

        LinearLayout filters = host.ink.row();
        int type = Arrays.asList(TYPE_KEYS).indexOf(host.store.libraryType());
        if (type > 0) filters.addView(Chip.create(host.ink.folio, TYPE_LABELS[type] + " only", v -> {
            host.store.setLibraryType("all");
            host.pages.put("lib", 0);
            host.render();
        }));
        int sort = Arrays.asList(SORT_KEYS).indexOf(host.store.librarySort());
        if (sort > 0) filters.addView(Chip.create(host.ink.folio, "Sorted by " + SORT_LABELS[sort].toLowerCase(java.util.Locale.ROOT), v -> {
            host.store.setLibrarySort("recent");
            host.pages.put("lib", 0);
            host.render();
        }), host.ink.m(-2, -2, type > 0 ? 8 : 0, 0, 0, 0));
        if (filters.getChildCount() > 0) body.addView(filters, host.ink.m(-1, -2, 0, 8, 0, 4));

        List<Library.Book> list = host.filterBooks(false);
        if (list.isEmpty()) {
            String msg;
            if (!host.booksLoaded) msg = "Looking for books…";
            else if (host.libCollection != null) msg = "This collection is empty. Long-press a book and choose Add to collection.";
            else if (host.libTab.equals("reading")) msg = "Books you open show up here, with your progress and time read.";
            else if (host.libTab.equals("finished")) msg = "Finished books show up here. Long-press a book to mark it finished.";
            else msg = "No books found. Copy EPUB, PDF, MOBI, FB2, DJVU, CBZ or CBR files to the tablet.";
            TextView t = host.ink.muted(msg, 15);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, host.ink.dp(18), 0, 0);
            body.addView(t);
            return;
        }
        host.coverPage = host.store.libraryGrid();
        body.addView(host.bookPages(list, host.store.libraryGrid()), new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

package dev.inkhome;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LibraryScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        if (host.libSearch != null) { new SearchScreen().build(host); return; }

        LinearLayout col = host.page();
        ImageView search = host.ink.iconButton("search", () -> { host.libSearch = ""; host.pages.put("lib", 0); host.render(); });
        ImageView view = host.ink.iconButton(host.store.libraryGrid() ? "list" : "grid", () -> { host.store.setLibraryGrid(!host.store.libraryGrid()); host.pages.put("lib", 0); host.render(); });
        Runnable back = host.libCollection != null ? () -> { host.libCollection = null; host.render(); } : null;
        col.addView(host.header(host.libCollection != null ? host.libCollection : "Library", host.booksLoaded ? String.valueOf(host.books.size()) : null, back, search, view));

        LinearLayout body = host.ink.col();
        body.setPadding(host.ink.dp(MainActivity.PAD), 0, host.ink.dp(MainActivity.PAD), 0);
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

        LinearLayout pills = host.ink.row();
        pills.setPadding(0, host.ink.dp(12), 0, host.ink.dp(10));
        Map<String, String> sorts = new LinkedHashMap<>();
        sorts.put("recent", "Recent"); sorts.put("title", "Title"); sorts.put("author", "Author"); sorts.put("added", "Added"); sorts.put("progress", "Progress");
        TextView sort = host.ink.pill("Sort: " + sorts.get(host.store.librarySort()), false);
        sort.setOnClickListener(v -> host.chooseSort());
        pills.addView(sort);
        Map<String, String> types = new LinkedHashMap<>();
        types.put("all", "All types"); types.put("book", "Books"); types.put("comic", "Comics"); types.put("pdf", "PDFs");
        TextView type = host.ink.pill(types.get(host.store.libraryType()), !host.store.libraryType().equals("all"));
        type.setOnClickListener(v -> host.chooseType());
        pills.addView(type, host.ink.m(-2, -2, 8, 0, 0, 0));
        body.addView(pills);
        body.addView(host.ink.hair());

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

package dev.inkhome;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import android.view.View;
import dev.inkhome.folio.paging.PageLayout;
import dev.inkhome.folio.widget.Chip;
import java.util.List;


final class LibraryScreen implements Screen {
    static final String[] SORT_KEYS = {"recent", "title", "author", "added", "progress"};
    static final String[] TYPE_KEYS = {"all", "book", "comic", "pdf"};

    @Override
    public void build(MainActivity host) {
        if (host.libSearch != null) { new SearchScreen().build(host); return; }

        boolean expanded = host.ink.folio.windowClass() == dev.inkhome.folio.core.WindowClass.EXPANDED;
        if (!expanded) host.libraryDetail = null;
        LinearLayout col = host.page();
        ImageView search = host.ink.iconButton("search", () -> { host.libSearch = ""; host.pages.put("lib", 0); host.render(); });
        ImageView view = host.ink.iconButton(host.store.libraryGrid() ? "list" : "grid", () -> { host.store.setLibraryGrid(!host.store.libraryGrid()); host.pages.put("lib", 0); host.render(); });
        Runnable back = host.libCollection != null ? () -> { host.libraryDetail = null; host.libCollection = null; host.render(); } : null;
        boolean collections = host.libCollection == null && host.libTab.equals("collections");
        ImageView options = host.ink.iconButton(collections ? "plus" : "filter", () -> {
            if (collections) host.newCollection(); else host.libraryOptions();
        });
        search.setContentDescription(host.getString(R.string.library_ui_search_books));
        options.setContentDescription(collections ? host.getString(R.string.library_ui_new_collection) : host.getString(R.string.library_ui_sort_and_filter));
        view.setContentDescription(host.store.libraryGrid() ? host.getString(R.string.library_ui_list_view) : host.getString(R.string.library_ui_grid_view));
        String title = host.libCollection != null ? host.libCollection : host.getString(R.string.library_ui_library);
        String count = host.booksLoaded ? String.valueOf(host.books.size()) : null;
        View[] actions = collections ? new View[]{search, options} : new View[]{search, options, view};
        col.addView(host.header(title, count, back, actions));

        LinearLayout body = host.ink.col();
        int margin = host.ink.dp(host.ink.folio.windowClass().margin);
        body.setPadding(margin, 0, margin, 0);
        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout tabRow = null;
        View tabs = null;
        if (host.libCollection == null) {
            String[] keys = {"reading", "all", "finished", "collections"};
            tabs = host.tabs(new String[]{host.getString(R.string.library_ui_reading), host.getString(R.string.library_ui_all), host.getString(R.string.library_ui_finished), host.getString(R.string.library_ui_collections)}, Arrays.asList(keys).indexOf(host.libTab),
                    i -> { host.libraryDetail = null; host.libTab = keys[i]; host.pages.put("lib", 0); host.render(); });
            tabRow = host.ink.row();
            tabRow.addView(tabs, new LinearLayout.LayoutParams(0, -2, 1));
            body.addView(tabRow);
        }
        if (!host.hasFileAccess()) {
            body.addView(host.note(host.getString(R.string.library_ui_allow_file_access), host.getString(R.string.library_ui_file_access_description), host::requestFileAccess), host.ink.m(-1, -2, 0, 16, 0, 0));
            return;
        }
        if (host.libCollection == null && host.libTab.equals("collections")) {
            body.addView(host.collectionsList(), host.ink.m(-1, 0, 0, 12, 0, 0));
            ((LinearLayout.LayoutParams) body.getChildAt(body.getChildCount() - 1).getLayoutParams()).weight = 1;
            return;
        }

        LinearLayout filters = host.ink.row();
        int type = Arrays.asList(TYPE_KEYS).indexOf(host.store.libraryType());
        if (type > 0) filters.addView(Chip.create(host.ink.folio, host.getString(R.string.library_ui_type_filter, host.getResources().getStringArray(R.array.library_type_labels)[type]), v -> {
            host.store.setLibraryType("all");
            host.pages.put("lib", 0);
            host.render();
        }));
        int sort = Arrays.asList(SORT_KEYS).indexOf(host.store.librarySort());
        if (sort > 0) filters.addView(Chip.create(host.ink.folio, host.getString(R.string.library_ui_sort_filter, host.getResources().getStringArray(R.array.library_sort_labels)[sort].toLowerCase(java.util.Locale.ROOT)), v -> {
            host.store.setLibrarySort("recent");
            host.pages.put("lib", 0);
            host.render();
        }), host.ink.m(-2, -2, type > 0 ? 8 : 0, 0, 0, 0));
        if (filters.getChildCount() > 0) body.addView(filters, host.ink.m(-1, -2, 0, 8, 0, 4));

        List<Library.Book> list = host.filterBooks(false);
        Library.Book detail = null;
        if (host.libraryDetail != null) {
            for (Library.Book book : list) {
                if (host.libraryDetail.equals(book.path)) { detail = book; break; }
            }
            if (detail == null && host.booksLoaded) host.libraryDetail = null;
        }
        if (list.isEmpty()) {
            String msg;
            if (!host.booksLoaded) msg = host.getString(R.string.library_ui_looking_for_books);
            else if (host.libCollection != null) msg = host.getString(R.string.library_ui_empty_collection);
            else if (host.libTab.equals("reading")) msg = host.getString(R.string.library_ui_empty_reading);
            else if (host.libTab.equals("finished")) msg = host.getString(R.string.library_ui_empty_finished);
            else msg = host.getString(R.string.library_ui_empty_library);
            TextView t = host.ink.muted(msg, 15);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, host.ink.dp(18), 0, 0);
            body.addView(t);
            return;
        }
        host.coverPage = host.store.libraryGrid();
        if (detail != null) {
            LinearLayout panes = host.ink.row();
            body.addView(panes, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout info = host.ink.col();
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(0, -1, 2);
            bounds.leftMargin = host.ink.dp(host.ink.folio.windowClass().gutter);
            panes.addView(info, bounds);
            View close = host.ink.iconButton("close", () -> { host.libraryDetail = null; host.render(); });
            close.setContentDescription(host.getString(R.string.library_ui_close_book_details));
            info.addView(host.header(host.getString(R.string.library_ui_book_details), null, null, close));
            info.addView(host.scrollable(host.bookInformation(detail, true), "detail:" + detail.path),
                    new LinearLayout.LayoutParams(-1, 0, 1));
            // The most recently bound pager owns the hardware keys.
            panes.addView(host.bookPages(list, host.store.libraryGrid()), 0, new LinearLayout.LayoutParams(0, -1, 3));
            return;
        }
        View books = host.bookPages(list, host.store.libraryGrid());
        if (tabRow != null && books instanceof PageLayout) {
            tabs.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            ((PageLayout) books).offerInlinePager(tabRow, tabs.getMeasuredWidth(),
                    host.store.libraryGrid() ? 8 : 6);
        }
        body.addView(books, new LinearLayout.LayoutParams(-1, 0, 1f));
    }
}

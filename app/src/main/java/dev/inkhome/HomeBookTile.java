package dev.inkhome;

import android.view.View;
import android.widget.LinearLayout;

import dev.inkhome.folio.core.TypeScale;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

class HomeBookTile extends HomeTiles {
    HomeBookTile(MainActivity host, HomeLayout.Item item, boolean preview) {
        super(host, item, preview);
    }

    void emptyBook() {
        boolean access = a.hasFileAccess();
        int title = !access ? R.string.library_ui_allow_file_access :
                a.booksLoaded ? R.string.launcher_no_book_yet : R.string.launcher_opening_your_library;
        tile.addView(label(a.getString(title), TypeScale.BODY, 2));
        action(() -> {
            if (access) a.go(MainActivity.Screen.LIBRARY);
            else a.requestFileAccess();
        });
    }

    List<Library.Book> orderedBooks() {
        LinkedHashSet<Library.Book> result = new LinkedHashSet<>(a.reading());
        result.addAll(a.newest());
        return new ArrayList<>(result);
    }

    void covers(List<Library.Book> books) {
        LinearLayout row = ink.row();
        tile.addView(row);
        for (int i = 0; i < Math.min(item.w, books.size()); i++) {
            Library.Book book = books.get(i);
            View cell = dev.inkhome.folio.widget.BookViews.cell(ink.folio,
                    ink.cover(Covers.get(a, book, a.store.realCovers())), book.title,
                    book.finished ? 100 : Math.max(0, book.percent()), () -> a.openBook(book));
            row.addView(cell, ink.weight(1));
        }
    }
}

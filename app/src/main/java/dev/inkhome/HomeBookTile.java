package dev.inkhome;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.core.TypeScale;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

class HomeBookTile extends HomeTiles {
    HomeBookTile(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        super(host, item, preview, width, height);
    }

    int measuredHeight(View view, int availableWidth) {
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, availableWidth), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);
        return view.getMeasuredHeight();
    }

    void emptyBook() {
        boolean access = a.hasFileAccess();
        int title = !access ? R.string.library_ui_allow_file_access :
                a.booksLoaded ? R.string.launcher_no_book_yet : R.string.launcher_opening_your_library;
        tile.addView(label(a.getString(title), TypeScale.BODY, fits(0, 52) ? 2 : 1));
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

    void bookAction(View view, Library.Book book) {
        view.setContentDescription(book.title);
        view.setBackground(ink.pressable());
        view.setOnClickListener(v -> a.openBook(book));
        view.setOnLongClickListener(v -> { a.bookMenu(book); return true; });
    }

    void covers(List<Library.Book> books, int availableHeight) {
        if (books.isEmpty()) return;
        LinearLayout row = ink.row();
        row.setGravity(Gravity.TOP);
        tile.addView(row);
        int columns = Math.min(books.size(), Math.max(1, width / Math.max(1, ink.dp(compact ? 64 : 88))));
        int cellWidth = width / columns;
        int gap = ink.dp(compact ? 2 : 4);
        for (int i = 0; i < columns; i++) {
            Library.Book book = books.get(i);
            LinearLayout cell = ink.col();
            cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            TextView title = label(book.title, TypeScale.META, 1);
            int captionHeight = measuredHeight(title, cellWidth);
            boolean caption = availableHeight >= ink.dp(36) + gap + captionHeight;
            int coverHeight = Math.min(ink.dp(compact ? 96 : 144),
                    Math.min(Math.round(Math.max(1, cellWidth - gap) * 10f / 7),
                            availableHeight - (caption ? captionHeight + gap : 0)));
            if (coverHeight > 0) {
                cell.addView(ink.cover(Covers.get(a, book, a.store.realCovers())),
                        Math.max(1, Math.round(coverHeight * .7f)), coverHeight);
            }
            if (caption) {
                title.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.topMargin = gap;
                cell.addView(title, params);
            }
            bookAction(cell, book);
            row.addView(cell, ink.weight(1));
        }
    }
}

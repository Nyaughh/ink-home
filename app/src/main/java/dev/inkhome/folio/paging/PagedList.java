package dev.inkhome.folio.paging;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.Pagination;

public class PagedList extends PageLayout {
    private final int count, rowHeight;
    private final IntFunction<View> builder;
    private final LinearLayout rows;
    private int columns;

    public PagedList(Folio f, LinearLayout header, int count, int rowHeight, IntFunction<View> builder,
                     int page, IntConsumer remember, Runnable changed) {
        super(f, header, page, remember, changed);
        this.count = count;
        this.rowHeight = Math.max(1, rowHeight);
        this.builder = builder;
        rows = new LinearLayout(f.context);
        rows.setOrientation(VERTICAL);
        install(rows);
    }

    int columns(int width) { return 1; }
    int gutter() { return 0; }

    @Override Pagination paginate(int width, int height, int footer) {
        columns = columns(width);
        return Pagination.uniform(count, columns, rowHeight, height, footer);
    }

    @Override void show(int start, int end) {
        rows.removeAllViews();
        for (int index = start; index < end; index += columns) {
            View row;
            if (columns == 1) row = builder.apply(index);
            else {
                LinearLayout line = new LinearLayout(getContext());
                for (int c = 0; c < columns; c++) {
                    View cell = index + c < end ? builder.apply(index + c) : new View(getContext());
                    LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    if (c > 0) lp.setMarginStart(gutter());
                    line.addView(cell, lp);
                }
                row = line;
            }
            rows.addView(row, new LayoutParams(-1, rowHeight));
        }
    }
}

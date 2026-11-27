package dev.inkhome.folio.paging;

import android.view.View;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import dev.inkhome.folio.Folio;

public final class PagedGrid extends PagedList {
    private final int minCell;

    public PagedGrid(Folio f, int count, int rowHeight, int minCell, IntFunction<View> builder,
                     int page, IntConsumer remember, Runnable changed) {
        super(f, null, count, rowHeight, builder, page, remember, changed);
        this.minCell = minCell;
    }

    @Override int columns(int width) {
        return Math.max(1, (width + gutter()) / (minCell + gutter()));
    }

    @Override int gutter() { return folio.u(folio.windowClass().gutter); }
}

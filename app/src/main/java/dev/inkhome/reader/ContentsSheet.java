package dev.inkhome.reader;

import android.text.TextUtils;
import android.view.View;
import android.widget.RadioButton;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.paging.PagedList;
import dev.inkhome.folio.widget.Radio;

import java.util.List;
import java.util.function.IntConsumer;

/** A sheet-sized page of the book's contents, initially showing the current chapter. */
public final class ContentsSheet extends PagedList {
    private final long naturalHeight;

    public ContentsSheet(Folio folio, List<String> names, int current, IntConsumer select, Runnable changed) {
        this(folio, names, current, select, changed, folio.u(Math.max(48, 44 * folio.textScale)));
    }

    private ContentsSheet(Folio folio, List<String> names, int current, IntConsumer select,
                          Runnable changed, int rowHeight) {
        super(folio, null, names.size(), rowHeight, index -> {
            RadioButton row = Radio.create(folio, names.get(index), index == current);
            row.setMaxLines(2);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setOnClickListener(v -> select.accept(index));
            return row;
        }, 0, page -> { }, changed);
        setPadding(folio.u(16), 0, folio.u(16), 0);
        naturalHeight = (long) rowHeight * Math.max(1, names.size());
        jump(current);
    }

    @Override protected void onMeasure(int width, int height) {
        if (MeasureSpec.getMode(height) != View.MeasureSpec.EXACTLY) {
            int windowHeight = ((android.app.Activity) getContext()).getWindow().getDecorView().getHeight();
            int limit = Math.max(1, windowHeight * 2 / 3);
            if (MeasureSpec.getMode(height) == MeasureSpec.AT_MOST) {
                limit = Math.min(limit, MeasureSpec.getSize(height));
            }
            height = MeasureSpec.makeMeasureSpec((int) Math.min(naturalHeight, limit), MeasureSpec.EXACTLY);
        }
        super.onMeasure(width, height);
    }
}

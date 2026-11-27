package dev.inkhome.folio.paging;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import java.util.function.IntFunction;
import dev.inkhome.folio.Folio;

/** Only materialize visible rows and covers in continuous scrolling mode. */
public final class ScrollingCells extends GridView {
    private final Runnable changed;
    public ScrollingCells(Folio f, int count, int height, int minWidth, IntFunction<View> builder, Runnable changed) {
        super(f.context);
        this.changed = changed;
        setNumColumns(minWidth > 0 ? AUTO_FIT : 1);
        if (minWidth > 0) {
            setColumnWidth(minWidth);
            setHorizontalSpacing(f.u(f.windowClass().gutter));
        }
        setStretchMode(STRETCH_COLUMN_WIDTH);
        setSelector(android.R.color.transparent);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setAdapter(new BaseAdapter() {
            public int getCount() { return count; }
            public Integer getItem(int position) { return position; }
            public long getItemId(int position) { return position; }
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = builder.apply(position);
                view.setLayoutParams(new AbsListView.LayoutParams(-1, height));
                return view;
            }
        });
    }

    public void turn(int direction) {
        if (canScrollVertically(direction)) {
            scrollListBy(direction * getHeight());
            changed.run();
        }
    }
}

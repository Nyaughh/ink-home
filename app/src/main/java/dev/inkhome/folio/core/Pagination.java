package dev.inkhome.folio.core;

import java.util.ArrayList;
import java.util.List;

/** Page starts in item indices for uniform content, or pixels for measured flow content. */
public final class Pagination {
    private final int[] starts;
    private final int end;

    private Pagination(int[] starts, int end) {
        this.starts = starts;
        this.end = end;
    }

    public int count() { return starts.length; }
    public int start(int page) { return starts[page]; }
    public int end(int page) { return page + 1 < count() ? starts[page + 1] : end; }
    public int pageAt(int position) {
        int i = java.util.Arrays.binarySearch(starts, position);
        return i >= 0 ? i : Math.max(0, -i - 2);
    }

    public static Pagination uniform(int count, int columns, int rowHeight, int height, int footer) {
        int per = capacity(columns, rowHeight, height);
        if (count > per) per = capacity(columns, rowHeight, height - footer);
        int[] starts = new int[count == 0 ? 1 : 1 + (count - 1) / per];
        for (int i = 0; i < starts.length; i++) starts[i] = i * per;
        return new Pagination(starts, count);
    }

    private static int capacity(int columns, int rowHeight, int height) {
        return (int) Math.min(Integer.MAX_VALUE, (long) columns * Math.max(1, height / rowHeight));
    }

    /** Extents are in visual order. A negative groupStart means an ungrouped item. */
    public static final class Item {
        public final int top, bottom, groupStart, groupEnd;
        public final boolean first;
        public Item(int top, int bottom, int groupStart, int groupEnd, boolean first) {
            this.top = top;
            this.bottom = bottom;
            this.groupStart = groupStart;
            this.groupEnd = groupEnd;
            this.first = first;
        }
    }

    public static Pagination flow(List<Item> items, int total, int height, int footer) {
        if (total <= height) return new Pagination(new int[]{0}, total);
        int viewport = Math.max(1, height - footer);
        List<Integer> starts = new ArrayList<>();
        int y = 0, i = 0;
        starts.add(y);
        while (total - y > viewport) {
            int limit = y + viewport;
            while (i < items.size() && items.get(i).bottom <= limit) i++;
            int cut = limit;
            if (i < items.size()) {
                Item item = items.get(i);
                if (item.top <= y || (item.first && item.groupStart == y)) {
                    // An oversized item occupies a page of its own. Its viewport can scroll.
                    cut = item.bottom;
                } else {
                    cut = Math.min(cut, item.top);
                }
                if (item.groupStart > y && item.groupStart <= limit
                        && (item.first || item.groupEnd - item.groupStart <= viewport)) {
                    cut = item.groupStart;
                }
            }
            if (cut >= total) break;
            starts.add(cut);
            y = cut;
        }
        int[] result = new int[starts.size()];
        for (int p = 0; p < result.length; p++) result[p] = starts.get(p);
        return new Pagination(result, total);
    }
}

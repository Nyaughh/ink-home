package dev.inkhome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Partitions one page into rectangles, preserving the tiles' reading order. */
final class HomeMosaic {
    private static final int COLS = HomeLayout.COLS, ROWS = HomeLayout.ROWS;
    private final List<HomeLayout.Tile> tiles;
    private final Plan[][][][] memo;

    private HomeMosaic(List<HomeLayout.Tile> tiles) {
        this.tiles = tiles;
        memo = new Plan[COLS + 1][ROWS + 1][tiles.size()][tiles.size() + 1];
    }

    /** Keep the larger half of an odd span for the original tile. */
    static HomeLayout.Tile split(HomeLayout.Tile tile, String kind, int widgetId) {
        int x = tile.x, y = tile.y, w = tile.w, h = tile.h;
        if (w >= h && w > 1) {
            w /= 2;
            tile.w -= w;
            x += tile.w;
        } else if (h > 1) {
            h /= 2;
            tile.h -= h;
            y += tile.h;
        } else {
            throw new IllegalArgumentException("Single cell");
        }
        return new HomeLayout.Tile(kind, tile.page, x, y, w, h, widgetId);
    }

    static void swap(HomeLayout.Tile a, HomeLayout.Tile b) {
        int page = a.page, x = a.x, y = a.y, w = a.w, h = a.h;
        a.page = b.page;
        a.x = b.x;
        a.y = b.y;
        a.w = b.w;
        a.h = b.h;
        b.page = page;
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = h;
    }

    static void tidy(HomeLayout layout, int page) {
        List<HomeLayout.Tile> tiles = new ArrayList<>();
        int area = 0;
        for (HomeLayout.Tile tile : layout.tiles) {
            if (tile.page == page) {
                tiles.add(tile);
                area += tile.w * tile.h;
            }
        }
        if (tiles.isEmpty() || area == ROWS * COLS) return;
        tiles.sort(Comparator.comparingInt((HomeLayout.Tile t) -> t.y).thenComparingInt(t -> t.x));
        HomeMosaic mosaic = new HomeMosaic(tiles);
        mosaic.place(mosaic.solve(0, tiles.size(), COLS, ROWS), 0, 0);
    }

    private static final class Plan {
        final int width, height, cost, first;
        final boolean vertical;
        final Plan left, right;

        Plan(int width, int height, int cost, int first, boolean vertical, Plan left, Plan right) {
            this.width = width;
            this.height = height;
            this.cost = cost;
            this.first = first;
            this.vertical = vertical;
            this.left = left;
            this.right = right;
        }
    }

    // For each tile range and rectangle, try all horizontal and vertical partitions.
    // Reusing their costs keeps the search bounded even on a page of single-cell tiles.
    private Plan solve(int start, int end, int w, int h) {
        Plan best = memo[w][h][start][end];
        if (best != null) return best;
        if (end - start == 1) {
            HomeLayout.Tile t = tiles.get(start);
            int shrink = Math.max(0, t.w - w) + Math.max(0, t.h - h);
            int dw = w - t.w, dh = h - t.h;
            int cost = shrink * 10000 + dw * dw + dh * dh;
            best = new Plan(w, h, cost, start, false, null, null);
        } else {
            for (int axis = 0; axis < 2; axis++) {
                boolean vertical = axis == 0;
                for (int cut = 1; cut < (vertical ? w : h); cut++) {
                    int lw = vertical ? cut : w, lh = vertical ? h : cut;
                    int rw = vertical ? w - cut : w, rh = vertical ? h : h - cut;
                    int min = Math.max(start + 1, end - rw * rh);
                    int max = Math.min(end - 1, start + lw * lh);
                    for (int split = min; split <= max; split++) {
                        Plan left = solve(start, split, lw, lh);
                        Plan right = solve(split, end, rw, rh);
                        int cost = left.cost + right.cost;
                        if (best == null || cost < best.cost)
                            best = new Plan(w, h, cost, start, vertical, left, right);
                    }
                }
            }
        }
        memo[w][h][start][end] = best;
        return best;
    }

    private void place(Plan plan, int x, int y) {
        if (plan.left == null) {
            HomeLayout.Tile tile = tiles.get(plan.first);
            tile.x = x;
            tile.y = y;
            tile.w = plan.width;
            tile.h = plan.height;
            return;
        }
        place(plan.left, x, y);
        place(plan.right, x + (plan.vertical ? plan.left.width : 0),
                y + (plan.vertical ? 0 : plan.left.height));
    }
}

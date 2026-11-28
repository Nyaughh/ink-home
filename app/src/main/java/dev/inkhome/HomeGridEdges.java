package dev.inkhome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves collinear edge intervals in grid pixels. Positive weights extend inward
 * from a top/left edge, negative weights inward from a bottom/right edge. */
final class HomeGridEdges {
    static final int SEPARATOR = 0, BORDER = 1, SELECTION = 2;
    record Edge(boolean vertical, int position, int start, int end, int weight, int color, int priority) {}
    record Strip(int left, int top, int right, int bottom, int color, int priority) {}

    private final List<Edge> edges = new ArrayList<>();

    void ring(int left, int top, int right, int bottom, int width, int color, int priority) {
        int w = Math.min(width, right - left), h = Math.min(width, bottom - top);
        if (w <= 0 || h <= 0) return;
        add(new Edge(true, left, top, bottom, w, color, priority));
        add(new Edge(true, right, top, bottom, -w, color, priority));
        add(new Edge(false, top, left, right, h, color, priority));
        add(new Edge(false, bottom, left, right, -h, color, priority));
    }

    void add(Edge edge) {
        edges.add(edge);
    }

    List<Strip> resolve() {
        List<Strip> result = new ArrayList<>();
        edges.sort(Comparator.comparing(Edge::vertical).thenComparingInt(Edge::position));
        int first = 0;
        while (first < edges.size()) {
            Edge line = edges.get(first);
            int end = first + 1;
            while (end < edges.size() && edges.get(end).vertical == line.vertical
                    && edges.get(end).position == line.position) end++;
            resolveLine(edges.subList(first, end), result);
            first = end;
        }
        // Corners can intersect perpendicular edges of different colors.
        result.sort(Comparator.comparingInt(Strip::priority));
        return result;
    }

    private static void resolveLine(List<Edge> line, List<Strip> result) {
        int start = Integer.MAX_VALUE;
        for (Edge edge : line) start = Math.min(start, edge.start);
        while (start < Integer.MAX_VALUE) {
            int next = Integer.MAX_VALUE;
            Edge winner = null;
            for (Edge edge : line) {
                if (edge.start > start) next = Math.min(next, edge.start);
                if (edge.end <= start) continue;
                next = Math.min(next, edge.end);
                if (edge.start <= start && (winner == null || edge.priority > winner.priority
                        || edge.priority == winner.priority && edge.weight > winner.weight)) winner = edge;
            }
            if (winner != null) {
                int low = winner.position + Math.min(0, winner.weight);
                int high = winner.position + Math.max(0, winner.weight);
                result.add(winner.vertical ? new Strip(low, start, high, next, winner.color, winner.priority)
                        : new Strip(start, low, next, high, winner.color, winner.priority));
            }
            start = next;
        }
    }
}

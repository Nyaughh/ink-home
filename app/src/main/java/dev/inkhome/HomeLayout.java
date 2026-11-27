package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The editable desktop is stored separately from the legacy Home style. */
final class HomeLayout {
    private static final String KEY = "homeGridV1";
    static final int COLS = 4;
    static final int ROWS = 6;

    int pages = 1;
    final List<Tile> tiles = new ArrayList<>();

    static final class Tile {
        String kind;
        int page, x, y, w, h, widgetId;

        Tile(String kind, int page, int x, int y, int w, int h, int widgetId) {
            this.kind = kind;
            this.page = page;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.widgetId = widgetId;
        }
    }

    private static SharedPreferences prefs(MainActivity a) {
        return a.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
    }

    static boolean exists(MainActivity a) {
        return prefs(a).contains(KEY);
    }

    /** Caller releases hosted widget IDs before discarding their placements. */
    static void reset(MainActivity a) {
        prefs(a).edit().remove(KEY).apply();
    }

    static HomeLayout load(MainActivity a) {
        String saved = prefs(a).getString(KEY, null);
        if (saved != null) {
            try {
                JSONObject data = new JSONObject(saved);
                HomeLayout layout = new HomeLayout();
                layout.pages = data.getInt("pages");
                if (layout.pages < 1) throw new JSONException("Invalid page count");
                JSONArray entries = data.getJSONArray("tiles");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject t = entries.getJSONObject(i);
                    Tile tile = new Tile(t.getString("kind"), t.getInt("page"), t.getInt("x"),
                            t.getInt("y"), t.getInt("w"), t.getInt("h"), t.getInt("widgetId"));
                    if (!layout.fits(null, tile.page, tile.x, tile.y, tile.w, tile.h)) {
                        throw new JSONException("Invalid tile placement");
                    }
                    layout.tiles.add(tile);
                }
                return layout;
            } catch (JSONException ignored) {
                // Leave the saved data untouched until the user saves a new layout.
            }
        }
        return preset("reading");
    }

    static HomeLayout preset(String key) {
        HomeLayout layout = new HomeLayout();
        if ("planner".equals(key)) {
            layout.place("calendar", 0, 0, 3, 3);
            layout.place("clock", 3, 0, 1, 2);
            layout.place("battery", 3, 2, 1, 1);
            layout.place("month", 0, 3, 2, 2);
            layout.place("goal", 2, 3, 2, 2);
            layout.place("apps", 0, 5, 4, 1);
        } else if ("night".equals(key)) {
            layout.place("moon", 0, 0, 2, 2);
            layout.place("clock", 2, 0, 2, 2);
            layout.place("current", 0, 2, 3, 3);
            layout.place("battery", 3, 2, 1, 1);
            layout.place("goal", 3, 3, 1, 2);
            layout.place("apps", 0, 5, 4, 1);
        } else {
            layout.place("current", 0, 0, 4, 2);
            layout.place("next", 0, 2, 2, 2);
            layout.place("clock", 2, 2, 2, 1);
            layout.place("goal", 2, 3, 2, 1);
            layout.place("month", 0, 4, 2, 2);
            layout.place("apps", 2, 4, 2, 1);
            layout.place("battery", 2, 5, 1, 1);
            layout.place("moon", 3, 5, 1, 1);
        }
        return layout;
    }

    private void place(String kind, int x, int y, int w, int h) {
        tiles.add(new Tile(kind, 0, x, y, w, h, -1));
    }

    void save(MainActivity a) {
        JSONObject data = new JSONObject();
        JSONArray entries = new JSONArray();
        try {
            for (Tile t : tiles) {
                entries.put(new JSONObject().put("kind", t.kind).put("page", t.page)
                        .put("x", t.x).put("y", t.y).put("w", t.w).put("h", t.h)
                        .put("widgetId", t.widgetId));
            }
            data.put("pages", pages).put("tiles", entries);
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot encode home layout", e);
        }
        prefs(a).edit().putString(KEY, data.toString()).apply();
    }

    boolean fits(Tile ignore, int page, int x, int y, int w, int h) {
        if (page < 0 || page >= pages || x < 0 || y < 0 || w < 1 || h < 1
                || x > COLS - w || y > ROWS - h) return false;
        for (Tile t : tiles) {
            if (t != ignore && t.page == page && x < t.x + t.w && x + w > t.x
                    && y < t.y + t.h && y + h > t.y) return false;
        }
        return true;
    }
}

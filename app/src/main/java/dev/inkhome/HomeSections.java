package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ordered Home sections and their display options. */
final class HomeSections {
    private static final String KEY = "homeSectionsV1";
    private static final String GRID = "homeGridV1";
    private static final String[] OLD_KEYS = {GRID, "useHomePreset", "moonLatitude", "moonLongitude"};
    private static final List<String> BUILTINS = Arrays.asList(
            "current", "next", "apps", "clock", "calendar", "goal", "month", "battery");
    private static final List<String> DEFAULT_VISIBLE = Arrays.asList("current", "next", "apps");

    final List<String> order = new ArrayList<>(BUILTINS);
    final Set<String> visible = new LinkedHashSet<>(DEFAULT_VISIBLE);
    boolean borders, verticalApps, compact;
    int appCount, nextCount;

    boolean isDefault() {
        if (borders || verticalApps || compact || appCount != 0 || nextCount != 0
                || visible.size() != DEFAULT_VISIBLE.size()) return false;
        List<String> shown = new ArrayList<>(order);
        shown.retainAll(visible);
        return DEFAULT_VISIBLE.equals(shown);
    }

    static int widgetId(String id) {
        if (id == null || !id.startsWith("widget:")) return -1;
        try {
            int value = Integer.parseInt(id.substring(7));
            if (value > 0 && id.equals("widget:" + value)) return value;
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    static HomeSections load(MainActivity a) {
        SharedPreferences prefs = a.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
        HomeSections sections = new HomeSections();
        String saved = prefs.getString(KEY, null);
        if (saved != null) {
            try {
                sections.read(new JSONObject(saved));
            } catch (JSONException ignored) {
                sections = new HomeSections();
            }
        } else {
            String grid = prefs.getString(GRID, null);
            if (grid != null) {
                try {
                    sections.migrate(new JSONObject(grid), !prefs.getBoolean("useHomePreset", false));
                } catch (JSONException ignored) {
                    sections = new HomeSections();
                }
            }
        }
        for (String key : OLD_KEYS) {
            if (prefs.contains(key)) {
                sections.save(a);
                break;
            }
        }
        return sections;
    }

    private void read(JSONObject data) throws JSONException {
        JSONArray ids = data.getJSONArray("order");
        JSONArray shown = data.getJSONArray("visible");
        Set<String> restored = new LinkedHashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.optString(i);
            if (BUILTINS.contains(id) || widgetId(id) > 0) restored.add(id);
        }
        restored.addAll(BUILTINS);
        order.clear();
        order.addAll(restored);
        visible.clear();
        for (int i = 0; i < shown.length(); i++) {
            String id = shown.optString(i);
            if (restored.contains(id)) visible.add(id);
        }
        borders = data.optBoolean("borders");
        verticalApps = data.optBoolean("verticalApps");
        compact = data.optBoolean("compact");
        appCount = Math.max(0, data.optInt("appCount"));
        nextCount = Math.max(0, data.optInt("nextCount"));
    }

    private void migrate(JSONObject grid, boolean active) throws JSONException {
        JSONArray tiles = grid.getJSONArray("tiles");
        if (isOriginalReadingGrid(grid, tiles)) return;
        List<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < tiles.length(); i++) {
            JSONObject tile = tiles.optJSONObject(i);
            if (tile != null) sorted.add(tile);
        }
        sorted.sort(Comparator.comparingInt((JSONObject t) -> t.optInt("page"))
                .thenComparingInt(t -> t.optInt("y")).thenComparingInt(t -> t.optInt("x")));
        Set<String> ids = new LinkedHashSet<>();
        for (JSONObject tile : sorted) {
            String kind = tile.optString("kind");
            if ("widget".equals(kind)) {
                int id = tile.optInt("widgetId", -1);
                if (id > 0) ids.add("widget:" + id);
            } else if (active && BUILTINS.contains(kind)) {
                ids.add(kind);
            }
        }
        if (active) {
            visible.clear();
            visible.addAll(ids);
            ids.addAll(BUILTINS);
            order.clear();
        }
        // Hidden sections keep widgets allocated by an inactive grid available in the editor.
        order.addAll(ids);
    }

    private static boolean isOriginalReadingGrid(JSONObject grid, JSONArray tiles) {
        if (grid.optInt("pages") != 1 || tiles.length() != 8) return false;
        Set<String> placements = new LinkedHashSet<>(Arrays.asList(
                "current:0:0:4:2", "next:0:2:2:2", "clock:2:2:2:1", "goal:2:3:2:1",
                "month:0:4:2:2", "apps:2:4:2:1", "battery:2:5:1:1", "moon:3:5:1:1"));
        for (int i = 0; i < tiles.length(); i++) {
            JSONObject t = tiles.optJSONObject(i);
            if (t == null || t.optInt("page", -1) != 0 || t.optInt("widgetId", 0) != -1
                    || !placements.remove(t.optString("kind") + ":" + t.optInt("x", -1) + ":"
                    + t.optInt("y", -1) + ":" + t.optInt("w", -1) + ":" + t.optInt("h", -1))) {
                return false;
            }
        }
        return true;
    }

    void save(MainActivity a) {
        JSONObject data = new JSONObject();
        try {
            data.put("order", new JSONArray(order)).put("visible", new JSONArray(visible))
                    .put("borders", borders).put("verticalApps", verticalApps).put("compact", compact)
                    .put("appCount", appCount).put("nextCount", nextCount);
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot encode Home sections", e);
        }
        SharedPreferences.Editor edit = a.getSharedPreferences("inkhome", Context.MODE_PRIVATE).edit();
        edit.putString(KEY, data.toString());
        for (String key : OLD_KEYS) edit.remove(key);
        edit.apply();
    }
}

package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;
import dev.inkhome.folio.core.WindowClass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The editable Home canvas. Instance keys distinguish copies of the same component. */
final class HomeLayout {
    static final int COLS = 4, ROWS = 16;
    private static final String KEY = "homeCanvasV1";
    final List<Item> items = new ArrayList<>();
    final List<Integer> hiddenWidgetIds = new ArrayList<>();

    static final class Item {
        String key, type, variant;
        int x, y, w, h;
        final Map<String, String> settings = new LinkedHashMap<>();

        Item(String type, String variant, int x, int y, int w, int h) {
            key = UUID.randomUUID().toString();
            this.type = type;
            this.variant = variant;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Item copy() {
            Item copy = new Item(type, variant, x, y, w, h);
            copy.key = key;
            copy.settings.putAll(settings);
            return copy;
        }
    }

    HomeLayout copy() {
        HomeLayout copy = new HomeLayout();
        for (Item item : items) copy.items.add(item.copy());
        copy.hiddenWidgetIds.addAll(hiddenWidgetIds);
        return copy;
    }

    boolean fits(Item ignore, int x, int y, int w, int h) {
        if (w < 1 || h < 1 || x < 0 || y < 0 || x > COLS - w || y > ROWS - h) return false;
        for (Item item : items) {
            if (item != ignore && x < item.x + item.w && x + w > item.x
                    && y < item.y + item.h && y + h > item.y) return false;
        }
        return true;
    }

    static HomeLayout preset(MainActivity host) {
        return readingPreset(host, "cover");
    }

    private static HomeLayout readingPreset(MainActivity host, String style) {
        HomeLayout home = new HomeLayout();
        int height = host.ink == null ? 0 : Math.round(
                host.getResources().getConfiguration().screenHeightDp / host.ink.folio.uiScale);
        int appRows = height >= 780 ? 4 : 3;
        int appsTop = ROWS - appRows;
        String appVariant = height >= 780 ? "grid" : "row";
        if ("shelf".equals(style)) {
            home.band("current", "shelf", 0, appsTop);
        } else if ("minimal".equals(style)) {
            home.band("clock", "time only", 0, 3);
            home.band("current", "minimal", 3, 5);
        } else {
            WindowClass window = host.ink == null
                    ? WindowClass.COMPACT : host.ink.folio.windowClass();
            if (window == WindowClass.EXPANDED ||
                    (window == WindowClass.MEDIUM && height < 500)) {
                home.band("current", "cover", 0, ROWS);
                home.items.get(0).w = 2;
                home.band("next", "list", 0, appsTop);
                home.items.get(1).x = 2;
                home.items.get(1).w = 2;
                home.band("apps", appVariant, appsTop, appRows);
                home.items.get(2).x = 2;
                home.items.get(2).w = 2;
                return home;
            }
            home.band("current", "cover", 0, 6);
            home.band("next", "list", 6, appsTop - 6);
        }
        home.band("apps", appVariant, appsTop, appRows);
        return home;
    }

    private void band(String type, String variant, int y, int height) {
        Item item = new Item(type, variant, 0, y, COLS, height);
        item.key = "preset-" + type;
        items.add(item);
    }

    static HomeLayout load(MainActivity host) {
        SharedPreferences prefs = host.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY, null);
        if (saved != null) {
            try {
                return read(new JSONObject(saved));
            } catch (JSONException ignored) {
                return preset(host);
            }
        }
        HomeSections sections = HomeSections.loadLegacy(prefs);
        HomeLayout layout = sections.isDefault()
                ? readingPreset(host, prefs.getString("homeLayout", "cover"))
                : fromSections(sections);
        for (String id : sections.order) {
            int widget = HomeSections.widgetId(id);
            if (widget > 0 && !sections.visible.contains(id)) layout.hiddenWidgetIds.add(widget);
        }
        if (HomeSections.hasLegacyData(prefs)) layout.save(host);
        return layout;
    }

    private static HomeLayout fromSections(HomeSections sections) {
        HomeLayout home = new HomeLayout();
        List<Item> shown = new ArrayList<>();
        int total = 0;
        for (String id : sections.order) {
            if (!sections.visible.contains(id)) continue;
            int widget = HomeSections.widgetId(id);
            String type = widget > 0 ? "android" : id;
            HomeComponent component = HomeComponents.get(type);
            if (component == null) continue;
            String variant = "apps".equals(type) && sections.verticalApps
                    ? "column" : component.variants()[0];
            int height = switch (type) {
                case "current", "month", "calendar", "next", "android" -> 4;
                case "apps" -> sections.verticalApps ? 4 : 3;
                default -> 2;
            };
            Item item = new Item(type, variant, 0, 0, COLS, height);
            if (widget > 0) item.settings.put("widgetId", String.valueOf(widget));
            if (sections.borders) item.settings.put("borders", "true");
            if (sections.compact) item.settings.put("compact", "true");
            int count = "next".equals(type) ? sections.nextCount : "apps".equals(type) ? sections.appCount : 0;
            if (count > 0) item.settings.put("count", String.valueOf(Math.min(12, count)));
            shown.add(item);
            total += height;
        }
        for (Item item : shown) {
            if (total > ROWS) {
                // Find the smallest supported rectangle so a long legacy list fits on one canvas.
                HomeComponent component = HomeComponents.get(item.type);
                int area = Integer.MAX_VALUE;
                for (int h = 1; h <= ROWS; h++) {
                    for (int w = 1; w <= COLS; w++) {
                        if (w * h < area && component.supports(w, h)) {
                            item.w = w;
                            item.h = h;
                            area = w * h;
                        }
                    }
                }
            }
            if (!home.place(item) && "android".equals(item.type)) {
                home.hiddenWidgetIds.add(Integer.parseInt(item.settings.get("widgetId")));
            }
        }
        return home;
    }

    private boolean place(Item item) {
        for (int y = 0; y <= ROWS - item.h; y++) {
            for (int x = 0; x <= COLS - item.w; x++) {
                if (!fits(null, x, y, item.w, item.h)) continue;
                item.x = x;
                item.y = y;
                items.add(item);
                return true;
            }
        }
        return false;
    }

    private static HomeLayout read(JSONObject data) throws JSONException {
        HomeLayout home = new HomeLayout();
        Set<String> keys = new HashSet<>();
        JSONArray items = data.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject raw = items.optJSONObject(i);
            if (raw == null) continue;
            HomeComponent component = HomeComponents.get(raw.optString("type"));
            if (component == null) continue;
            String[] variants = component.variants();
            String variant = raw.optString("variant", variants[0]);
            if (!Arrays.asList(variants).contains(variant)) variant = variants[0];
            Item item = new Item(component.id(), variant,
                    raw.optInt("x", -1), raw.optInt("y", -1), raw.optInt("w"), raw.optInt("h"));
            if (!home.fits(null, item.x, item.y, item.w, item.h) || !component.supports(item.w, item.h)) continue;
            String key = raw.optString("key", "");
            if (!key.isEmpty() && keys.add(key)) item.key = key;
            else keys.add(item.key);
            JSONObject settings = raw.optJSONObject("settings");
            if (settings != null) {
                Iterator<String> names = settings.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    item.settings.put(name, settings.getString(name));
                }
            }
            home.items.add(item);
        }
        JSONArray hidden = data.optJSONArray("hiddenWidgetIds");
        if (hidden != null) {
            for (int i = 0; i < hidden.length(); i++) {
                int id = hidden.optInt(i);
                if (id > 0 && !home.hiddenWidgetIds.contains(id)) home.hiddenWidgetIds.add(id);
            }
        }
        return home;
    }

    void save(MainActivity host) {
        JSONObject data = new JSONObject();
        JSONArray cells = new JSONArray();
        try {
            for (Item item : items) {
                cells.put(new JSONObject().put("key", item.key).put("type", item.type)
                        .put("variant", item.variant).put("x", item.x).put("y", item.y)
                        .put("w", item.w).put("h", item.h).put("settings", new JSONObject(item.settings)));
            }
            data.put("items", cells).put("hiddenWidgetIds", new JSONArray(hiddenWidgetIds));
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot encode Home layout", e);
        }
        SharedPreferences.Editor edit = host.getSharedPreferences("inkhome", Context.MODE_PRIVATE).edit();
        HomeSections.clearLegacyData(edit);
        edit.putString(KEY, data.toString()).apply();
    }
}

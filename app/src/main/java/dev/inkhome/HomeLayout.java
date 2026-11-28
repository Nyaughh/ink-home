package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;
import dev.inkhome.folio.core.WindowClass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

    /** A preview of one layout edit. Coordinates change together only when the editor accepts it. */
    static final class Placement {
        private final Item selected;
        private final int x, y, width, height;
        private final Map<Item, Integer> destinations = new LinkedHashMap<>();

        Placement(Item selected, int x, int y, int width, int height) {
            this.selected = selected;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void apply() {
            selected.x = x;
            selected.y = y;
            selected.w = width;
            selected.h = height;
            for (Map.Entry<Item, Integer> entry : destinations.entrySet()) {
                entry.getKey().x = entry.getValue() % COLS;
                entry.getKey().y = entry.getValue() / COLS;
            }
        }
    }

    Placement move(Item selected, int dx, int dy) {
        if (!items.contains(selected) || !((dx == 0 && (dy == -1 || dy == 1))
                || (dy == 0 && (dx == -1 || dx == 1)))) return null;
        int x = selected.x + dx, y = selected.y + dy;
        long next = cells(x, y, selected.w, selected.h);
        if (next == 0) return null;
        List<Item> neighbours = new ArrayList<>();
        int distance = 0;
        for (Item other : items) {
            if (other == selected || (next & cells(other)) == 0) continue;
            neighbours.add(other);
            distance = Math.max(distance, dx == 0 ? other.h : other.w);
        }
        if (neighbours.isEmpty()) return new Placement(selected, x, y, selected.w, selected.h);

        // Cross the adjacent band in one action. Equal-sized widgets swap, and a wider
        // widget can cross two narrower widgets together without disturbing the rest.
        int acrossX = selected.x + dx * distance, acrossY = selected.y + dy * distance;
        long occupied = cells(acrossX, acrossY, selected.w, selected.h);
        if (occupied != 0) {
            Placement swap = new Placement(selected, acrossX, acrossY, selected.w, selected.h);
            for (Item other : items) {
                if (other == selected) continue;
                boolean pushed = neighbours.contains(other);
                int ox = other.x - (pushed ? dx * selected.w : 0);
                int oy = other.y - (pushed ? dy * selected.h : 0);
                long mask = cells(ox, oy, other.w, other.h);
                if (mask == 0 || (mask & occupied) != 0) {
                    swap = null;
                    break;
                }
                occupied |= mask;
                if (pushed) swap.destinations.put(other, oy * COLS + ox);
            }
            if (swap != null) return swap;
        }
        Placement step = reflow(selected, x, y, selected.w, selected.h, dx, dy);
        if (step != null || distance == 1 || cells(acrossX, acrossY, selected.w, selected.h) == 0) return step;
        // With an irregular group, the one-cell destination can split a band into
        // unusable slivers. Try crossing its full depth with reflow as well.
        return reflow(selected, acrossX, acrossY, selected.w, selected.h, dx, dy);
    }

    Placement resize(Item selected, int width, int height) {
        if (!items.contains(selected)) return null;
        HomeComponent component = HomeComponents.get(selected.type);
        if (component == null || !component.supports(width, height)) return null;
        // Preserve the top-left anchor, moving it inward only at the outer edges.
        int x = Math.min(selected.x, COLS - width), y = Math.min(selected.y, ROWS - height);
        return reflow(selected, x, y, width, height, 0, 0);
    }

    private static long cells(Item item) {
        return cells(item.x, item.y, item.w, item.h);
    }

    /** One bit per cell on the 4 × 16 canvas, including the sign bit for the last cell. */
    private static long cells(int x, int y, int w, int h) {
        if (w < 1 || w > COLS || h < 1 || h > ROWS ||
                x < 0 || x > COLS - w || y < 0 || y > ROWS - h) return 0;
        long row = ((1L << w) - 1) << (y * COLS + x);
        long result = 0;
        for (int i = 0; i < h; i++, row <<= COLS) result |= row;
        return result;
    }

    private Placement reflow(Item selected, int x, int y, int w, int h, int dx, int dy) {
        Placement result = new Placement(selected, x, y, w, h);
        long target = cells(x, y, w, h), fixed = target;
        int area = w * h;
        List<Item> blocked = new ArrayList<>();
        List<Item> others = new ArrayList<>();
        for (Item other : items) {
            if (other == selected) continue;
            others.add(other);
            area += other.w * other.h;
            long mask = cells(other);
            if ((target & mask) != 0) blocked.add(other);
            else fixed |= mask;
        }
        if (area > COLS * ROWS) return null;
        if (blocked.isEmpty()) return result;
        if (pack(result, blocked, fixed, dx, dy)) return result;
        // A third widget can be in the only gap that fits the neighbour. Open the
        // rest of the canvas to reflow only after the local displacement fails.
        if (others.size() != blocked.size() && pack(result, others, target, dx, dy)) return result;
        return null;
    }

    private static final class Candidate {
        final long mask;
        final int cell, cost;

        Candidate(Item item, int x, int y, int cost) {
            mask = cells(x, y, item.w, item.h);
            cell = y * COLS + x;
            this.cost = cost;
        }
    }

    private static final class Choices {
        final Item item;
        final List<Candidate> places = new ArrayList<>();
        int chosen;

        Choices(Item item, long fixed, int dx, int dy) {
            this.item = item;
            for (int y = 0; y <= ROWS - item.h; y++) {
                for (int x = 0; x <= COLS - item.w; x++) {
                    int horizontal = x - item.x, vertical = y - item.y;
                    // Distance first, then favour movement opposite the arrow.
                    int cost = 3 * (Math.abs(horizontal) + Math.abs(vertical))
                            + Integer.signum(horizontal * dx + vertical * dy);
                    Candidate place = new Candidate(item, x, y, cost);
                    if ((fixed & place.mask) == 0) places.add(place);
                }
            }
            places.sort(Comparator.comparingInt(p -> p.cost));
        }
    }

    private static boolean pack(Placement result, List<Item> moving, long fixed, int dx, int dy) {
        List<Choices> choices = new ArrayList<>();
        for (Item item : moving) {
            Choices options = new Choices(item, fixed, dx, dy);
            if (options.places.isEmpty()) return false;
            choices.add(options);
        }
        choices.sort(Comparator.comparingInt((Choices c) -> c.places.size())
                .thenComparingInt(c -> -c.item.w * c.item.h));
        if (!new PackingSearch(choices).solve(0, fixed)) return false;
        for (Choices c : choices) {
            if (c.chosen != c.item.y * COLS + c.item.x) result.destinations.put(c.item, c.chosen);
        }
        return true;
    }

    private static final class PackingSearch {
        final List<Choices> choices;
        // Bound worst-case UI work for impossible arrangements of mixed rectangles.
        int attempts = 12000;

        PackingSearch(List<Choices> choices) {
            this.choices = choices;
        }

        boolean solve(int index, long occupied) {
            if (index == choices.size()) return true;
            Choices current = choices.get(index);
            for (Candidate place : current.places) {
                if ((occupied & place.mask) != 0) continue;
                if (--attempts < 0) return false;
                current.chosen = place.cell;
                if (solve(index + 1, occupied | place.mask)) return true;
            }
            return false;
        }
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
        item.settings.put("readingPreset", "true");
        if ("apps".equals(type) || ("current".equals(type) && "cover".equals(variant))) {
            item.settings.put("heightMode", "content");
        }
        items.add(item);
    }

    static HomeLayout load(MainActivity host) {
        SharedPreferences prefs = host.getSharedPreferences("inkhome", Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY, null);
        if (saved != null) {
            try {
                HomeLayout layout = read(new JSONObject(saved));
                layout.restoreOriginalPresetSizing(host, prefs.getString("homeLayout", "cover"));
                return layout;
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

    /** A first-generation canvas could already contain the untouched reading preset. */
    private void restoreOriginalPresetSizing(MainActivity host, String style) {
        if (items.isEmpty() || !items.get(0).key.startsWith("preset-") || !items.get(0).settings.isEmpty()) return;
        HomeLayout preset = readingPreset(host, style);
        if (preset.items.size() != items.size()) return;
        for (int i = 0; i < items.size(); i++) {
            Item old = items.get(i), fresh = preset.items.get(i);
            if (!old.key.equals(fresh.key) || !old.type.equals(fresh.type) || !old.variant.equals(fresh.variant)
                    || old.x != fresh.x || old.y != fresh.y || old.w != fresh.w || old.h != fresh.h
                    || !old.settings.isEmpty()) return;
        }
        for (int i = 0; i < items.size(); i++) items.get(i).settings.putAll(preset.items.get(i).settings);
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

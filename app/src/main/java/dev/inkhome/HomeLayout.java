package dev.inkhome;

import android.content.Context;
import android.content.SharedPreferences;
import dev.inkhome.folio.core.WindowClass;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
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
    static final int MIN_COLS = 3, MAX_COLS = 6, MIN_ROWS = 4, MAX_ROWS = 16;
    int cols = COLS, rows = ROWS;
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
        copy.cols = cols;
        copy.rows = rows;
        for (Item item : items) copy.items.add(item.copy());
        copy.hiddenWidgetIds.addAll(hiddenWidgetIds);
        return copy;
    }

    boolean fits(Item ignore, int x, int y, int w, int h) {
        if (w < 1 || h < 1 || x < 0 || y < 0 || x > cols - w || y > rows - h) return false;
        for (Item item : items) {
            if (item != ignore && x < item.x + item.w && x + w > item.x
                    && y < item.y + item.h && y + h > item.y) return false;
        }
        return true;
    }

    /** A preview of one layout edit. Coordinates change together only when the editor accepts it. */
    final class Placement {
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
                entry.getKey().x = entry.getValue() % cols;
                entry.getKey().y = entry.getValue() / cols;
            }
        }
    }

    Placement move(Item selected, int dx, int dy) {
        if (!items.contains(selected) || !((dx == 0 && (dy == -1 || dy == 1))
                || (dy == 0 && (dx == -1 || dx == 1)))) return null;
        int x = selected.x + dx, y = selected.y + dy;
        BitSet next = cells(x, y, selected.w, selected.h);
        if (next.isEmpty()) return null;
        List<Item> neighbours = new ArrayList<>();
        int distance = 0;
        for (Item other : items) {
            if (other == selected || !next.intersects(cells(other))) continue;
            neighbours.add(other);
            distance = Math.max(distance, dx == 0 ? other.h : other.w);
        }
        if (neighbours.isEmpty()) return new Placement(selected, x, y, selected.w, selected.h);

        // Cross the adjacent band in one action. Equal-sized widgets swap, and a wider
        // widget can cross two narrower widgets together without disturbing the rest.
        int acrossX = selected.x + dx * distance, acrossY = selected.y + dy * distance;
        BitSet occupied = cells(acrossX, acrossY, selected.w, selected.h);
        if (!occupied.isEmpty()) {
            Placement swap = new Placement(selected, acrossX, acrossY, selected.w, selected.h);
            for (Item other : items) {
                if (other == selected) continue;
                boolean pushed = neighbours.contains(other);
                int ox = other.x - (pushed ? dx * selected.w : 0);
                int oy = other.y - (pushed ? dy * selected.h : 0);
                BitSet mask = cells(ox, oy, other.w, other.h);
                if (mask.isEmpty() || occupied.intersects(mask)) {
                    swap = null;
                    break;
                }
                occupied.or(mask);
                if (pushed) swap.destinations.put(other, oy * cols + ox);
            }
            if (swap != null) return swap;
        }
        Placement step = reflow(selected, x, y, selected.w, selected.h, dx, dy);
        if (step != null || distance == 1 || cells(acrossX, acrossY, selected.w, selected.h).isEmpty()) return step;
        // With an irregular group, the one-cell destination can split a band into
        // unusable slivers. Try crossing its full depth with reflow as well.
        return reflow(selected, acrossX, acrossY, selected.w, selected.h, dx, dy);
    }

    Placement resize(Item selected, int width, int height) {
        if (!items.contains(selected)) return null;
        HomeComponent component = HomeComponents.get(selected.type);
        if (component == null || !component.supports(width, height)) return null;
        // Preserve the top-left anchor, moving it inward only at the outer edges.
        int x = Math.min(selected.x, cols - width), y = Math.min(selected.y, rows - height);
        return reflow(selected, x, y, width, height, 0, 0);
    }

    private BitSet cells(Item item) {
        return cells(item.x, item.y, item.w, item.h);
    }

    private BitSet cells(int x, int y, int w, int h) {
        BitSet result = new BitSet(cols * rows);
        if (w < 1 || h < 1 || x < 0 || y < 0 || x > cols - w || y > rows - h) return result;
        for (int row = y; row < y + h; row++) {
            int start = row * cols + x;
            result.set(start, start + w);
        }
        return result;
    }

    Placement moveTo(Item selected, int x, int y) {
        if (!items.contains(selected)) return null;
        return reflow(selected, x, y, selected.w, selected.h,
                Integer.compare(x, selected.x), Integer.compare(y, selected.y));
    }

    private Placement reflow(Item selected, int x, int y, int w, int h, int dx, int dy) {
        Placement result = new Placement(selected, x, y, w, h);
        BitSet target = cells(x, y, w, h);
        if (target.isEmpty()) return null;
        BitSet fixed = (BitSet) target.clone();
        int area = w * h;
        List<Item> blocked = new ArrayList<>();
        List<Item> others = new ArrayList<>();
        for (Item other : items) {
            if (other == selected) continue;
            others.add(other);
            area += other.w * other.h;
            BitSet mask = cells(other);
            if (target.intersects(mask)) blocked.add(other);
            else fixed.or(mask);
        }
        if (area > cols * rows) return null;
        if (blocked.isEmpty()) return result;
        if (pack(result.destinations, blocked, fixed, dx, dy)) return result;
        // A third widget can be in the only gap that fits the neighbour. Open the
        // rest of the canvas to reflow only after the local displacement fails.
        if (others.size() != blocked.size() && pack(result.destinations, others, target, dx, dy)) return result;
        return null;
    }

    private final class Candidate {
        final BitSet mask;
        final int cell, cost;

        Candidate(Item item, int x, int y, int cost) {
            mask = cells(x, y, item.w, item.h);
            cell = y * cols + x;
            this.cost = cost;
        }
    }

    private final class Choices {
        final Item item;
        final List<Candidate> places = new ArrayList<>();
        int chosen;

        Choices(Item item, BitSet fixed, int dx, int dy) {
            this.item = item;
            for (int y = 0; y <= rows - item.h; y++) {
                for (int x = 0; x <= cols - item.w; x++) {
                    int horizontal = x - item.x, vertical = y - item.y;
                    // Distance first, then favour movement opposite the arrow.
                    int cost = 3 * (Math.abs(horizontal) + Math.abs(vertical))
                            + Integer.signum(horizontal * dx + vertical * dy);
                    Candidate place = new Candidate(item, x, y, cost);
                    if (!fixed.intersects(place.mask)) places.add(place);
                }
            }
            places.sort(Comparator.comparingInt(p -> p.cost));
        }
    }

    private boolean pack(Map<Item, Integer> destinations, List<Item> moving, BitSet fixed, int dx, int dy) {
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
            destinations.put(c.item, c.chosen);
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

        boolean solve(int index, BitSet occupied) {
            if (index == choices.size()) return true;
            Choices current = choices.get(index);
            for (Candidate place : current.places) {
                if (occupied.intersects(place.mask)) continue;
                if (--attempts < 0) return false;
                current.chosen = place.cell;
                occupied.or(place.mask);
                if (solve(index + 1, occupied)) return true;
                occupied.andNot(place.mask);
            }
            return false;
        }
    }

    private static boolean validGrid(int cols, int rows) {
        return cols >= MIN_COLS && cols <= MAX_COLS && rows >= MIN_ROWS && rows <= MAX_ROWS;
    }

    boolean changeGrid(int newCols, int newRows) {
        if (!validGrid(newCols, newRows)) return false;
        if (cols == newCols && rows == newRows) return true;
        if (items.size() > newCols * newRows) return false;

        HomeLayout draft = copy();
        draft.cols = newCols;
        draft.rows = newRows;
        // Try proportional rectangles first. Reduce preferred spans in single-cell
        // steps when rounding or a module's aspect ratio prevents them from fitting.
        for (int reduction = 0; reduction <= MAX_ROWS; reduction++) {
            int area = 0;
            for (int i = 0; i < items.size(); i++) {
                Item before = items.get(i), item = draft.items.get(i);
                item.x = Math.round(before.x * (float) newCols / cols);
                item.y = Math.round(before.y * (float) newRows / rows);
                int right = Math.round((before.x + before.w) * (float) newCols / cols);
                int bottom = Math.round((before.y + before.h) * (float) newRows / rows);
                int wantedW = Math.max(1, right - item.x - reduction);
                int wantedH = Math.max(1, bottom - item.y - reduction);
                if (!draft.chooseSpan(item, wantedW, wantedH)) return false;
                item.x = Math.min(item.x, newCols - item.w);
                item.y = Math.min(item.y, newRows - item.h);
                area += item.w * item.h;
            }
            Map<Item, Integer> packed = new LinkedHashMap<>();
            if (area > newCols * newRows ||
                    !draft.pack(packed, draft.items, new BitSet(), 0, 0)) continue;
            for (int i = 0; i < items.size(); i++) {
                Item original = items.get(i), placed = draft.items.get(i);
                int position = packed.get(placed);
                original.x = position % newCols;
                original.y = position / newCols;
                original.w = placed.w;
                original.h = placed.h;
            }
            cols = newCols;
            rows = newRows;
            return true;
        }
        return false;
    }

    private boolean chooseSpan(Item item, int wantedW, int wantedH) {
        HomeComponent component = HomeComponents.get(item.type);
        if (component == null) return false;
        int best = Integer.MAX_VALUE;
        for (int h = 1; h <= rows; h++) {
            for (int w = 1; w <= cols; w++) {
                if (!component.supports(w, h)) continue;
                int dw = w - wantedW, dh = h - wantedH;
                int cost = dw * dw + dh * dh;
                if (cost < best) {
                    best = cost;
                    item.w = w;
                    item.h = h;
                }
            }
        }
        return best != Integer.MAX_VALUE;
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
        for (int y = 0; y <= rows - item.h; y++) {
            for (int x = 0; x <= cols - item.w; x++) {
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
        int cols = data.optInt("cols", COLS), rows = data.optInt("rows", ROWS);
        if (validGrid(cols, rows)) {
            home.cols = cols;
            home.rows = rows;
        }
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
            // Size rules govern new spans. A module may tighten them after this placement was saved.
            if (!home.fits(null, item.x, item.y, item.w, item.h)) continue;
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
            data.put("cols", cols).put("rows", rows).put("items", cells).put("hiddenWidgetIds", new JSONArray(hiddenWidgetIds));
        } catch (JSONException e) {
            throw new IllegalStateException("Cannot encode Home layout", e);
        }
        SharedPreferences.Editor edit = host.getSharedPreferences("inkhome", Context.MODE_PRIVATE).edit();
        HomeSections.clearLegacyData(edit);
        edit.putString(KEY, data.toString()).apply();
    }
}

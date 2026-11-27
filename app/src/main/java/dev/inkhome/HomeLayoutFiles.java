package dev.inkhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Version 1 files contain only portable, built-in tiles. */
final class HomeLayoutFiles {
    private static final int EXPORT = 0x4903, IMPORT = 0x4904;
    private static final int MAX_PAGES = 4096;
    private static final int MAX_BYTES = 256 * 1024;
    private static final List<String> KINDS = Arrays.asList("current", "next", "apps", "clock",
            "calendar", "goal", "month", "battery", "moon");

    static void exportLayout(MainActivity a) {
        pick(a, new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .putExtra(Intent.EXTRA_TITLE, "ink-home-layout.json"), EXPORT);
    }

    static void importLayout(MainActivity a) {
        pick(a, new Intent(Intent.ACTION_OPEN_DOCUMENT), IMPORT);
    }

    private static void pick(MainActivity a, Intent intent, int code) {
        try {
            a.startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json"), code);
        } catch (RuntimeException e) {
            a.toast(a.getString(R.string.home_layout_file_picker_unavailable));
        }
    }

    static boolean result(MainActivity a, int request, int result, Intent data) {
        if (request != EXPORT && request != IMPORT) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) {
            a.toast(a.getString(R.string.home_layout_file_choose_file));
            return true;
        }
        a.io.execute(() -> {
            try {
                if (request == EXPORT) {
                    write(a, uri);
                    show(a, () -> a.toast(a.getString(R.string.home_layout_file_exported)));
                } else {
                    HomeLayout layout = read(a, uri);
                    show(a, () -> confirm(a, layout));
                }
            } catch (IOException | JSONException | RuntimeException e) {
                String message = request == EXPORT ? a.getString(R.string.home_layout_file_export_failed)
                        : a.getString(R.string.home_layout_file_import_failed);
                show(a, () -> a.toast(message));
            }
        });
        return true;
    }

    private static void show(MainActivity a, Runnable action) {
        a.ui.post(() -> {
            if (!a.isDestroyed() && !a.isFinishing()) action.run();
        });
    }

    private static void write(MainActivity a, Uri uri) throws JSONException, IOException {
        HomeLayout layout = HomeLayout.load(a);
        if (layout.pages > MAX_PAGES) throw new IOException("Too many pages to export");
        JSONArray entries = new JSONArray();
        for (HomeLayout.Tile t : layout.tiles) {
            if ("widget".equals(t.kind)) continue;
            entries.put(new JSONObject().put("kind", t.kind).put("page", t.page)
                    .put("x", t.x).put("y", t.y).put("w", t.w).put("h", t.h));
        }
        byte[] bytes = new JSONObject().put("version", 1).put("pages", layout.pages)
                .put("tiles", entries).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Layout too large");
        try (OutputStream out = a.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Document unavailable");
            out.write(bytes);
        }
    }

    private static HomeLayout read(MainActivity a, Uri uri) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = a.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Document unavailable");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (bytes.size() + n > MAX_BYTES) throw new IOException("Layout too large");
                bytes.write(buffer, 0, n);
            }
        }
        String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            return decode(reader);
        }
    }

    private static HomeLayout decode(JsonReader r) throws IOException {
        HomeLayout layout = new HomeLayout();
        int version = -1, pages = -1;
        boolean hasTiles = false;
        r.beginObject();
        while (r.hasNext()) {
            switch (r.nextName()) {
                case "version":
                    if (version != -1) throw new IOException("Duplicate version");
                    version = number(r);
                    break;
                case "pages":
                    if (pages != -1) throw new IOException("Duplicate page count");
                    pages = number(r);
                    break;
                case "tiles":
                    if (hasTiles) throw new IOException("Duplicate tiles");
                    hasTiles = true;
                    r.beginArray();
                    while (r.hasNext()) layout.tiles.add(tile(r));
                    r.endArray();
                    break;
                default: throw new IOException("Unknown layout field");
            }
        }
        r.endObject();
        if (r.peek() != JsonToken.END_DOCUMENT || version != 1 || pages <= 0 || pages > MAX_PAGES || !hasTiles)
            throw new IOException("Incomplete or unsupported layout");
        layout.pages = pages;
        for (HomeLayout.Tile t : layout.tiles) {
            if (!layout.fits(t, t.page, t.x, t.y, t.w, t.h))
                throw new IOException("Invalid placement");
        }
        return layout;
    }

    private static int number(JsonReader r) throws IOException {
        if (r.peek() != JsonToken.NUMBER) throw new IOException("Expected integer");
        int n = Integer.parseInt(r.nextString());
        if (n < 0) throw new IOException("Negative layout value");
        return n;
    }

    private static HomeLayout.Tile tile(JsonReader r) throws IOException {
        HomeLayout.Tile t = new HomeLayout.Tile(null, -1, -1, -1, -1, -1, -1);
        r.beginObject();
        while (r.hasNext()) {
            switch (r.nextName()) {
                case "kind":
                    if (t.kind != null || r.peek() != JsonToken.STRING) throw new IOException("Invalid kind");
                    t.kind = r.nextString();
                    break;
                case "page": t.page = field(r, t.page); break;
                case "x": t.x = field(r, t.x); break;
                case "y": t.y = field(r, t.y); break;
                case "w": t.w = field(r, t.w); break;
                case "h": t.h = field(r, t.h); break;
                default: throw new IOException("Unknown tile field");
            }
        }
        r.endObject();
        if (!KINDS.contains(t.kind)) throw new IOException("Unknown tile kind");
        return t;
    }

    private static int field(JsonReader reader, int previous) throws IOException {
        if (previous != -1) throw new IOException("Duplicate tile field");
        return number(reader);
    }

    private static void confirm(MainActivity a, HomeLayout replacement) {
        String[] choices = {a.getString(android.R.string.cancel), a.getString(R.string.home_layout_file_replace)};
        a.choose(a.getString(R.string.home_layout_file_replace_prompt), choices, -1, which -> {
            if (which != 1) return;
            HomeLayout previous = HomeLayout.load(a);
            replacement.save(a);
            a.store.setUseHomePreset(false);
            for (HomeLayout.Tile t : previous.tiles) {
                if ("widget".equals(t.kind)) {
                    try {
                        a.homeWidgets.remove(t.widgetId);
                    } catch (RuntimeException e) {
                        Log.w("HomeLayoutFiles", "Could not release widget ID", e);
                    }
                }
            }
            a.recreate();
        });
    }
}

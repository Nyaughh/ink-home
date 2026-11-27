package dev.inkhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import dev.inkhome.folio.DisplayProbe;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.DisplayClass;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Book covers: pulled from the file itself (EPUB cover image, first PDF page, first comic page),
 * cropped and cached for the display class; a typographic cover is drawn when a file has none.
 */
final class Covers {

    static final int W = 301, H = 430;
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private static File dir(Context c, DisplayClass display) {
        File d = new File(c.getCacheDir(), "covers-v2/" + display.name());
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Cached cover if one exists, else a generated one. Safe on the UI thread once extract() has run. */
    static Bitmap get(Context c, Library.Book b, boolean real) {
        DisplayClass display = DisplayProbe.displayClass(c);
        String k = key(display, b, real);
        Bitmap hit = MEM.get(k);
        if (hit != null) return hit;
        Bitmap bmp = null;
        if (real) {
            File f = new File(dir(c, display), b.key() + ".png");
            if (f.exists()) bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        }
        if (bmp == null) bmp = generated(c, b);
        synchronized (MEM) {
            // A background extraction may have published a real cover during this read.
            hit = MEM.get(k);
            if (hit != null) return hit;
            MEM.put(k, bmp);
        }
        return bmp;
    }

    private static String key(DisplayClass display, Library.Book book, boolean real) {
        return display.name() + (real ? ":real:" : ":generated:") + book.key();
    }

    /** Drops in-memory covers, e.g. after titles were updated from EPUB metadata. */
    static void flush() { MEM.evictAll(); }

    /** True when this book still needs a cover pass. */
    static boolean pending(Context c, Library.Book b) {
        File d = dir(c, DisplayProbe.displayClass(c));
        return !new File(d, b.key() + ".png").exists() && !new File(d, b.key() + ".none").exists();
    }

    /** Extracts and caches the cover (and EPUB title/author). Call off the UI thread. */
    static void extract(Context c, Store store, Library.Book b) {
        DisplayClass display = DisplayProbe.displayClass(c);
        File d = dir(c, display);
        Bitmap raw = null;
        try {
            switch (b.ext) {
                case "epub": raw = epub(store, b); break;
                case "pdf": raw = pdf(b.file); break;
                case "cbz": raw = firstZipImage(b.file); break;
                default: break;
            }
        } catch (Throwable t) {
            raw = null;
        }
        try {
            if (raw == null) {
                new File(d, b.key() + ".none").createNewFile();
                return;
            }
            Bitmap out = toCover(raw, display);
            raw.recycle();
            File staged = File.createTempFile("cover", ".tmp", d);
            try {
                try (FileOutputStream fo = new FileOutputStream(staged)) {
                    if (!out.compress(Bitmap.CompressFormat.PNG, 100, fo)) return;
                }
                if (staged.renameTo(new File(d, b.key() + ".png"))) MEM.put(key(display, b, true), out);
            } finally {
                if (staged.exists()) staged.delete();
            }
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- sources

    private static Bitmap epub(Store store, Library.Book b) throws Exception {
        try (ZipFile zip = new ZipFile(b.file)) {
            String opfPath = null;
            ZipEntry container = zip.getEntry("META-INF/container.xml");
            if (container != null) {
                XmlPullParser x = Xml.newPullParser();
                x.setInput(zip.getInputStream(container), null);
                for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT && opfPath == null; e = x.next()) {
                    if (e == XmlPullParser.START_TAG && x.getName().endsWith("rootfile")) opfPath = x.getAttributeValue(null, "full-path");
                }
            }
            if (opfPath == null) return null;
            ZipEntry opf = zip.getEntry(opfPath);
            if (opf == null) return null;
            String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

            Map<String, String> hrefById = new HashMap<>();
            String coverId = null, coverHref = null, firstImage = null, title = "", author = "";
            XmlPullParser x = Xml.newPullParser();
            x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            x.setInput(zip.getInputStream(opf), null);
            String textFor = null;
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                if (e == XmlPullParser.START_TAG) {
                    String n = x.getName().toLowerCase(Locale.ROOT);
                    if (n.endsWith("title") && title.isEmpty()) textFor = "title";
                    else if (n.endsWith("creator") && author.isEmpty()) textFor = "author";
                    else if (n.equals("meta") && "cover".equals(x.getAttributeValue(null, "name"))) coverId = x.getAttributeValue(null, "content");
                    else if (n.equals("item")) {
                        String id = x.getAttributeValue(null, "id"), href = x.getAttributeValue(null, "href");
                        String type = x.getAttributeValue(null, "media-type"), props = x.getAttributeValue(null, "properties");
                        if (id != null && href != null) hrefById.put(id, href);
                        boolean image = type != null && type.startsWith("image/");
                        if (image && props != null && props.contains("cover-image")) coverHref = href;
                        if (image && firstImage == null) firstImage = href;
                        if (image && coverHref == null && href != null && (href.toLowerCase(Locale.ROOT).contains("cover")
                                || (id != null && id.toLowerCase(Locale.ROOT).contains("cover")))) coverHref = href;
                    }
                } else if (e == XmlPullParser.TEXT && textFor != null) {
                    String t = x.getText().trim();
                    if (!t.isEmpty()) {
                        if (textFor.equals("title")) title = t; else author = t;
                    }
                } else if (e == XmlPullParser.END_TAG) {
                    textFor = null;
                }
            }
            store.setMeta(b.key(), title + "\u0001" + author);
            String href = coverId != null && hrefById.containsKey(coverId) ? hrefById.get(coverId) : coverHref;
            if (href == null) href = firstImage;
            if (href == null) return null;
            String path = normalize(base + URLDecoder.decode(href, "UTF-8"));
            ZipEntry img = zip.getEntry(path);
            if (img == null) return null;
            return decode(read(zip.getInputStream(img)));
        }
    }

    private static Bitmap pdf(File f) throws Exception {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer r = new PdfRenderer(fd)) {
            if (r.getPageCount() == 0) return null;
            try (PdfRenderer.Page page = r.openPage(0)) {
                float scale = Math.min(W * 1.5f / page.getWidth(), H * 1.5f / page.getHeight());
                int w = Math.max(1, Math.round(page.getWidth() * scale)), h = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bmp;
            }
        }
    }

    private static Bitmap firstZipImage(File f) throws Exception {
        try (ZipFile zip = new ZipFile(f)) {
            List<ZipEntry> images = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (!e.isDirectory() && !n.contains("__macosx") && (n.endsWith(".jpg") || n.endsWith(".jpeg")
                        || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".gif"))) images.add(e);
            }
            if (images.isEmpty()) return null;
            images.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return decode(read(zip.getInputStream(images.get(0))));
        }
    }

    // ---------------------------------------------------------------- image helpers

    private static byte[] read(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n, total = 0;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
                total += n;
                if (total > 20 * 1024 * 1024) return null;
            }
            return bo.toByteArray();
        }
    }

    private static Bitmap decode(byte[] data) {
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= W && o.outHeight / (sample * 2) >= H) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    /** Center-crops without stretching and compensates for the lower contrast of ink panels. */
    private static Bitmap toCover(Bitmap src, DisplayClass display) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        float scale = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());
        float sw = W / scale, sh = H / scale;
        float sx = (src.getWidth() - sw) / 2f, sy = (src.getHeight() - sh) / 2f;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (display != DisplayClass.EMISSIVE) {
            ColorMatrix colors = new ColorMatrix();
            colors.setSaturation(display == DisplayClass.MONO_EINK ? 0f : 1.18f);
            float contrast = 1.12f, shift = (1f - contrast) * 128f;
            colors.postConcat(new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, shift,
                    0, contrast, 0, 0, shift,
                    0, 0, contrast, 0, shift,
                    0, 0, 0, 1, 0}));
            p.setColorFilter(new ColorMatrixColorFilter(colors));
        }
        c.drawBitmap(src, new Rect(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh)), new RectF(0, 0, W, H), p);
        return out;
    }

    /** A clean typographic cover: format band at the top, title in serif, author underneath. */
    private static Bitmap generated(Context context, Library.Book b) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xFF333333);
        label.setTextSize(20);
        label.setTypeface(Type.sans(context, 600));
        String kind = b.kind == Library.Kind.COMIC ? "Comic" : b.format();
        c.drawText(kind, 24, 52, label);

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.BLACK);
        tp.setTypeface(Type.reading(context, false));
        float size = b.title.length() > 40 ? 30 : b.title.length() > 20 ? 36 : 42;
        tp.setTextSize(size);
        int width = W - 48;
        StaticLayout sl = StaticLayout.Builder.obtain(b.title, 0, b.title.length(), tp, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.05f)
                .setMaxLines(Math.min(6, (int) (232 / tp.getFontSpacing()))).setEllipsize(TextUtils.TruncateAt.END).build();
        c.save();
        c.translate(24, 96);
        sl.draw(c);
        c.restore();

        Paint rule = new Paint();
        rule.setColor(0xFF111111);
        float ruleY = 96 + sl.getHeight() + 22;
        c.drawRect(24, ruleY, 64, ruleY + 3, rule);

        if (!b.author.isEmpty()) {
            TextPaint ap = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            ap.setColor(0xFF555555);
            ap.setTextSize(22);
            ap.setTypeface(Type.reading(context, true));
            CharSequence a = TextUtils.ellipsize(b.author, ap, width, TextUtils.TruncateAt.END);
            c.drawText(a, 0, a.length(), 24, ruleY + 46, ap);
        }
        return out;
    }

    private static String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String s : path.split("/")) {
            if (s.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); }
            else if (!s.equals(".") && !s.isEmpty()) parts.add(s);
        }
        return String.join("/", parts);
    }
}

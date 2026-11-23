package dev.inkhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A book for the built-in reader: an EPUB (spine, contents, chapters, images) or a plain
 * text file split into sections. Chapters are turned into styled text on demand, so even a
 * book with hundreds of chapters opens instantly and only one chapter is in memory at a time.
 */
final class Epub implements Closeable {

    static final class TocEntry {
        final String title;
        final int chapter;
        TocEntry(String title, int chapter) { this.title = title; this.chapter = chapter; }
    }

    String title = "", author = "";
    /** Spine items in reading order (zip paths), or text sections for TXT. */
    final List<String> spine = new ArrayList<>();
    /** Uncompressed size of each chapter; used to estimate whole-book progress. */
    final List<Long> sizes = new ArrayList<>();
    final List<TocEntry> toc = new ArrayList<>();
    long totalSize = 0;

    private ZipFile zip;
    private List<String> textSections;

    private Epub() { }

    static Epub open(File f) throws Exception {
        Epub e = new Epub();
        String name = f.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt")) e.openText(f); else e.openEpub(f);
        for (long s : e.sizes) e.totalSize += Math.max(1, s);
        return e;
    }

    // ---------------------------------------------------------------- EPUB structure

    private void openEpub(File f) throws Exception {
        zip = new ZipFile(f);
        String opfPath = null;
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container != null) {
            XmlPullParser x = Xml.newPullParser();
            x.setInput(zip.getInputStream(container), null);
            for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT && opfPath == null; ev = x.next())
                if (ev == XmlPullParser.START_TAG && x.getName().endsWith("rootfile")) opfPath = x.getAttributeValue(null, "full-path");
        }
        if (opfPath == null) throw new IllegalStateException("Not an EPUB");
        String base = dirOf(opfPath);

        Map<String, String> hrefById = new HashMap<>();
        Map<String, String> typeById = new HashMap<>();
        List<String> spineIds = new ArrayList<>();
        String ncxId = null, navHref = null;
        XmlPullParser x = Xml.newPullParser();
        x.setInput(zip.getInputStream(zip.getEntry(opfPath)), null);
        String textFor = null;
        for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = x.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = x.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith("title") && title.isEmpty()) textFor = "t";
                else if (n.endsWith("creator") && author.isEmpty()) textFor = "a";
                else if (n.equals("item")) {
                    String id = x.getAttributeValue(null, "id"), href = x.getAttributeValue(null, "href");
                    String type = x.getAttributeValue(null, "media-type"), props = x.getAttributeValue(null, "properties");
                    if (id != null && href != null) {
                        hrefById.put(id, resolve(base, href));
                        typeById.put(id, type == null ? "" : type);
                        if (props != null && props.contains("nav")) navHref = resolve(base, href);
                    }
                } else if (n.equals("itemref")) {
                    String idref = x.getAttributeValue(null, "idref");
                    String linear = x.getAttributeValue(null, "linear");
                    if (idref != null && !"no".equals(linear)) spineIds.add(idref);
                } else if (n.equals("spine")) {
                    ncxId = x.getAttributeValue(null, "toc");
                }
            } else if (ev == XmlPullParser.TEXT && textFor != null) {
                String t = x.getText().trim();
                if (!t.isEmpty()) { if (textFor.equals("t")) title = t; else author = t; }
            } else if (ev == XmlPullParser.END_TAG) {
                textFor = null;
            }
        }
        for (String id : spineIds) {
            String href = hrefById.get(id);
            String type = typeById.get(id);
            if (href == null || (type != null && !type.contains("html") && !type.contains("xml"))) continue;
            ZipEntry ze = zip.getEntry(href);
            if (ze == null) continue;
            spine.add(href);
            sizes.add(ze.getSize() > 0 ? ze.getSize() : 4096L);
        }
        try {
            if (navHref != null) readNav(navHref);
            if (toc.isEmpty() && ncxId != null && hrefById.containsKey(ncxId)) readNcx(hrefById.get(ncxId));
        } catch (Exception ignored) { }
    }

    private void readNav(String path) throws Exception {
        String html = new String(read(zip.getInputStream(zip.getEntry(path))), StandardCharsets.UTF_8);
        int navStart = html.indexOf("epub:type=\"toc\"");
        if (navStart < 0) navStart = 0;
        int navEnd = html.indexOf("</nav>", navStart);
        String nav = html.substring(navStart, navEnd < 0 ? html.length() : navEnd);
        Matcher m = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL).matcher(nav);
        String dir = dirOf(path);
        while (m.find()) addToc(stripTags(m.group(2)), resolve(dir, m.group(1)));
    }

    private void readNcx(String path) throws Exception {
        XmlPullParser x = Xml.newPullParser();
        x.setInput(zip.getInputStream(zip.getEntry(path)), null);
        String dir = dirOf(path), label = null;
        boolean inText = false;
        for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = x.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = x.getName();
                if (n.equals("text")) inText = true;
                else if (n.equals("content") && label != null) {
                    addToc(label, resolve(dir, x.getAttributeValue(null, "src")));
                    label = null;
                }
            } else if (ev == XmlPullParser.TEXT && inText) {
                label = x.getText().trim();
            } else if (ev == XmlPullParser.END_TAG && x.getName().equals("text")) {
                inText = false;
            }
        }
    }

    private void addToc(String label, String href) {
        if (label == null || label.trim().isEmpty() || href == null) return;
        int hash = href.indexOf('#');
        String file = hash >= 0 ? href.substring(0, hash) : href;
        int idx = spine.indexOf(file);
        if (idx >= 0) toc.add(new TocEntry(label.trim(), idx));
    }

    // ---------------------------------------------------------------- plain text

    private void openText(File f) throws Exception {
        String text;
        try (InputStream in = new java.io.FileInputStream(f)) { text = new String(read(in), StandardCharsets.UTF_8); }
        title = Library.titleFromName(f.getName());
        textSections = new ArrayList<>();
        int i = 0;
        // ~40k character sections, cut at a paragraph break, so pagination stays quick.
        while (i < text.length()) {
            int end = Math.min(text.length(), i + 40000);
            if (end < text.length()) {
                int nl = text.indexOf("\n\n", end);
                if (nl > 0 && nl - end < 8000) end = nl;
            }
            textSections.add(text.substring(i, end));
            i = end;
        }
        if (textSections.isEmpty()) textSections.add("");
        for (int s = 0; s < textSections.size(); s++) {
            spine.add("section-" + s);
            sizes.add((long) textSections.get(s).length());
        }
    }

    // ---------------------------------------------------------------- chapters

    /** Styled text for a chapter; images are scaled to fit maxW×maxH and turned greyscale. */
    Spanned chapter(int index, Resources res, int maxW, int maxH) throws Exception {
        if (textSections != null) return new SpannableStringBuilder(textSections.get(index));
        String path = spine.get(index);
        String html = new String(read(zip.getInputStream(zip.getEntry(path))), StandardCharsets.UTF_8);
        html = html.replaceAll("(?is)<head.*?</head>", "")
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?is)<style.*?</style>", "");
        // Headings get a little room around them; Html.fromHtml handles the rest.
        String dir = dirOf(path);
        Html.ImageGetter images = src -> image(dir, src, res, maxW, maxH);
        // Compact mode: one line break between paragraphs; the reader adds a small gap itself.
        Spanned s = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, images, null);
        return centerImages(trim(s));
    }

    /** Pictures that sit on a line of their own are centred, like in any reading app. */
    private static Spanned centerImages(Spanned s) {
        android.text.style.ImageSpan[] imgs = s.getSpans(0, s.length(), android.text.style.ImageSpan.class);
        if (imgs.length == 0) return s;
        SpannableStringBuilder b = new SpannableStringBuilder(s);
        for (android.text.style.ImageSpan img : imgs) {
            int start = b.getSpanStart(img), end = b.getSpanEnd(img);
            int ps = start;
            while (ps > 0 && b.charAt(ps - 1) != '\n') ps--;
            int pe = end;
            while (pe < b.length() && b.charAt(pe) != '\n') pe++;
            if (b.subSequence(ps, start).toString().trim().isEmpty() && b.subSequence(end, pe).toString().trim().isEmpty())
                b.setSpan(new android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER), ps, pe, Spanned.SPAN_PARAGRAPH);
        }
        return b;
    }

    private Drawable image(String dir, String src, Resources res, int maxW, int maxH) {
        try {
            String path = resolve(dir, src);
            ZipEntry ze = zip.getEntry(path);
            if (ze == null) return null;
            byte[] data = read(zip.getInputStream(ze));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b == null) return null;
            float scale = Math.min(1f, Math.min((float) maxW / b.getWidth(), (float) maxH / b.getHeight()));
            int w = Math.max(1, Math.round(b.getWidth() * scale)), h = Math.max(1, Math.round(b.getHeight() * scale));
            Bitmap grey = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(grey);
            c.drawColor(0xFFFFFFFF);
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(0);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(m));
            c.drawBitmap(b, null, new android.graphics.Rect(0, 0, w, h), p);
            b.recycle();
            BitmapDrawable d = new BitmapDrawable(res, grey);
            d.setBounds(0, 0, w, h);
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Whole-book progress (0..1) for a position inside a chapter. */
    float progress(int chapter, float withinChapter) {
        if (totalSize <= 0) return 0;
        long before = 0;
        for (int i = 0; i < chapter && i < sizes.size(); i++) before += Math.max(1, sizes.get(i));
        long cur = chapter < sizes.size() ? Math.max(1, sizes.get(chapter)) : 0;
        return Math.max(0f, Math.min(1f, (before + cur * withinChapter) / (float) totalSize));
    }

    /** The chapter that contains a whole-book fraction, and the fraction within it. */
    float[] locate(float fraction) {
        long target = (long) (fraction * totalSize), acc = 0;
        for (int i = 0; i < sizes.size(); i++) {
            long s = Math.max(1, sizes.get(i));
            if (acc + s >= target) return new float[]{i, (target - acc) / (float) s};
            acc += s;
        }
        return new float[]{Math.max(0, sizes.size() - 1), 1f};
    }

    String chapterTitle(int chapter) {
        String best = "";
        for (TocEntry t : toc) if (t.chapter <= chapter) best = t.title;
        return best;
    }

    @Override public void close() {
        try { if (zip != null) zip.close(); } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- helpers

    private static Spanned trim(Spanned s) {
        int a = 0, b = s.length();
        while (a < b && Character.isWhitespace(s.charAt(a))) a++;
        while (b > a && Character.isWhitespace(s.charAt(b - 1))) b--;
        return (Spanned) s.subSequence(a, b);
    }

    static String stripTags(String s) {
        return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ").trim();
    }

    private static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(0, i + 1) : "";
    }

    private static String resolve(String dir, String href) {
        try { href = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8"); } catch (Exception ignored) { }
        List<String> parts = new ArrayList<>();
        for (String s : (dir + href).split("/")) {
            if (s.equals("..")) { if (!parts.isEmpty()) parts.remove(parts.size() - 1); }
            else if (!s.equals(".") && !s.isEmpty()) parts.add(s);
        }
        String out = String.join("/", parts);
        int hash = out.indexOf('#');
        return hash >= 0 && !href.startsWith("#") ? out : out;
    }

    private static byte[] read(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }
}

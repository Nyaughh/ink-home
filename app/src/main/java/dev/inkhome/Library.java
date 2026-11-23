package dev.inkhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The book library: files found on storage, merged with NeoReader's own records
 * (progress, last read, finished) and with what the user told Ink Home.
 */
final class Library {

    private static final String TAG = "InkHome";
    static final Uri NEO_METADATA = Uri.parse("content://com.onyx.content.database.ContentProvider/Metadata");

    enum Kind { BOOK, COMIC, PDF }

    static final class Book {
        final File file;
        final String path, ext;
        final Kind kind;
        final long modified, size;
        String title, author = "";
        /** 0..1, or -1 when unknown. */
        float progress = -1f;
        String pages = "";
        long lastRead;
        boolean finished;

        Book(File f, String ext) {
            this.file = f;
            this.path = f.getAbsolutePath();
            this.ext = ext;
            this.kind = kindOf(ext);
            this.modified = f.lastModified();
            this.size = f.length();
            this.title = titleFromName(f.getName());
        }

        String key() { return Integer.toHexString((path + "|" + modified + "|" + size).hashCode()); }

        String format() { return ext.toUpperCase(Locale.ROOT); }

        String folder() {
            File parent = file.getParentFile();
            return parent == null ? "" : parent.getName();
        }

        boolean started() { return lastRead > 0; }

        int percent() { return progress < 0 ? -1 : Math.round(progress * 100f); }

        /** "42%  ·  p. 120 of 300" style line, or the format when nothing is known yet. */
        String progressLine() {
            if (finished) return "Finished";
            int pc = percent();
            if (pc < 0) return started() ? "Started" : "Not started";
            String s = pc + "%";
            if (!pages.isEmpty()) s += "  ·  p. " + pages;
            return s;
        }
    }

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put("epub", "application/epub+zip");
        MIME.put("pdf", "application/pdf");
        MIME.put("mobi", "application/x-mobipocket-ebook");
        MIME.put("azw", "application/vnd.amazon.ebook");
        MIME.put("azw3", "application/vnd.amazon.ebook");
        MIME.put("fb2", "application/x-fb2");
        MIME.put("txt", "text/plain");
        MIME.put("djvu", "image/vnd.djvu");
        MIME.put("cbz", "application/x-cbz");
        MIME.put("cbr", "application/x-cbr");
        MIME.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME.put("doc", "application/msword");
        MIME.put("rtf", "application/rtf");
    }

    static String mime(String ext) {
        String m = MIME.get(ext);
        return m == null ? "*/*" : m;
    }

    static Kind kindOf(String ext) {
        switch (ext) {
            case "cbz": case "cbr": return Kind.COMIC;
            case "pdf": case "djvu": return Kind.PDF;
            default: return Kind.BOOK;
        }
    }

    // ---------------------------------------------------------------- loading

    /** Scans storage and merges reader data. Call off the UI thread. */
    static List<Book> load(Context c, Store store) { return load(c, store, null); }

    /**
     * With {@code known} set, skips the storage walk and only refreshes those files' reading data;
     * that's what happens after you close a book, so returning home stays quick.
     */
    static List<Book> load(Context c, Store store, List<File> known) {
        List<Book> books = new ArrayList<>();
        if (known == null) {
            walk(Environment.getExternalStorageDirectory(), 0, books, true);
        } else {
            for (File f : known) {
                if (!f.isFile()) continue;
                String n = f.getName();
                books.add(new Book(f, n.substring(n.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)));
            }
        }
        Map<String, Book> byPath = new HashMap<>();
        for (Book b : books) byPath.put(b.path, b);

        mergeNeoReader(c, byPath);

        for (Book b : books) {
            String meta = store.meta(b.key());
            if (meta != null) {
                String[] parts = meta.split("\u0001", -1);
                if (!parts[0].isEmpty()) b.title = parts[0];
                if (parts.length > 1 && !parts[1].isEmpty() && b.author.isEmpty()) b.author = parts[1];
            }
            b.lastRead = Math.max(b.lastRead, store.openedAt(b.path));
            int fo = store.finishedOverride(b.path);
            if (fo >= 0) b.finished = fo == 1;
        }
        return books;
    }

    private static void mergeNeoReader(Context c, Map<String, Book> byPath) {
        String[] cols = {"nativeAbsolutePath", "title", "authors", "progress", "lastAccess", "readingStatus"};
        try (Cursor cur = c.getContentResolver().query(NEO_METADATA, cols, null, null, null)) {
            if (cur == null) return;
            while (cur.moveToNext()) {
                Book b = byPath.get(cur.getString(0));
                if (b == null) continue;
                String title = cur.getString(1);
                if (title != null && !title.trim().isEmpty()) b.title = title.trim();
                String authors = cur.getString(2);
                if (authors != null) {
                    authors = authors.replace("[", "").replace("]", "").replace("\"", "").trim();
                    if (!authors.isEmpty()) b.author = authors;
                }
                String progress = cur.getString(3);
                if (progress != null && progress.contains("/")) {
                    try {
                        String[] pq = progress.split("/");
                        float cur0 = Float.parseFloat(pq[0].trim());
                        float total = Float.parseFloat(pq[1].trim());
                        if (total > 0) {
                            b.progress = Math.max(0f, Math.min(1f, cur0 / total));
                            if (total > 1) b.pages = Math.round(cur0) + " of " + Math.round(total);
                        }
                    } catch (NumberFormatException ignored) { }
                }
                b.lastRead = Math.max(b.lastRead, cur.getLong(4));
                b.finished = cur.getInt(5) == 2;
            }
        } catch (Exception e) {
            Log.i(TAG, "NeoReader library not readable: " + e);
        }
    }

    private static void walk(File dir, int depth, List<Book> out, boolean isRoot) {
        if (depth > 7 || out.size() > 5000) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (f.isDirectory()) {
                if (isRoot && (name.equals("Android") || name.equals("DCIM") || name.equals("Music")
                        || name.equals("Ringtones") || name.equals("Alarms") || name.equals("Notifications")
                        || name.equals("Podcasts") || name.equals("Movies") || name.equals("Recordings"))) continue;
                walk(f, depth + 1, out, false);
            } else {
                int dot = name.lastIndexOf('.');
                if (dot < 0) continue;
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (MIME.containsKey(ext) && f.length() > 0) out.add(new Book(f, ext));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    static String titleFromName(String name) {
        int dot = name.lastIndexOf('.');
        String t = dot > 0 ? name.substring(0, dot) : name;
        return t.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", b / (1024.0 * 1024.0));
        return String.format(Locale.ROOT, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0));
    }

    static String ago(long t) {
        if (t <= 0) return "";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        if (h < 24) return h + (h == 1 ? " hour ago" : " hours ago");
        long d = h / 24;
        if (d < 7) return d == 1 ? "yesterday" : d + " days ago";
        if (d < 60) return (d / 7) + (d / 7 == 1 ? " week ago" : " weeks ago");
        return (d / 30) + " months ago";
    }
}

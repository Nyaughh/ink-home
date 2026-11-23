package dev.inkhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sleep pictures: a 758×1024 greyscale PNG in Pictures/Ink Home, made from the current book or
 * from any picture the user picks. Boox copies a picture when it's set as the screensaver, so
 * the user sets this file in Boox (Storage › Pictures › Ink Home) after each new picture.
 */
final class SleepScreen {

    static final int W = 758, H = 1024;

    static File dir() {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Ink Home");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File file() { return new File(dir(), "Ink Home sleep.png"); }

    /** Sleep picture from the current book. Call off the UI thread. */
    static boolean writeBook(Context c, Library.Book book, boolean realCovers) {
        Bitmap out = blank();
        Canvas cv = new Canvas(out);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.BLACK);
        if (book != null) {
            Bitmap cover = Covers.get(c, book, realCovers);
            float cw = 420, ch = cw * Covers.H / Covers.W;
            float left = (W - cw) / 2f, top = 120;
            Paint edge = new Paint();
            edge.setColor(0xFF9A9A9A);
            cv.drawRect(left - 2, top - 2, left + cw + 2, top + ch + 2, edge);
            cv.drawBitmap(cover, null, new RectF(left, top, left + cw, top + ch), new Paint(Paint.FILTER_BITMAP_FLAG));

            tp.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            tp.setTextSize(36);
            StaticLayout title = StaticLayout.Builder.obtain(book.title, 0, book.title.length(), tp, W - 140)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
            cv.save();
            cv.translate(70, top + ch + 44);
            title.draw(cv);
            cv.restore();
            if (!book.author.isEmpty()) {
                tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                tp.setTextSize(24);
                tp.setColor(0xFF555555);
                CharSequence a = TextUtils.ellipsize(book.author, tp, W - 140, TextUtils.TruncateAt.END);
                cv.drawText(a, 0, a.length(), (W - tp.measureText(a, 0, a.length())) / 2f, top + ch + 44 + title.getHeight() + 40, tp);
            }
        } else {
            tp.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            tp.setTextSize(56);
            String t = "Sleeping";
            cv.drawText(t, (W - tp.measureText(t)) / 2f, H / 2f, tp);
        }
        footer(cv);
        return save(c, out);
    }

    /** Sleep picture from a picture file. fill = crop to fill the screen, else fit with white margins. */
    static boolean writeImage(Context c, File image, boolean fill) {
        Bitmap src = decode(image, W * 2, H * 2);
        if (src == null) return false;
        Bitmap out = blank();
        Canvas cv = new Canvas(out);
        float s = fill ? Math.max((float) W / src.getWidth(), (float) H / src.getHeight())
                : Math.min((float) W / src.getWidth(), (float) H / src.getHeight());
        float w = src.getWidth() * s, h = src.getHeight() * s;
        RectF dst = new RectF((W - w) / 2f, (H - h) / 2f, (W + w) / 2f, (H + h) / 2f);
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(grey));
        cv.drawBitmap(src, null, dst, p);
        src.recycle();
        return save(c, out);
    }

    /** Pictures on the tablet the user could use (Pictures, Download, DCIM), newest first. */
    static List<File> pictures() {
        List<File> out = new ArrayList<>();
        File root = Environment.getExternalStorageDirectory();
        for (String d : new String[]{Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DCIM, "Screensaver"}) {
            collect(new File(root, d), out, 0);
        }
        out.remove(file());
        out.removeIf(f -> f.getAbsolutePath().equals(file().getAbsolutePath()));
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private static void collect(File dir, List<File> out, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 4) return;
        for (File f : kids) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) collect(f, out, depth + 1);
            else {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".bmp")) out.add(f);
            }
        }
    }

    /** Small greyscale thumbnail for the picker. */
    static Bitmap thumb(File f, int w, int h) {
        Bitmap b = decode(f, w, h);
        if (b == null) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Color.WHITE);
        float s = Math.max((float) w / b.getWidth(), (float) h / b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(grey));
        cv.drawBitmap(b, null, new RectF((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f), p);
        b.recycle();
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private static Bitmap blank() {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        out.eraseColor(Color.WHITE);
        return out;
    }

    private static void footer(Canvas cv) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFF555555);
        tp.setTextSize(22);
        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        String s = "Press the power button to wake";
        cv.drawText(s, (W - tp.measureText(s)) / 2f, H - 48, tp);
    }

    private static Bitmap decode(File f, int maxW, int maxH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b == null) return null;
            int rot = 0;
            try {
                int or = new ExifInterface(f.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rot = or == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : or == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (Exception ignored) { }
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean save(Context c, Bitmap out) {
        try (FileOutputStream fo = new FileOutputStream(file())) {
            out.compress(Bitmap.CompressFormat.PNG, 100, fo);
        } catch (Exception e) {
            return false;
        }
        // Index it, or the Boox picture picker won't list it.
        c.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file())));
        return true;
    }
}

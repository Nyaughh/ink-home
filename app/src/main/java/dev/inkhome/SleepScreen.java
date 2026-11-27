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
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import dev.inkhome.folio.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-panel greyscale PNG in Pictures/Ink Home. Boox copies the file when the user sets it
 * as the screensaver, so it must be set again after a change.
 */
final class SleepScreen {

    static SleepSize panel(Context c) {
        Display display = c.getSystemService(WindowManager.class).getDefaultDisplay();
        Display.Mode mode = display.getMode();
        int rotation = display.getRotation();
        return SleepSize.rotated(mode.getPhysicalWidth(), mode.getPhysicalHeight(),
                rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270);
    }

    static File dir() {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Ink Home");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File file() { return new File(dir(), "Ink Home sleep.png"); }

    /** Sleep picture from the current book. Call off the UI thread. */
    static boolean writeBook(Context c, SleepSize size, Library.Book book, boolean realCovers, String detail) {
        Bitmap out = blank(size);
        Canvas cv = new Canvas(out);
        float scale = size.artworkScale();
        cv.scale(scale, scale);
        float width = size.width / scale;
        float height = size.height / scale;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setColor(Color.BLACK);
        if (book != null) {
            float left = (width - 236) / 2;
            RectF cover = new RectF(left, 74, left + 236, 74 + 236 * 10f / 7);
            cv.drawBitmap(Covers.get(c, book, realCovers), null, cover, greyPaint());
            Paint edge = new Paint();
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1 / scale);
            cv.drawRect(cover, edge);

            tp.setTypeface(Type.sans(c, 700));
            tp.setTextSize(24);
            tp.setTextAlign(Paint.Align.LEFT);
            int lineWidth = Math.max(1, Math.round(width - 96));
            StaticLayout title = StaticLayout.Builder.obtain(book.title, 0, book.title.length(), tp, lineWidth)
                    .setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
            float y = cover.bottom + 28;
            cv.save();
            cv.translate((width - lineWidth) / 2, y);
            title.draw(cv);
            cv.restore();
            y += title.getHeight();
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(Type.sans(c, 500));
            tp.setTextSize(16);
            if (!book.author.isEmpty()) {
                y += 6;
                CharSequence author = TextUtils.ellipsize(book.author, tp, lineWidth, TextUtils.TruncateAt.END);
                cv.drawText(author, 0, author.length(), width / 2, y - tp.ascent(), tp);
                y += 22;
            }
            y += 22;
            cv.drawRect(left, y + 2, cover.right, y + 2 + 1 / scale, tp);
            float progress = Math.min(1, Math.max(0, book.progress));
            cv.drawRect(left, y, left + 236 * progress, y + 5, tp);
            tp.setTypeface(Type.sans(c, 600));
            tp.setTextSize(13);
            CharSequence line = TextUtils.ellipsize(detail, tp, lineWidth, TextUtils.TruncateAt.END);
            cv.drawText(line, 0, line.length(), width / 2, y + 15 - tp.ascent(), tp);
        } else {
            tp.setTypeface(Type.sans(c, 700));
            tp.setTextSize(24);
            cv.drawText(c.getString(R.string.service_ui_sleeping), width / 2, height / 2, tp);
        }
        tp.setTypeface(Type.sans(c, 500));
        tp.setTextSize(13);
        cv.drawText(c.getString(R.string.service_ui_ink_home_asleep), width / 2, height - 26 - tp.descent(), tp);
        return save(c, out);
    }

    static boolean writeImage(Context c, SleepSize size, File image, boolean fill) {
        Bitmap src = decode(image, size.width, size.height);
        if (src == null) return false;
        Bitmap out = blank(size);
        Canvas cv = new Canvas(out);
        float scale = size.imageScale(src.getWidth(), src.getHeight(), fill);
        float w = src.getWidth() * scale, h = src.getHeight() * scale;
        float left = (size.width - w) / 2, top = (size.height - h) / 2;
        cv.drawBitmap(src, null, new RectF(left, top, left + w, top + h), greyPaint());
        src.recycle();
        return save(c, out);
    }

    private static Paint greyPaint() {
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(grey));
        return p;
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

    static Bitmap preview(File file, int width, int height) {
        return decode(file, width, height);
    }

    /** Cropped thumbnail shared by the picture picker and Home wallpaper. */
    static Bitmap thumb(File f, int w, int h, boolean mono, int paper) {
        Bitmap b = decode(f, w, h);
        if (b == null) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(paper);
        float s = Math.max((float) w / b.getWidth(), (float) h / b.getHeight());
        float bw = b.getWidth() * s, bh = b.getHeight() * s;
        cv.drawBitmap(b, null, new RectF((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f),
                mono ? greyPaint() : new Paint(Paint.FILTER_BITMAP_FLAG));
        b.recycle();
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private static Bitmap blank(SleepSize size) {
        Bitmap out = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888);
        out.eraseColor(Color.WHITE);
        return out;
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
        android.util.AtomicFile png = new android.util.AtomicFile(file());
        FileOutputStream fo = null;
        try {
            fo = png.startWrite();
            if (!out.compress(Bitmap.CompressFormat.PNG, 100, fo)) throw new java.io.IOException("PNG encoding failed");
            png.finishWrite(fo);
        } catch (Exception e) {
            if (fo != null) png.failWrite(fo);
            return false;
        } finally {
            out.recycle();
        }
        c.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file())));
        return true;
    }
}

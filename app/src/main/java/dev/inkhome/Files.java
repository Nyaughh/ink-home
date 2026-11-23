package dev.inkhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Hands a book to another reader app (Lithium, etc.) as a read-only content:// link.
 * Only files on shared storage can be served, and only to apps Ink Home granted access.
 */
public class Files extends ContentProvider {

    static final String AUTHORITY = "dev.inkhome.files";

    static Uri uriFor(File f) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).encodedPath("/" + Uri.encode(f.getAbsolutePath(), "/")).build();
    }

    private static File fileFor(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException();
        try {
            File f = new File(path).getCanonicalFile();
            String root = Environment.getExternalStorageDirectory().getCanonicalPath();
            if (!f.getPath().startsWith(root + "/") || !f.isFile()) throw new FileNotFoundException(path);
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(path);
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode.contains("w")) throw new SecurityException("Read only");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.getPath() == null ? "" : uri.getPath();
        int dot = p.lastIndexOf('.');
        return dot < 0 ? "application/octet-stream" : Library.mime(p.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    @Override public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}

package dev.inkhome;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

final class BookStorage {
    static Set<File> roots(Context context) {
        Set<File> roots = new LinkedHashSet<>();
        for (StorageVolume volume : context.getSystemService(StorageManager.class).getStorageVolumes()) {
            String state = volume.getState();
            File directory = volume.getDirectory();
            if (directory != null && (Environment.MEDIA_MOUNTED.equals(state)
                    || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) roots.add(directory);
        }
        return roots;
    }

    static boolean contains(Iterable<File> roots, File file) throws IOException {
        File target = file.getCanonicalFile();
        for (File root : roots) {
            if (target.toPath().startsWith(root.getCanonicalFile().toPath())) return true;
        }
        return false;
    }
}

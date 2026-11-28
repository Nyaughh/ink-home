package dev.inkhome;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Journal widget IDs across binding, draft changes and process death. */
final class AndroidWidgetAllocations {
    private static final String KEY = "homeEditorWidgetAllocations";
    private final MainActivity host;
    private final SharedPreferences prefs;

    AndroidWidgetAllocations(MainActivity host) {
        this.host = host;
        prefs = host.getSharedPreferences("inkhome", 0);
    }

    void recover() {
        if (!tracked().isEmpty()) finish(HomeLayout.load(host));
    }

    private Set<String> tracked() {
        return new HashSet<>(prefs.getStringSet(KEY, Collections.emptySet()));
    }

    private Set<String> owned(HomeLayout layout) {
        Set<String> ids = new HashSet<>();
        for (Integer hidden : layout.hiddenWidgetIds) ids.add(hidden.toString());
        for (HomeLayout.Item item : layout.items) {
            int id = AndroidWidgetTile.widgetId(item);
            if (id > 0) ids.add(Integer.toString(id));
        }
        return ids;
    }

    void added(HomeLayout.Item item) {
        int id = AndroidWidgetTile.widgetId(item);
        if (id <= 0) return;
        Set<String> ids = tracked();
        ids.add(Integer.toString(id));
        write(ids);
    }

    void beforeSave(HomeLayout original) {
        Set<String> ids = tracked();
        ids.addAll(owned(original));
        write(ids);
    }

    void prune(HomeLayout original, HomeLayout draft) {
        Set<String> keep = owned(original);
        keep.addAll(owned(draft));
        reconcile(keep, true);
    }

    void finish(HomeLayout layout) {
        reconcile(owned(layout), false);
    }

    private void reconcile(Set<String> keep, boolean stillEditing) {
        Set<String> pending = new HashSet<>();
        for (String id : tracked()) {
            if (keep.contains(id)) {
                if (stillEditing) pending.add(id);
            } else {
                try {
                    host.homeWidgets.remove(Integer.parseInt(id));
                } catch (RuntimeException e) {
                    Log.w("HomeEditor", "Could not release widget", e);
                    pending.add(id);
                }
            }
        }
        write(pending);
    }

    private void write(Set<String> ids) {
        prefs.edit().putStringSet(KEY, ids).apply();
    }
}

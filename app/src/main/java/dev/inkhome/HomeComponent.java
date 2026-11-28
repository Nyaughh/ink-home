package dev.inkhome;

import android.view.View;
import java.util.Collections;
import java.util.Map;

/** A registered widget owns its appearance, size rules, settings and external resources. */
abstract class HomeComponent {
    abstract String id();
    abstract String name(MainActivity host);
    abstract String[] variants();
    abstract boolean supports(int width, int height);
    abstract View render(MainActivity host, HomeLayout.Item item, boolean preview);

    Map<String, String[]> options() { return Collections.emptyMap(); }
    String optionName(MainActivity host, String key) { return key; }
    java.util.List<HomeLayout.Item> restorable(MainActivity host, HomeLayout layout) {
        return Collections.emptyList();
    }
    String restoredName(MainActivity host, HomeLayout.Item item) { return name(host); }
    void restored(HomeLayout layout, HomeLayout.Item item) { }
    void configureNew(MainActivity host, HomeLayout.Item item, Runnable ready) { ready.run(); }
    void editorStarted(MainActivity host) { }
    void added(MainActivity host, HomeLayout.Item item) { }
    void beforeSave(MainActivity host, HomeLayout original) { }
    void reset(MainActivity host, HomeLayout original, HomeLayout preset) { }
    void prune(MainActivity host, HomeLayout original, HomeLayout draft) { }
    void finish(MainActivity host, HomeLayout keep) { }
    void release(MainActivity host, HomeLayout.Item item) { }
}

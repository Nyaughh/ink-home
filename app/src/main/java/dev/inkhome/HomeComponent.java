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
    final View render(MainActivity host, HomeLayout.Item item, boolean preview) {
        if (!preview && displaysCovers(host, item)) host.coverPage = true;
        return new HomeTileFrame(host, item, rebuildOnResize(),
                (width, height) -> renderContent(host, item, preview, width, height));
    }

    /** Content bounds exclude the frame's stroke and padding. An unbounded height requests intrinsic size. */
    abstract View renderContent(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height);

    boolean displaysCovers(MainActivity host, HomeLayout.Item item) { return false; }

    /** Hosted Android views reflow themselves in response to the normal View size callback. */
    boolean rebuildOnResize() { return true; }

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

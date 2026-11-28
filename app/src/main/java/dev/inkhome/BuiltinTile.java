package dev.inkhome;

import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

class BuiltinTile extends HomeComponent {
    interface Content { HomeTiles create(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height); }

    final int countTitle;
    final String type;
    final int title, minWidth, minHeight;
    final String[] styles;
    final Content content;

    BuiltinTile(String type, int title, int minWidth, int minHeight, Content content, int countTitle, String... styles) {
        this.countTitle = countTitle;
        this.type = type;
        this.title = title;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.content = content;
        this.styles = styles.length == 0 ? new String[]{"default"} : styles;
    }

    @Override public String id() { return type; }
    @Override public String name(MainActivity host) { return host.getString(title); }
    @Override public String[] variants() { return styles.clone(); }
    @Override public boolean supports(int w, int h) {
        return w >= minWidth && h >= minHeight && (!squareFace() || w == h);
    }

    @Override public View renderContent(MainActivity host, HomeLayout.Item item, boolean preview, int width, int height) {
        return content.create(host, item, preview, width, height).content();
    }

    @Override public String optionName(MainActivity host, String key) {
        if ("compact".equals(key)) return host.getString(R.string.home_settings_compact);
        if ("borders".equals(key)) return host.getString(R.string.home_settings_borders);
        if ("count".equals(key) && countTitle != 0) return host.getString(countTitle);
        return super.optionName(host, key);
    }
    @Override public Map<String, String[]> options() {
        Map<String, String[]> opts = new LinkedHashMap<>();
        opts.put("borders", new String[]{"default", "false", "true"});
        opts.put("compact", new String[]{"false", "true"});
        if (countTitle != 0) {
            String[] counts = new String[13];
            for (int i = 0; i < counts.length; i++) counts[i] = Integer.toString(i);
            opts.put("count", counts);
        }
        return opts;
    }
}

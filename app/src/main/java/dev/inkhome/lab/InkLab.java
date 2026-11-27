package dev.inkhome.lab;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkhome.SheetGallery;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.epd.Epd;
import dev.inkhome.folio.widget.FolioSwitch;
import dev.inkhome.folio.widget.LevelBar;
import dev.inkhome.folio.widget.Segmented;
import dev.inkhome.folio.widget.Stepper;
import dev.inkhome.folio.core.Tokens;
import dev.inkhome.folio.core.Tokens.Type;

/** Interactive, view-local panel diagnostics. Never changes the global update mode. */
public final class InkLab {
    private final Activity host;
    private final Folio folio;
    private final float uiScale;
    private final float textScale;
    private final Typeface sans;
    private final Typeface serif;
    private final LinearLayout content;
    private final Pattern pattern;
    private final TextView result;
    private boolean compareWindow = true;

    public static void open(Activity host, float uiScale, float textScale, Bitmap cover) {
        new InkLab(host, uiScale, textScale, cover).show();
    }

    private InkLab(Activity host, float uiScale, float textScale, Bitmap cover) {
        this.host = host;
        folio = new Folio(host, textScale, uiScale);
        this.uiScale = uiScale;
        this.textScale = textScale;
        sans = font("FolioSans.ttf", Typeface.SANS_SERIF);
        serif = font("Literata.ttf", Typeface.SERIF);
        content = column();
        pattern = new Pattern(cover);
        result = label("Change the specimen, then compare the refreshes.", 13, 500);
        result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    private Typeface font(String asset, Typeface fallback) {
        try { return Typeface.createFromAsset(host.getAssets(), "fonts/" + asset); }
        catch (RuntimeException e) { return fallback; }
    }

    private int u(float size) {
        return Math.round(host.getResources().getDisplayMetrics().density * uiScale * size);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(host);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(folio.palette.paper);
        return layout;
    }

    private TextView label(String text, float size, int weight) {
        TextView view = new TextView(host);
        view.setText(text);
        view.setTextColor(folio.palette.ink);
        view.setTypeface(Typeface.create(sans, weight, false));
        view.setTextSize(size * uiScale * textScale);
        return view;
    }

    private void show() {
        Dialog dialog = new Dialog(host, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        LinearLayout page = column();
        page.setPadding(u(12), u(8), u(12), u(8));
        LinearLayout header = new LinearLayout(host);
        header.addView(label("Ink lab", 23, 700), new LinearLayout.LayoutParams(0, -2, 1));
        Button close = new Button(host);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close);
        page.addView(header);
        ScrollView scroll = new ScrollView(host);
        page.addView(new Segmented(folio, "Lab view", new String[]{"Components", "Refresh tests"}, 0,
                index -> {
                    content.removeAllViews();
                    boolean diagnostics = index == 1;
                    pattern.setVisibility(diagnostics ? View.VISIBLE : View.GONE);
                    result.setVisibility(diagnostics ? View.VISIBLE : View.GONE);
                    if (diagnostics) {
                        addRefreshComparison();
                        addModes();
                        addProbes();
                        addSpecimens();
                    } else {
                        addGallery(uiScale);
                    }
                    scroll.scrollTo(0, 0);
                }));
        // Keep the specimen visible while scrolling through the waveform buttons.
        page.addView(pattern, -1, Math.min(u(132),
                host.getWindowManager().getCurrentWindowMetrics().getBounds().height() / 3));
        page.addView(result);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        pattern.setVisibility(View.GONE);
        result.setVisibility(View.GONE);
        addGallery(uiScale);
        dialog.setContentView(page);
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(-1, -1);
        dialog.show();
    }

    private void addGallery(float scale) {
        Folio sample = new Folio(host, textScale, scale);
        content.addView(label("Preview size", 16, 600));
        float[] sizes = {1f, 1.15f, 1.3f, 1.45f};
        int current = 0;
        for (int i = 1; i < sizes.length; i++)
            if (Math.abs(scale - sizes[i]) < Math.abs(scale - sizes[current])) current = i;
        content.addView(new Segmented(folio, "Preview size", new String[]{"100%", "115%", "130%", "145%"},
                current, i -> {
                    content.removeAllViews();
                    addGallery(sizes[i]);
                }));
        content.addView(label("Switch", 16, 600));
        LinearLayout switches = new LinearLayout(host);
        switches.addView(new FolioSwitch(sample, "Book covers", false, checked -> {}));
        switches.addView(new FolioSwitch(sample, "App icons", true, checked -> {}));
        content.addView(switches);
        content.addView(label("Segmented", 16, 600));
        content.addView(new Segmented(sample, "Clock", new String[]{"Auto", "12-hour", "24-hour"}, 0, i -> {}));
        content.addView(label("Stepper", 16, 600));
        content.addView(new Stepper(sample, "Full refresh", new String[]{"Off", "1 page", "4 pages"}, 1, i -> {}));
        TextView level = label("Level bar · 100%", 16, 600);
        content.addView(level);
        String[] levels = {"90%", "100%", "115%", "130%"};
        content.addView(new LevelBar(sample, "Text size", levels, 1, i -> level.setText("Level bar · " + levels[i])));
        StructureGallery.add(content, sample);
        DataGallery.add(content, sample);
        SheetGallery.add(content, sample);
    }

    private void addRefreshComparison() {
        content.addView(label("Compare refreshes", 20, 700));
        content.addView(label("Change the specimen above to leave a visible edge. Compare any remaining ink inside and outside the strip. Firmware controls how far each request reaches.", 13, 500));
        comparisonButton("Change specimen", () -> {
            pattern.inverted = !pattern.inverted;
            pattern.invalidate();
            result.setText("Specimen changed · compare the remaining ink");
        });
        content.addView(new Segmented(folio, "Request area", new String[]{"Lab window", "Specimen strip"},
                compareWindow ? 0 : 1, index -> compareWindow = index == 0));
        comparisonButton("Gentle · GU 2", () -> report("GU", Epd.requestGentleRefresh(comparisonTarget())));
        comparisonButton("Text · Regal 6", () -> report("Regal", Epd.requestRegalRefresh(comparisonTarget())));
        comparisonButton("Strong · GC 98 (flashes)", () -> report("GC", Epd.requestFullClean(comparisonTarget())));
        content.addView(label("Specimen redraw modes", 20, 700));
    }

    private void comparisonButton(String title, Runnable action) {
        Button button = new Button(host);
        button.setAllCaps(false);
        button.setText(title);
        button.setOnClickListener(v -> action.run());
        content.addView(button);
    }

    private View comparisonTarget() {
        return compareWindow ? pattern.getRootView() : pattern;
    }

    private void report(String mode, boolean returned) {
        String target = compareWindow ? "lab window" : "specimen strip";
        result.setText(mode + " · " + target + (returned ? " · refresh requested" : " · request unavailable"));
    }

    private void addModes() {
        String[] names = {"Default", "DU", "GU", "GC4", "A2 performance", "Regal", "GC", "GCC",
                "Deep GC", "DU quality", "A2 quality", "X DU", "X A2", "Mono A2"};
        int[] codes = {5, 1, 2, 3, 4, 6, 98, 107, 108, 2305, 2308, 16777217, 16777220, 33554436};
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 2 == 0) { row = new LinearLayout(host); content.addView(row); }
            int code = codes[i];
            String title = names[i] + " (" + code + ")";
            Button button = new Button(host);
            button.setAllCaps(false);
            button.setText(title);
            button.setOnClickListener(v -> update(code, title));
            row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void update(int mode, String title) {
        pattern.inverted = !pattern.inverted;
        try {
            View.class.getMethod("invalidate", int.class).invoke(pattern, mode);
            result.setText(title + " · invalidate call returned");
        } catch (ReflectiveOperationException | RuntimeException e) {
            pattern.invalidate();
            result.setText(title + " · ordinary redraw (" + e.getClass().getSimpleName() + ")");
        }
    }

    private String probe(String method) {
        try { return String.valueOf(View.class.getMethod(method).invoke(null)); }
        catch (ReflectiveOperationException | RuntimeException e) { return "unavailable"; }
    }

    private boolean onyx() {
        try { View.class.getMethod("refreshScreen", int.class); return true; }
        catch (NoSuchMethodException | SecurityException e) { return false; }
    }

    private void addProbes() {
        Rect bounds = host.getWindowManager().getCurrentWindowMetrics().getBounds();
        float width = bounds.width() / (host.getResources().getDisplayMetrics().density * uiScale);
        String size = width < Tokens.Window.MEDIUM_MIN ? "Compact" : width < Tokens.Window.EXPANDED_MIN ? "Medium" : "Expanded";
        content.addView(label("Device probes", 20, 700));
        String info = Build.MANUFACTURER + " " + Build.MODEL
                + "\nDisplay estimate · " + (onyx() ? "Mono e-ink (Onyx API)" : "Emissive (no Onyx API)")
                + "\nOnyx panel · " + probe("getEpdWidth") + " × " + probe("getEpdHeight")
                + "\nWindow · " + bounds.width() + " × " + bounds.height() + " px · " + size
                + "\nRegal · " + probe("supportRegal");
        int[] keys = {KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN};
        for (int key : keys) info += "\n" + KeyEvent.keyCodeToString(key) + " · " + KeyCharacterMap.deviceHasKey(key);
        content.addView(label(info, 13, 500));
    }

    private void addSpecimens() {
        content.addView(label("Type specimens", 20, 700));
        specimen("Display", Type.DISPLAY_SIZE, Type.DISPLAY_WEIGHT_EINK, sans);
        specimen("Figure", Type.FIGURE_SIZE, Type.FIGURE_WEIGHT_EINK, sans);
        specimen("Title large", Type.TITLE_LG_SIZE, Type.TITLE_LG_WEIGHT_EINK, sans);
        specimen("Title", Type.TITLE_SIZE, Type.TITLE_WEIGHT_EINK, sans);
        specimen("Title expanded", Type.TITLE_EXPANDED_SIZE, Type.TITLE_EXPANDED_WEIGHT_EINK, sans);
        specimen("Title medium", Type.TITLE_MD_SIZE, Type.TITLE_MD_WEIGHT_EINK, sans);
        specimen("Title small", Type.TITLE_SM_SIZE, Type.TITLE_SM_WEIGHT_EINK, sans);
        specimen("Byline", Type.BYLINE_SIZE, Type.BYLINE_WEIGHT_EINK, sans);
        specimen("Body large", Type.BODY_LG_SIZE, Type.BODY_LG_WEIGHT_EINK, sans);
        specimen("Body", Type.BODY_SIZE, Type.BODY_WEIGHT_EINK, sans);
        specimen("Meta", Type.META_SIZE, Type.META_WEIGHT_EINK, sans);
        specimen("Label", Type.LABEL_SIZE, Type.LABEL_WEIGHT_EINK, sans);
        specimen("Reading", Type.READING_SIZE, Type.READING_WEIGHT, serif);
        int hair = Math.max(Tokens.Stroke.HAIRLINE_MIN, (int) (Tokens.Stroke.HAIRLINE_DENSITY
                * host.getResources().getDisplayMetrics().density));
        int[] heights = {hair, Tokens.Stroke.RULE_MULTIPLIER * hair, u(Tokens.Stroke.BAR)};
        String[] strokes = {"Hairline", "Rule", "Bar"};
        for (int i = 0; i < heights.length; i++) {
            content.addView(label(strokes[i] + " · " + heights[i] + " px", 13, 500));
            View stroke = new View(host);
            stroke.setBackgroundColor(Color.BLACK);
            content.addView(stroke, -1, heights[i]);
        }
    }

    private void specimen(String name, float size, int weight, Typeface family) {
        TextView sample = label(name + " · 012345", size, weight);
        sample.setTypeface(Typeface.create(family, weight, false));
        content.addView(sample);
    }

    private final class Pattern extends View {
        private final Bitmap cover;
        private final Paint paint = new Paint();
        private final int[] bars = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA};
        private boolean inverted;

        Pattern(Bitmap cover) {
            super(host);
            this.cover = cover;
            setContentDescription("Refresh specimen with a book cover, reading text, sixteen greys, colour bars and line grids");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            int foreground = inverted ? Color.WHITE : Color.BLACK;
            canvas.drawColor(inverted ? Color.BLACK : Color.WHITE);
            int left = h * 7 / 10;
            paint.setColor(foreground);
            paint.setAntiAlias(true);
            if (cover != null) {
                paint.setFilterBitmap(true);
                canvas.drawBitmap(cover, null, new Rect(0, 0, left, h), paint);
            } else {
                canvas.drawRect(0, 0, left, h, paint);
                paint.setColor(inverted ? Color.BLACK : Color.WHITE);
                paint.setTypeface(serif);
                paint.setTextSize(Math.max(1, left / 5));
                canvas.drawText("Field", 5, h / 3f, paint);
                canvas.drawText("notes", 5, h / 2f, paint);
            }
            left += u(4);
            paint.setColor(foreground);
            paint.setTypeface(sans);
            paint.setTextSize(h * .13f);
            canvas.drawText("Quiet pages", left, h * .15f, paint);
            canvas.drawText("on a winter morning.", left, h * .32f, paint);
            paint.setAntiAlias(false);
            for (int i = 0; i < 16; i++) {
                int grey = (inverted ? 15 - i : i) * 17;
                paint.setColor(Color.rgb(grey, grey, grey));
                canvas.drawRect(left + (w-left)*i/16, h*2/5, left + (w-left)*(i+1)/16, h*3/5, paint);
            }
            for (int i = 0; i < bars.length; i++) {
                paint.setColor(bars[inverted ? bars.length-i-1 : i]);
                canvas.drawRect(left + (w-left)*i/6, h*3/5, left + (w-left)*(i+1)/6, h*4/5, paint);
            }
            paint.setColor(foreground);
            int middle = (left + w) / 2;
            for (int x = left; x < middle; x += 4) canvas.drawRect(x, h*4/5, x+1, h, paint);
            for (int x = middle; x < w; x += 8) canvas.drawRect(x, h*4/5, x+2, h, paint);
            for (int y = h*4/5; y < h; y += 4) canvas.drawRect(left, y, middle, y+1, paint);
            for (int y = h*4/5; y < h; y += 8) canvas.drawRect(middle, y, w, y+2, paint);
        }
    }
}

package dev.inkhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import dev.inkhome.reader.ChapterPages;
import dev.inkhome.reader.ContentsSheet;
import dev.inkhome.folio.Type;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.epd.RefreshController;
import dev.inkhome.folio.widget.Stepper;
import dev.inkhome.folio.widget.Segmented;
import dev.inkhome.folio.core.RefreshBudget.Change;
import dev.inkhome.reader.TextSheet;
import dev.inkhome.folio.widget.Progress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ink Home's built-in reader for EPUB and TXT. Pages are laid out natively (no WebView) and
 * drawn straight to the e-ink screen: tap or swipe to turn, page keys work, a full refresh
 * clears ghosting every few pages, and your place survives font changes.
 */
public class ReaderActivity extends Activity {

    static final String EXTRA_PATH = "path";

    private Store store;
    private Ink ink;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();

    private File file;
    private Epub book;
    private PageView pageView;
    private FrameLayout root;
    private SheetHost sheets;
    private View menu;
    private TextSheet textSheet;
    private boolean moreSettings;
    private RefreshController refreshController;

    // position
    private int chapter = 0;
    private int page = 0;
    /** Character offset to land on once the chapter is laid out (-1 = last page). */
    private int pendingOffset = 0;

    // current chapter layout
    private StaticLayout layout;
    private final List<Integer> pageStarts = new ArrayList<>();
    private boolean loading = false;
    private int laidOutChapter = -1;
    private volatile int layoutGeneration;

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        ActivityWindow.applyOrientation(this, store);
        ink = new Ink(this, store.textScale(), store.uiScale());
        getWindow().getDecorView();
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        refreshController = new RefreshController((android.view.ViewGroup) getWindow().getDecorView(), store::readerRefreshEvery);

        root = new FrameLayout(this);
        ActivityWindow.fitContent(this, root);
        root.setBackgroundColor(ink.PAPER);
        pageView = new PageView(this);
        root.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        sheets = new SheetHost(this, root, null, () -> ink);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) { finish(); return; }
        file = new File(path);
        int[] pos = store.readerPosition(path);
        chapter = pos[0];
        pendingOffset = pos[1];
        if (b != null) {
            chapter = b.getInt("chapter", chapter);
            pendingOffset = b.getInt("offset", pendingOffset);
        }
        loading = true;
        pageView.invalidate();
        work.execute(() -> {
            try {
                Epub e = Epub.open(file);
                ui.post(() -> {
                    if (isDestroyed() || isFinishing()) {
                        e.close();
                        return;
                    }
                    book = e;
                    chapter = Math.max(0, Math.min(chapter, book.spine.size() - 1));
                    pageView.post(this::loadChapter);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    sheets.notice(getString(R.string.reader_ui_open_error), null, this::finish);
                });
            }
        });
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (ink != null && ink.folio.night != dev.inkhome.folio.DisplayProbe.night(this)) recreate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ActivityWindow.applyOrientation(this, store);
        if (ink.folio.displayClass != dev.inkhome.folio.DisplayProbe.displayClass(this)
                || ink.folio.night != dev.inkhome.folio.DisplayProbe.night(this)
                || ink.folio.accentIndex != dev.inkhome.folio.DisplayProbe.accentIndex(this)) {
            recreate();
            return;
        }
        refreshController.resume();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("chapter", chapter);
        state.putInt("offset", pendingOffset);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshController.pause();
        savePosition();
    }

    @Override
    protected void onDestroy() {
        sheets.close();
        refreshController.close();
        super.onDestroy();
        work.execute(() -> { if (book != null) book.close(); });
        work.shutdown();
    }

    @Override
    public void onBackPressed() {
        if (sheets.dismiss()) return;
        if (moreSettings) { showTextSettings(); return; }
        if (menu != null) { hideMenu(); return; }
        super.onBackPressed();
    }

    /** Page keys turn pages. Boox delivers only the key-up for them, so act on that. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean next = code == KeyEvent.KEYCODE_PAGE_DOWN || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean prev = code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_DPAD_LEFT;
        if (store.pageKeysReversed()) { boolean t = next; next = prev; prev = t; }
        if (next || prev) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
                if (textSheet != null) textSheet.pageScroll(next);
                else if (menu == null) { if (next) nextPage(); else prevPage(); }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ================================================================== layout & paging

    private TextPaint paint() {
        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        p.setColor(ink.TEXT);
        // Links would otherwise use the default link colour, which is transparent here.
        p.linkColor = ink.TEXT;
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, store.readerFontSize(), getResources().getDisplayMetrics()));
        p.setTypeface(readerTypeface(store.readerFont()));
        return p;
    }

    private int marginPx() { return ink.dp(store.readerMargin()); }
    private int headerPx() { return ink.dp(20); }
    private int footerPx() {
        if ("off".equals(store.readerFooter())) return ink.dp(20);
        return ink.dp(16) + (int) Math.ceil(pageView.small.descent() - pageView.small.ascent());
    }
    private int textWidth(float em) {
        return Math.max(1, Math.min(pageView.getWidth() - 2 * marginPx(), Math.round(34 * em)));
    }
    private int textHeight() { return Math.max(1, pageView.getHeight() - headerPx() - footerPx()); }

    /** Lays out the current chapter off the UI thread, then shows the page holding pendingOffset. */
    private void loadChapter() {
        if (isDestroyed() || isFinishing() || book == null || pageView.getWidth() == 0) return;
        final int generation = ++layoutGeneration;
        loading = true;
        pageView.invalidate();
        final TextPaint tp = paint();
        final int ch = chapter, w = textWidth(tp.getTextSize()), h = textHeight();
        final float spacing = store.readerLineSpacing();
        final boolean justify = store.readerJustify();
        work.execute(() -> {
            if (generation != layoutGeneration) return;
            StaticLayout sl;
            List<Integer> starts = new ArrayList<>();
            try {
                Spanned raw = book.chapter(ch, getResources(), w, h);
                sl = ChapterPages.layout(raw, tp, w, h, spacing, justify, starts);
            } catch (Exception e) {
                String message = getString(R.string.reader_ui_chapter_error);
                sl = StaticLayout.Builder.obtain(message, 0, message.length(), tp, w).build();
                starts.clear();
                starts.add(0);
            }
            final StaticLayout result = sl;
            ui.post(() -> {
                if (generation != layoutGeneration || isDestroyed() || isFinishing()) return;
                layout = result;
                laidOutChapter = ch;
                pageStarts.clear();
                pageStarts.addAll(starts);
                if (pendingOffset < 0) page = pageStarts.size() - 1;
                else page = pageForOffset(pendingOffset);
                if (pendingOffset < 0) pendingOffset = offsetOfPage();
                loading = false;
                if (textSheet == null) showPage(true);
                else {
                    pageView.invalidate();
                    savePosition();
                }
            });
        });
    }

    private int pageForOffset(int offset) {
        if (layout == null) return 0;
        int line = layout.getLineForOffset(Math.min(offset, layout.getText().length()));
        int match = java.util.Collections.binarySearch(pageStarts, line);
        return match >= 0 ? match : Math.max(0, -match - 2);
    }

    private int offsetOfPage() {
        if (layout == null || pageStarts.isEmpty()) return 0;
        return layout.getLineStart(pageStarts.get(Math.min(page, pageStarts.size() - 1)));
    }

    private void nextPage() {
        if (loading || book == null) return;
        if (page < pageStarts.size() - 1) { page++; pendingOffset = offsetOfPage(); showPage(false); return; }
        if (chapter < book.spine.size() - 1) { chapter++; pendingOffset = 0; loadChapter(); }
    }

    private void prevPage() {
        if (loading || book == null) return;
        if (page > 0) { page--; pendingOffset = offsetOfPage(); showPage(false); return; }
        if (chapter > 0) { chapter--; pendingOffset = -1; loadChapter(); }
    }

    private void showPage(boolean forceFull) {
        refreshController.changed(pageView, Change.PAGE);
        pageView.invalidate();
        if (forceFull) refreshController.cleanWhenIdle();
        savePosition();
    }

    private float chapterFraction() {
        if (pageStarts.isEmpty()) return 0;
        return pageStarts.size() <= 1 ? 1f : page / (float) (pageStarts.size() - 1);
    }

    private void savePosition() {
        if (loading || book == null || layout == null || file == null) return;
        float p = book.progress(chapter, chapterFraction());
        store.setReaderPosition(file.getAbsolutePath(), chapter, pendingOffset, p);
    }

    // ================================================================== drawing

    /** Just the page: with the configured reading footer. Tap the middle for controls. */
    final class PageView extends View {
        private final TextPaint small = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private float downX, downY;

        PageView(Context c) {
            super(c);
            TypeScale label = TypeScale.LABEL;
            small.setColor(ink.TEXT2);
            small.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    label.size * ink.folio.textScale * ink.folio.uiScale, getResources().getDisplayMetrics()));
            small.setTypeface(Type.sans(c, label.weight(ink.folio.displayClass)));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (book != null && w > 0 && (w != ow || h != oh)) {
                loadChapter();
            }
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(ink.PAPER);
            int w = getWidth();
            if (book == null || layout == null || laidOutChapter != chapter) {
                String s = book == null ? getString(R.string.reader_ui_opening) : "";
                c.drawText(s, (w - small.measureText(s)) / 2f, getHeight() / 2f, small);
                return;
            }
            int first = pageStarts.get(page);
            int last = page + 1 < pageStarts.size() ? pageStarts.get(page + 1) - 1 : layout.getLineCount() - 1;
            int top = layout.getLineTop(first), bottom = layout.getLineBottom(last);
            c.save();
            int left = (w - layout.getWidth()) / 2;
            c.translate(left, headerPx() - top);
            c.clipRect(0, top, layout.getWidth(), Math.min(bottom, top + textHeight()));
            layout.draw(c);
            c.restore();
            String footer = store.readerFooter();
            if (!"off".equals(footer)) drawFooter(c, left, footer);
        }

        private void drawFooter(Canvas canvas, int left, String mode) {
            int width = layout.getWidth();
            float baseline = getHeight() - ink.dp(8) - small.descent();
            String progress = getString(R.string.reader_ui_footer_progress, Math.round(book.progress(chapter, chapterFraction()) * 100), page + 1, pageStarts.size());
            float progressX = Math.max(0, width - small.measureText(progress));
            canvas.save();
            canvas.clipRect(left, getHeight() - footerPx(), left + width, getHeight());
            canvas.drawText(progress, left + progressX, baseline, small);
            if ("chapter_progress".equals(mode)) {
                CharSequence title = TextUtils.ellipsize(chapterName(chapter), small,
                        Math.max(0, progressX - ink.dp(12)), TextUtils.TruncateAt.END);
                canvas.drawText(title.toString(), left, baseline, small);
            }
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { downX = e.getX(); downY = e.getY(); return true; }
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (Math.abs(dx) > ink.dp(48) && Math.abs(dx) > Math.abs(dy)) {
                if (dx < 0) nextPage(); else prevPage();
                return true;
            }
            float x = e.getX(), y = e.getY(), w = getWidth(), h = getHeight();
            boolean center = x > w * 0.3f && x < w * 0.7f && y > h * 0.2f && y < h * 0.8f;
            if (center) showMenu();
            else if (x < w * 0.3f) prevPage();
            else nextPage();
            return true;
        }
    }

    // ================================================================== controls

    private void hideMenu() {
        if (menu == null) return;
        removeControls();
        refreshController.cleanWhenIdle();
    }

    private void removeControls() {
        if (menu != null) root.removeView(menu);
        menu = null;
        textSheet = null;
        moreSettings = false;
    }

    private void showMenu() {
        if (book == null) return;
        hideMenu();
        FrameLayout overlay = new FrameLayout(this);
        overlay.setOnClickListener(v -> hideMenu());

        LinearLayout top = ink.row();
        top.setBackgroundColor(ink.PAPER);
        top.setPadding(ink.dp(10), ink.dp(10), ink.dp(10), ink.dp(10));
        top.setClickable(true);
        ImageView back = barIcon("back", this::finish);
        back.setContentDescription(getString(R.string.reader_ui_close_book));
        top.addView(back);
        TextView title = ink.oneLine(ink.medium(book.title.isEmpty() ? Library.titleFromName(file.getName()) : book.title, 16));
        title.setPadding(ink.dp(8), 0, ink.dp(8), 0);
        top.addView(title, ink.weight(1));
        top.addView(barLabel(getString(R.string.reader_ui_contents), getString(R.string.reader_ui_contents), this::showChapters));
        top.addView(barLabel(getString(R.string.reader_ui_type_sample), getString(R.string.reader_ui_text_settings), this::showTextSettings));
        LinearLayout topWrap = ink.col();
        topWrap.addView(top);
        topWrap.addView(ink.hair());
        overlay.addView(topWrap, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bottom = ink.col();
        bottom.setBackgroundColor(ink.PAPER);
        bottom.setClickable(true);
        bottom.setPadding(ink.dp(10), ink.dp(10), ink.dp(10), ink.dp(16));

        LinearLayout chapters = ink.row();
        ImageView prev = barIcon("back", () -> { if (chapter > 0) { chapter--; pendingOffset = 0; hideMenu(); loadChapter(); } });
        prev.setAlpha(chapter > 0 ? 1f : 0.3f);
        chapters.addView(prev);
        prev.setEnabled(chapter > 0);
        prev.setContentDescription(getString(R.string.reader_ui_previous_chapter));
        LinearLayout description = ink.col();
        TextView name = ink.oneLine(ink.medium(chapterName(chapter), 15));
        name.setGravity(Gravity.CENTER);
        description.addView(name);
        TextView position = ink.medium(getString(R.string.reader_ui_page_in_chapter, page + 1, Math.max(1, pageStarts.size())), 13);
        position.setGravity(Gravity.CENTER);
        description.addView(position);
        chapters.addView(description, ink.weight(1));
        ImageView next = barIcon("chevron", () -> { if (chapter < book.spine.size() - 1) { chapter++; pendingOffset = 0; hideMenu(); loadChapter(); } });
        next.setAlpha(chapter < book.spine.size() - 1 ? 1f : 0.3f);
        next.setEnabled(chapter < book.spine.size() - 1);
        next.setContentDescription(getString(R.string.reader_ui_next_chapter));
        chapters.addView(next);
        bottom.addView(chapters);

        int pct = Math.round(book.progress(chapter, chapterFraction()) * 100);
        Progress progress = new Progress(ink.folio, 5, true, pct);
        bottom.addView(progress, ink.m(-1, -2, 8, 12, 8, 0));

        LinearLayout bottomWrap = ink.col();
        bottomWrap.addView(ink.hair());
        bottomWrap.addView(bottom);
        overlay.addView(bottomWrap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        refreshController.changed(overlay, Change.OVERLAY);
        menu = overlay;
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView barLabel(String label, String description, Runnable click) {
        TextView button = ink.medium(label, 16);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(ink.dp(44));
        button.setMinWidth(ink.dp(44));
        button.setPadding(ink.dp(6), 0, ink.dp(6), 0);
        button.setContentDescription(description);
        button.setBackground(ink.pressable());
        button.setOnClickListener(v -> click.run());
        return button;
    }

    private ImageView barIcon(String icon, Runnable r) {
        ImageView b = ink.icon(icon, 24);
        b.setPadding(ink.dp(10), ink.dp(10), ink.dp(10), ink.dp(10));
        b.setBackground(ink.pressable());
        b.setLayoutParams(new LinearLayout.LayoutParams(ink.dp(44), ink.dp(44)));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /** Contents entry for a chapter, or "Chapter n" when the book has no contents list. */
    private String chapterName(int ch) {
        String t = book.chapterTitle(ch);
        return t.isEmpty() ? getString(R.string.reader_ui_chapter_position, ch + 1, book.spine.size()) : t;
    }

    /** Open the contents on the page containing the current chapter. */
    private void showChapters() {
        final List<Integer> targets = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int current = 0;
        if (!book.toc.isEmpty()) {
            for (Epub.TocEntry t : book.toc) {
                if (t.chapter <= chapter) current = names.size();
                names.add(t.title);
                targets.add(t.chapter);
            }
        } else {
            for (int i = 0; i < book.spine.size(); i++) { names.add(getString(R.string.reader_ui_chapter_number, i + 1)); targets.add(i); }
            current = chapter;
        }
        ContentsSheet contents = new ContentsSheet(ink.folio, names, current, w -> {
            sheets.dismiss();
            chapter = targets.get(w);
            pendingOffset = 0;
            hideMenu();
            loadChapter();
        }, () -> refreshController.changed(root, Change.PAGE));
        sheets.paged(getString(R.string.reader_ui_contents), contents);
    }

    private static final String[] FONT_IDS = {"literata", "source_sans", "system_serif", "system_sans"};
    private static final String[] FOOTER_IDS = {"off", "progress", "chapter_progress"};

    private Typeface readerTypeface(String font) {
        switch (font) {
            case "system_serif": return Typeface.SERIF;
            case "system_sans": return Typeface.SANS_SERIF;
            case "source_sans": return Type.sans(this, 400);
            default: return Type.reading(this, false);
        }
    }

    private TextSheet openTextPanel(String title, boolean more) {
        removeControls();
        moreSettings = more;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setOnClickListener(v -> hideMenu());
        textSheet = new TextSheet(ink.folio, title, root.getHeight() * 3 / 4,
                more ? this::showTextSettings : this::hideMenu);
        int width = Math.min(root.getWidth(), ink.dp(500));
        overlay.addView(textSheet, new FrameLayout.LayoutParams(width, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        menu = overlay;
        root.addView(overlay);
        refreshController.changed(textSheet, Change.OVERLAY);
        return textSheet;
    }

    private void textChanged() {
        refreshController.fast(textSheet);
        relayout();
    }

    private void showTextSettings() {
        TextSheet panel = openTextPanel(getString(R.string.reader_ui_text), false);
        Typeface[] faces = new Typeface[FONT_IDS.length];
        for (int i = 0; i < faces.length; i++) faces[i] = readerTypeface(FONT_IDS[i]);
        panel.fonts(getResources().getStringArray(R.array.reader_ui_fonts), faces,
                java.util.Arrays.asList(FONT_IDS).indexOf(store.readerFont()), i -> {
                    store.setReaderFont(FONT_IDS[i]);
                    textChanged();
                });
        panel.sizes(store.readerFontSize(), size -> {
            store.setReaderFontSize(size);
            textChanged();
        });
        float[] spacing = {1.1f, 1.35f, 1.7f};
        int[] margins = {12, 24, 40};
        // Keep existing custom values until the user chooses a preset.
        int lineChoice = -1, marginChoice = -1;
        for (int i = 0; i < spacing.length; i++) {
            if (Math.abs(spacing[i] - store.readerLineSpacing()) < .001f) lineChoice = i;
            if (margins[i] == store.readerMargin()) marginChoice = i;
        }
        panel.pictured(getString(R.string.reader_ui_line_spacing), false, getResources().getStringArray(R.array.reader_ui_spacing_options), lineChoice, i -> {
            store.setReaderLineSpacing(spacing[i]);
            textChanged();
        });
        panel.pictured(getString(R.string.reader_ui_margins), true, getResources().getStringArray(R.array.reader_ui_margin_options), marginChoice, i -> {
            store.setReaderMargin(margins[i]);
            textChanged();
        });
        panel.heading(getString(R.string.reader_ui_alignment));
        panel.control(new Segmented(ink.folio, getString(R.string.reader_ui_alignment), getResources().getStringArray(R.array.reader_ui_alignment_options),
                store.readerJustify() ? 0 : 1, i -> {
                    store.setReaderJustify(i == 0);
                    textChanged();
                }));
        panel.control(barLabel(getString(R.string.reader_ui_page_footer_refresh_and_more), getString(R.string.reader_ui_page_footer_refresh_and_more), this::showMoreSettings));
    }

    private void showMoreSettings() {
        TextSheet panel = openTextPanel(getString(R.string.reader_ui_reader_settings), true);
        panel.heading(getString(R.string.reader_ui_page_footer));
        panel.control(new Segmented(ink.folio, getString(R.string.reader_ui_page_footer), getResources().getStringArray(R.array.reader_ui_footer_options),
                java.util.Arrays.asList(FOOTER_IDS).indexOf(store.readerFooter()), i -> {
                    store.setReaderFooter(FOOTER_IDS[i]);
                    textChanged();
                }));
        int[] rates = {0, 1, 4, 8, 16};
        String[] labels = new String[rates.length];
        labels[0] = getString(R.string.reader_ui_off);
        for (int i = 1; i < rates.length; i++) {
            labels[i] = getResources().getQuantityString(R.plurals.reader_ui_refresh_pages, rates[i], rates[i]);
        }
        int current = 3;
        for (int i = 0; i < rates.length; i++) if (rates[i] == store.readerRefreshEvery()) current = i;
        panel.heading(getString(R.string.reader_ui_full_refresh));
        panel.control(new Stepper(ink.folio, getString(R.string.reader_ui_full_refresh), labels, current, i -> {
            store.setReaderRefreshEvery(rates[i]);
            refreshController.fast(panel);
        }));
    }

    /** Re-paginates the chapter with new text settings, keeping the same place. */
    private void relayout() {
        loadChapter();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (sheets != null) sheets.touched(event);
        return super.dispatchTouchEvent(event);
    }

    static Intent intent(Context c, File f) {
        return new Intent(c, ReaderActivity.class).putExtra(EXTRA_PATH, f.getAbsolutePath());
    }
}

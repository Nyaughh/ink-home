package dev.inkhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.graphics.text.LineBreaker;
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
import dev.inkhome.folio.epd.RefreshController;
import dev.inkhome.folio.core.RefreshBudget.Change;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private View menu;
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

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        ink = new Ink(this, store.textScale(), store.uiScale());
        getWindow().getDecorView();
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        refreshController = new RefreshController((android.view.ViewGroup) getWindow().getDecorView(), store::readerRefreshEvery);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        pageView = new PageView(this);
        root.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) { finish(); return; }
        file = new File(path);
        int[] pos = store.readerPosition(path);
        chapter = pos[0];
        pendingOffset = pos[1];
        loading = true;
        pageView.invalidate();
        work.execute(() -> {
            try {
                Epub e = Epub.open(file);
                ui.post(() -> {
                    book = e;
                    chapter = Math.max(0, Math.min(chapter, book.spine.size() - 1));
                    pageView.post(this::loadChapter);
                });
            } catch (Exception ex) {
                ui.post(() -> {
                    android.widget.Toast.makeText(this, "Couldn't open this book", android.widget.Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshController.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshController.pause();
        savePosition();
    }

    @Override
    protected void onDestroy() {
        refreshController.close();
        super.onDestroy();
        work.execute(() -> { if (book != null) book.close(); });
        work.shutdown();
    }

    @Override
    public void onBackPressed() {
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
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && menu == null) { if (next) nextPage(); else prevPage(); }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ================================================================== layout & paging

    private TextPaint paint() {
        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        // Links would otherwise use the default link colour, which is transparent here.
        p.linkColor = Color.BLACK;
        p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, store.readerFontSize(), getResources().getDisplayMetrics()));
        p.setTypeface(store.readerSerif() ? Typeface.SERIF : Typeface.SANS_SERIF);
        return p;
    }

    private int marginPx() { return ink.dp(store.readerMargin()); }
    private int headerPx() { return ink.dp(20); }
    private int footerPx() { return ink.dp(20); }
    private int textWidth() { return Math.max(100, pageView.getWidth() - 2 * marginPx()); }
    private int textHeight() { return Math.max(100, pageView.getHeight() - headerPx() - footerPx()); }

    /** Lays out the current chapter off the UI thread, then shows the page holding pendingOffset. */
    private void loadChapter() {
        if (book == null || pageView.getWidth() == 0) return;
        loading = true;
        pageView.invalidate();
        final int ch = chapter, w = textWidth(), h = textHeight();
        final TextPaint tp = paint();
        final float spacing = store.readerLineSpacing();
        final boolean justify = store.readerJustify();
        work.execute(() -> {
            StaticLayout sl;
            List<Integer> starts = new ArrayList<>();
            try {
                Spanned raw = book.chapter(ch, getResources(), w, h);
                android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(raw);
                text.setSpan(new ParagraphGap(Math.round(tp.getTextSize() * 0.55f)), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                StaticLayout.Builder sb = StaticLayout.Builder.obtain(text, 0, text.length(), tp, w)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0, spacing)
                        .setIncludePad(false)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
                if (justify) sb.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
                sl = sb.build();
                // A page is as many whole lines as fit; a line taller than a page gets one to itself.
                int line = 0, n = sl.getLineCount();
                while (line < n) {
                    starts.add(line);
                    int top = sl.getLineTop(line);
                    int next = line + 1;
                    while (next < n && sl.getLineBottom(next) - top <= h) next++;
                    line = next;
                }
                if (starts.isEmpty()) starts.add(0);
            } catch (Exception e) {
                sl = StaticLayout.Builder.obtain("This chapter couldn't be read.", 0, 30, tp, w).build();
                starts.clear();
                starts.add(0);
            }
            final StaticLayout result = sl;
            ui.post(() -> {
                if (ch != chapter) return;
                layout = result;
                pageStarts.clear();
                pageStarts.addAll(starts);
                if (pendingOffset < 0) page = pageStarts.size() - 1;
                else page = pageForOffset(pendingOffset);
                loading = false;
                showPage(true);
            });
        });
    }

    private int pageForOffset(int offset) {
        if (layout == null) return 0;
        int line = layout.getLineForOffset(Math.min(offset, layout.getText().length()));
        int p = 0;
        for (int i = 0; i < pageStarts.size(); i++) if (pageStarts.get(i) <= line) p = i;
        return p;
    }

    private int offsetOfPage() {
        if (layout == null || pageStarts.isEmpty()) return 0;
        return layout.getLineStart(pageStarts.get(Math.min(page, pageStarts.size() - 1)));
    }

    private void nextPage() {
        if (loading || book == null) return;
        if (page < pageStarts.size() - 1) { page++; showPage(false); return; }
        if (chapter < book.spine.size() - 1) { chapter++; pendingOffset = 0; loadChapter(); }
    }

    private void prevPage() {
        if (loading || book == null) return;
        if (page > 0) { page--; showPage(false); return; }
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
        if (book == null || layout == null || file == null) return;
        float p = book.progress(chapter, chapterFraction());
        store.setReaderPosition(file.getAbsolutePath(), chapter, offsetOfPage(), p);
    }

    // ================================================================== drawing

    /** Just the page: no header or footer while reading. Tap the middle for controls. */
    final class PageView extends View {
        private final TextPaint small = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private float downX, downY;

        PageView(Context c) {
            super(c);
            small.setColor(0xFF6A6A6A);
            small.setTextSize(ink.dp(13));
            small.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (book != null && w > 0 && (w != ow || h != oh)) {
                pendingOffset = offsetOfPage();
                loadChapter();
            }
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(Color.WHITE);
            int m = marginPx(), w = getWidth();
            if (book == null || layout == null || loading) {
                String s = book == null ? "Opening…" : "";
                c.drawText(s, (w - small.measureText(s)) / 2f, getHeight() / 2f, small);
                return;
            }
            int first = pageStarts.get(page);
            int last = page + 1 < pageStarts.size() ? pageStarts.get(page + 1) - 1 : layout.getLineCount() - 1;
            int top = layout.getLineTop(first), bottom = layout.getLineBottom(last);
            c.save();
            c.translate(m, headerPx() - top);
            c.clipRect(0, top, textWidth(), bottom);
            layout.draw(c);
            c.restore();
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

    /** Progress bar in pure black and white: outlined track, solid fill. */
    final class BwBar extends View {
        private final float f;
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        BwBar(Context c, float fraction) {
            super(c);
            f = Math.max(0f, Math.min(1f, fraction));
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), ink.dp(10));
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.max(2, ink.dp(1.5f)), r = getHeight() / 2f;
            p.setColor(Color.BLACK);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(s);
            c.drawRoundRect(s / 2, s / 2, getWidth() - s / 2, getHeight() - s / 2, r, r, p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            if (f > 0) c.drawRoundRect(0, 0, Math.max(getHeight(), getWidth() * f), getHeight(), r, r, p);
        }
    }

    // ================================================================== controls

    private void hideMenu() {
        if (menu == null) return;
        root.removeView(menu);
        menu = null;
        refreshController.changed(pageView, Change.OVERLAY);
    }

    /** One tap shows: back and settings at the top; chapter picker, chevrons and progress at the bottom. */
    private void showMenu() {
        if (book == null) return;
        hideMenu();
        FrameLayout overlay = new FrameLayout(this);
        overlay.setOnClickListener(v -> hideMenu());

        LinearLayout top = ink.row();
        top.setBackgroundColor(Color.WHITE);
        top.setPadding(ink.dp(10), ink.dp(10), ink.dp(10), ink.dp(10));
        top.setClickable(true);
        top.addView(barIcon("back", this::finish));
        TextView title = ink.oneLine(ink.medium(book.title.isEmpty() ? Library.titleFromName(file.getName()) : book.title, 16));
        title.setPadding(ink.dp(8), 0, ink.dp(8), 0);
        top.addView(title, ink.weight(1));
        top.addView(barIcon("settings", this::showTextSettings));
        LinearLayout topWrap = ink.col();
        topWrap.addView(top);
        topWrap.addView(ink.hair());
        overlay.addView(topWrap, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout bottom = ink.col();
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setClickable(true);
        bottom.setPadding(ink.dp(10), ink.dp(10), ink.dp(10), ink.dp(16));

        LinearLayout chapters = ink.row();
        ImageView prev = barIcon("back", () -> { if (chapter > 0) { chapter--; pendingOffset = 0; hideMenu(); loadChapter(); } });
        prev.setAlpha(chapter > 0 ? 1f : 0.3f);
        chapters.addView(prev);
        // The chapter name, centred, opens the chapter list on the current chapter.
        LinearLayout picker = ink.row();
        picker.setGravity(Gravity.CENTER);
        picker.setPadding(ink.dp(10), ink.dp(8), ink.dp(10), ink.dp(8));
        picker.setBackground(ink.card(8, true));
        TextView name = ink.oneLine(ink.medium(chapterName(chapter), 15));
        name.setGravity(Gravity.CENTER);
        picker.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        picker.addView(ink.icon("down", 16), ink.m(ink.dp(16), ink.dp(16), 6, 0, 0, 0));
        picker.setOnClickListener(v -> showChapters());
        chapters.addView(picker, ink.m(0, -2, 8, 0, 8, 0));
        ((LinearLayout.LayoutParams) picker.getLayoutParams()).weight = 1;
        ImageView next = barIcon("chevron", () -> { if (chapter < book.spine.size() - 1) { chapter++; pendingOffset = 0; hideMenu(); loadChapter(); } });
        next.setAlpha(chapter < book.spine.size() - 1 ? 1f : 0.3f);
        chapters.addView(next);
        bottom.addView(chapters);

        int pct = Math.round(book.progress(chapter, chapterFraction()) * 100);
        LinearLayout prog = ink.row();
        prog.setPadding(ink.dp(8), ink.dp(16), ink.dp(8), 0);
        prog.addView(new BwBar(this, pct / 100f), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView pc = ink.medium(pct + "%", 15);
        pc.setPadding(ink.dp(12), 0, 0, 0);
        prog.addView(pc);
        bottom.addView(prog);

        LinearLayout bottomWrap = ink.col();
        bottomWrap.addView(ink.hair());
        bottomWrap.addView(bottom);
        overlay.addView(bottomWrap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        refreshController.changed(overlay, Change.OVERLAY);
        menu = overlay;
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
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
        return t.isEmpty() ? "Chapter " + (ch + 1) + " of " + book.spine.size() : t;
    }

    /** The chapter list, opened scrolled to the current chapter with it selected. */
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
            for (int i = 0; i < book.spine.size(); i++) { names.add("Chapter " + (i + 1)); targets.add(i); }
            current = chapter;
        }
        AlertDialog d = new AlertDialog.Builder(this)
                .setSingleChoiceItems(names.toArray(new String[0]), current, (di, w) -> {
                    di.dismiss();
                    chapter = targets.get(w);
                    pendingOffset = 0;
                    hideMenu();
                    loadChapter();
                }).create();
        show(d);
        final int sel = current;
        if (d.getListView() != null) d.getListView().post(() -> d.getListView().setSelection(Math.max(0, sel - 3)));
    }

    /** Text settings: size, spacing, margins, typeface, justification and refresh rate. */
    private void showTextSettings() {
        LinearLayout p = ink.col();
        p.setPadding(ink.dp(20), ink.dp(8), ink.dp(20), ink.dp(4));
        p.addView(stepper("Text size", store.readerFontSize() + "", () -> setFont(-1), () -> setFont(+1)));
        p.addView(stepper("Line spacing", String.format(Locale.ROOT, "%.2f", store.readerLineSpacing()),
                () -> { store.setReaderLineSpacing(Math.max(1.0f, store.readerLineSpacing() - 0.1f)); relayout(); },
                () -> { store.setReaderLineSpacing(Math.min(2.2f, store.readerLineSpacing() + 0.1f)); relayout(); }));
        p.addView(stepper("Margins", store.readerMargin() + "", () -> { store.setReaderMargin(Math.max(8, store.readerMargin() - 4)); relayout(); },
                () -> { store.setReaderMargin(Math.min(64, store.readerMargin() + 4)); relayout(); }));
        p.addView(ink.segmented(new String[]{"Serif", "Sans"}, store.readerSerif() ? 0 : 1, i -> { store.setReaderSerif(i == 0); relayout(); }), ink.m(-1, -2, 0, 12, 0, 0));
        p.addView(ink.segmented(new String[]{"Justified", "Ragged"}, store.readerJustify() ? 0 : 1, i -> { store.setReaderJustify(i == 0); relayout(); }), ink.m(-1, -2, 0, 10, 0, 0));
        int[] rates = {1, 4, 8, 16, 0};
        String[] labels = {"Every page", "Every 4", "Every 8", "Every 16", "Never"};
        int cur = 2;
        for (int i = 0; i < rates.length; i++) if (rates[i] == store.readerRefreshEvery()) cur = i;
        p.addView(ink.heading("Full refresh"), ink.m(-1, -2, 0, 16, 0, 0));
        p.addView(ink.segmented(labels, cur, i -> store.setReaderRefreshEvery(rates[i])));
        show(new AlertDialog.Builder(this).setTitle("Text").setView(p).setPositiveButton("Done", null).create());
    }

    private void setFont(int delta) {
        store.setReaderFontSize(Math.max(12, Math.min(40, store.readerFontSize() + delta)));
        relayout();
    }

    private View stepper(String label, String value, Runnable minus, Runnable plus) {
        LinearLayout r = ink.row();
        r.setPadding(0, ink.dp(8), 0, ink.dp(8));
        r.addView(ink.body(label), ink.weight(1));
        TextView v = ink.medium(value, 16);
        ImageView m = ink.iconButton("back", () -> { minus.run(); refreshDialogValue(v, label); });
        ImageView pl = ink.iconButton("chevron", () -> { plus.run(); refreshDialogValue(v, label); });
        r.addView(m);
        v.setGravity(Gravity.CENTER);
        r.addView(v, new LinearLayout.LayoutParams(ink.dp(64), -2));
        r.addView(pl);
        return r;
    }

    private void refreshDialogValue(TextView v, String label) {
        refreshController.fast(v);
        if (label.equals("Text size")) v.setText(String.valueOf(store.readerFontSize()));
        else if (label.equals("Line spacing")) v.setText(String.format(Locale.ROOT, "%.2f", store.readerLineSpacing()));
        else v.setText(String.valueOf(store.readerMargin()));
    }

    /** Re-paginates the chapter with new text settings, keeping the same place. */
    private void relayout() {
        pendingOffset = offsetOfPage();
        loadChapter();
    }

    private void show(AlertDialog d) {
        if (d.getWindow() != null) {
            d.getWindow().setWindowAnimations(0);
            d.getWindow().setDimAmount(0f);
        }
        d.show();
    }

    /** Adds a little space after each paragraph instead of a whole blank line. */
    static final class ParagraphGap implements android.text.style.LineHeightSpan {
        private final int gap;
        ParagraphGap(int gapPx) { gap = gapPx; }

        @Override
        public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, android.graphics.Paint.FontMetricsInt fm) {
            if (end > 0 && end <= text.length() && text.charAt(end - 1) == '\n') {
                fm.descent += gap;
                fm.bottom += gap;
            }
        }
    }

    static Intent intent(Context c, File f) {
        return new Intent(c, ReaderActivity.class).putExtra(EXTRA_PATH, f.getAbsolutePath());
    }
}

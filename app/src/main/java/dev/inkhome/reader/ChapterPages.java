package dev.inkhome.reader;

import android.graphics.text.LineBreaker;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.List;

/** Lays out only the requested chapter and records its whole-line page boundaries. */
public final class ChapterPages {
    private ChapterPages() { }

    public static StaticLayout layout(Spanned raw, TextPaint paint, int width, int height,
                                      float spacing, boolean justify, List<Integer> starts) {
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder(raw);
        text.setSpan(new ParagraphGap(Math.round(paint.getTextSize() * 0.55f)), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        StaticLayout.Builder sb = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, spacing)
                .setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
        if (justify) sb.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
        StaticLayout sl = sb.build();
        // A page is as many whole lines as fit; a line taller than a page gets one to itself.
        int line = 0, n = sl.getLineCount();
        while (line < n) {
            starts.add(line);
            int top = sl.getLineTop(line);
            int next = line + 1;
            while (next < n && sl.getLineBottom(next) - top <= height) next++;
            line = next;
        }
        if (starts.isEmpty()) starts.add(0);
        return sl;
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

}

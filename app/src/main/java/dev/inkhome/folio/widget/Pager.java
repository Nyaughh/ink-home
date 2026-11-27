package dev.inkhome.folio.widget;

import dev.inkhome.R;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.Tokens;
import dev.inkhome.folio.core.TypeScale;

/** Presents a zero-based controller's current page as a one-based folio. */
public final class Pager extends LinearLayout {
    private final TextView count;
    private final View back, forward;

    public Pager(Folio f, boolean inline, Runnable previous, Runnable next) {
        super(f.context);
        setGravity(Gravity.CENTER_VERTICAL);
        int height = inline ? Tokens.Bar.SECTION : Tokens.Bar.PAGER;
        Structure structure = new Structure(f);
        back = structure.icon("back", f.context.getString(R.string.reader_ui_previous_page), previous, height);
        forward = structure.icon("forward", f.context.getString(R.string.reader_ui_next_page), next, height);
        count = structure.text("", TypeScale.META);
        count.setGravity(Gravity.CENTER);
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(back);
        if (inline) addView(count);
        else structure.flex(this, count);
        addView(forward);
        bind(0, 0);
    }

    public void bind(int pageIndex, int pageCount) {
        int index = Math.max(0, Math.min(pageCount - 1, pageIndex));
        count.setText(getContext().getString(R.string.reader_ui_page_count, index + 1, pageCount));
        setVisibility(pageCount > 1 ? VISIBLE : GONE);
        back.setVisibility(index > 0 ? VISIBLE : INVISIBLE);
        forward.setVisibility(index + 1 < pageCount ? VISIBLE : INVISIBLE);
    }
}

package dev.inkhome;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

final class SearchScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        LinearLayout head = host.ink.row();
        int margin = host.ink.dp(host.ink.folio.windowClass().margin);
        head.setPadding(margin, host.ink.dp(8), margin, host.ink.dp(8));
        EditText q = new EditText(host);
        q.setText(host.libSearch);
        q.setHint(host.getString(R.string.library_ui_search_hint));
        q.setTypeface(host.ink.REGULAR);
        q.setTextColor(host.ink.TEXT);
        q.setHintTextColor(host.ink.TEXT3);
        q.setTextSize(17 * host.ink.scale);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setBackground(host.ink.stroke(host.ink.PAPER, 0, host.ink.LINE));
        q.setPadding(host.ink.dp(14), host.ink.dp(10), host.ink.dp(14), host.ink.dp(10));
        head.addView(q, host.ink.weight(1));
        TextView cancel = host.ink.medium(host.getString(R.string.library_ui_cancel), 15);
        cancel.setPadding(host.ink.dp(16), host.ink.dp(10), 0, host.ink.dp(10));
        cancel.setOnClickListener(v -> { host.libSearch = null; host.hideKeyboard(); host.render(); });
        head.addView(cancel);
        col.addView(head);
        LinearLayout results = host.ink.col();
        results.setPadding(margin, 0, margin, 0);
        col.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));
        Runnable refresh = () -> {
            results.removeAllViews();
            results.addView(host.ink.hair());
            List<Library.Book> list = host.filterBooks(true);
            if (list.isEmpty() && !host.libSearch.trim().isEmpty()) {
                TextView t = host.ink.muted(host.getString(R.string.library_ui_no_matches), 15);
                t.setPadding(0, host.ink.dp(16), 0, 0);
                results.addView(t);
            } else {
                results.addView(host.bookPages(list, false), new LinearLayout.LayoutParams(-1, 0, 1f));
            }
        };
        Runnable filterAfterTyping = () -> {
            if (q.isAttachedToWindow() && host.libSearch != null) refresh.run();
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                host.refreshController.fast(q);
                host.libSearch = s.toString();
                host.pages.put("lib", 0);
                host.ui.removeCallbacks(filterAfterTyping);
                host.ui.postDelayed(filterAfterTyping, 250);
            }
        });
        refresh.run();
        q.requestFocus();
        q.setSelection(q.getText().length());
        host.ui.postDelayed(() -> {
            InputMethodManager imm = host.getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(q, 0);
        }, 150);
    }
}

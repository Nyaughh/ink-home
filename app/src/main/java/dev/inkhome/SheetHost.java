package dev.inkhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.inkhome.folio.widget.Button;
import dev.inkhome.folio.paging.PagedList;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.core.WindowClass;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** A paper sheet owns input while open. Notices live in the activity above its dock. */
final class SheetHost {
    private final Activity activity;
    private final FrameLayout root;
    private final View dock;
    private final Supplier<Ink> style;
    private final Store settings;
    private Dialog sheet;
    private View strip;
    private Runnable commit;
    private final Runnable expire = () -> clearNotice(true);

    SheetHost(Activity activity, FrameLayout root, View dock, Supplier<Ink> style) {
        this.activity = activity;
        this.root = root;
        this.dock = dock;
        this.style = style;
        settings = new Store(activity);
    }

    boolean dismiss() {
        if (sheet == null) return false;
        sheet.dismiss();
        sheet = null;
        return true;
    }

    void choose(String title, String[] labels, int checked, IntConsumer select) {
        Ink ink = style.get();
        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(ink.LINE));
        list.setDividerHeight(ink.folio.hairline);
        list.setSelector(ink.pressable());
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return labels.length; }
            public String getItem(int i) { return labels[i]; }
            public long getItemId(int i) { return i; }
            public View getView(int i, View recycled, ViewGroup parent) {
                TextView row = recycled == null ? ink.body("") : (TextView) recycled;
                row.setText(checked < 0 ? labels[i] : activity.getString(
                        i == checked ? R.string.service_ui_choice_selected : R.string.service_ui_choice_unselected, labels[i]));
                row.setSelected(i == checked);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinHeight(ink.dp(48));
                row.setPadding(ink.dp(16), ink.dp(8), ink.dp(16), ink.dp(8));
                return row;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> { dismiss(); select.accept(pos); });
        present(title, list, null, null, null);
        list.setSelection(Math.max(0, checked - 2));
    }

    void prompt(String title, String initial, Consumer<String> save) {
        Ink ink = style.get();
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setTypeface(ink.REGULAR);
        field.setTextColor(ink.TEXT);
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ink.LINE));
        field.setText(initial);
        field.setSelection(field.length());
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Runnable submit = () -> {
            String value = field.getText().toString().trim();
            if (value.isEmpty()) { field.setError(activity.getString(R.string.service_ui_enter_a_name)); return; }
            dismiss();
            save.accept(value);
        };
        field.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
        present(title, padded(field), activity.getString(R.string.service_ui_save), submit, field);
    }

    void message(String title, String message, String action, Runnable run) {
        Ink ink = style.get();
        present(title, ink.scroller(padded(ink.body(message))), action, () -> {
            dismiss();
            if (run != null) run.run();
        }, null);
    }

    void accessStep(String title, String message, Runnable proceed, Runnable skip) {
        Ink ink = style.get();
        LinearLayout body = padded(ink.body(message));
        body.addView(Button.create(ink.folio, activity.getString(R.string.service_ui_continue), Button.Style.PRIMARY, v -> {
            dismiss();
            proceed.run();
        }), ink.m(-1, -2, 0, 12, 0, 4));
        body.addView(Button.create(ink.folio, activity.getString(R.string.service_ui_skip), Button.Style.SECONDARY, v -> {
            dismiss();
            skip.run();
        }));
        body.addView(Button.create(ink.folio, activity.getString(R.string.service_ui_finish_later), Button.Style.TEXT, v -> dismiss()));
        present(title, ink.scroller(body), null, null, null);
    }

    void captureKey(IntConsumer captured) {
        Ink ink = style.get();
        present(activity.getString(R.string.controls_capture), padded(ink.body(
                activity.getString(R.string.controls_capture_hint))),
                activity.getString(R.string.library_ui_cancel), this::dismiss, null);
        Dialog capture = sheet;
        if (capture == null) return;
        capture.setOnKeyListener((dialog, code, event) -> {
            if (code <= 0) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                dismiss();
                captured.accept(code);
            }
            return true;
        });
    }

    void paged(String title, PagedList contents) {
        present(title, contents, null, null, null);
    }

    void custom(String title, View contents) {
        present(title, style.get().scroller(contents), activity.getString(R.string.service_ui_done), this::dismiss, null);
    }

    private LinearLayout padded(View child) {
        Ink ink = style.get();
        LinearLayout box = ink.col();
        box.setPadding(ink.dp(16), ink.dp(8), ink.dp(16), ink.dp(12));
        box.addView(child);
        return box;
    }

    private void present(String title, View body, String action, Runnable run, EditText input) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        dismiss();
        clearNotice(true);
        Ink ink = style.get();
        Dialog d = new Dialog(activity);
        sheet = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = ink.col();
        GradientDrawable edge = ink.stroke(ink.PAPER, 0, ink.LINE);
        panel.setBackground(edge);
        int rule = ink.folio.rule;
        panel.setPadding(rule, rule, rule, rule);
        LinearLayout header = ink.row();
        header.setPadding(ink.dp(16), ink.dp(4), ink.dp(6), ink.dp(4));
        header.addView(ink.text(title, TypeScale.TITLE_SM, ink.TEXT), ink.weight(1));
        View close = ink.iconButton("close", this::dismiss);
        close.setContentDescription(activity.getString(R.string.service_ui_close));
        header.addView(close);
        panel.addView(header);
        panel.addView(ink.hair());
        panel.addView(body, new LinearLayout.LayoutParams(-1, -2, 1f));
        if (action != null) {
            panel.addView(ink.hair());
            TextView button = ink.text(action, TypeScale.LABEL, ink.TEXT);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(ink.dp(48));
            button.setBackground(ink.pressable());
            button.setOnClickListener(v -> run.run());
            panel.addView(button);
        }
        d.setContentView(panel);
        d.setCanceledOnTouchOutside(true);
        d.setOnDismissListener(dialog -> { if (sheet == d) sheet = null; });
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        w.setWindowAnimations(0);
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        boolean compact = ink.folio.windowClass() == WindowClass.COMPACT;
        w.setGravity(compact ? Gravity.BOTTOM : Gravity.CENTER);
        w.setLayout(compact ? -1 : ink.dp(480), -2);
        if (input != null) {
            input.requestFocus();
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        if (body instanceof ListView || body instanceof ScrollView || body instanceof PagedList) {
            d.setOnKeyListener((dialog, key, event) -> {
                Controls.Action binding = settings.controls().keyAction(key, body instanceof PagedList);
                if (binding == Controls.Action.NONE) return false;
                if (binding != Controls.Action.NEXT_PAGE && binding != Controls.Action.PREV_PAGE) {
                    Runnable handler;
                    if (activity instanceof MainActivity) handler = ((MainActivity) activity).controlHandler(binding);
                    else if (activity instanceof ReaderActivity) handler = () -> ((ReaderActivity) activity).runControl(binding);
                    else return false;
                    if (handler == null) return false;
                    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                        if (binding != Controls.Action.STRONG_CLEAN) dismiss();
                        handler.run();
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                    boolean forward = binding == Controls.Action.NEXT_PAGE;
                    if (body instanceof PagedList) {
                        PagedList list = (PagedList) body;
                        if (forward) list.next(); else list.previous();
                    } else if (body instanceof ListView) {
                        ListView list = (ListView) body;
                        int start = list.getFirstVisiblePosition();
                        int step = Math.max(1, list.getLastVisiblePosition() - start);
                        list.setSelection(Math.min(list.getCount() - 1, Math.max(0, start + (forward ? step : -step))));
                    } else ((ScrollView) body).pageScroll(forward ? View.FOCUS_DOWN : View.FOCUS_UP);
                }
                return true;
            });
        }
        d.show();
        if (settings.subtleMotion()) {
            panel.setAlpha(0);
            panel.animate().alpha(1).setDuration(120).start();
        }
    }

    void notice(String text, Runnable undo, Runnable onExpire) {
        clearNotice(true);
        if (activity.isDestroyed() || activity.isFinishing()) return;
        Ink ink = style.get();
        LinearLayout row = ink.row();
        row.setBackgroundColor(ink.folio.palette.inverse);
        row.setPadding(ink.dp(16), 0, ink.dp(16), 0);
        row.setMinimumHeight(ink.dp(42));
        row.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        row.addView(ink.text(text, TypeScale.BODY, ink.folio.palette.onInverse), ink.weight(1));
        if (undo != null) {
            TextView button = ink.text(activity.getString(R.string.service_ui_undo), TypeScale.LABEL, ink.folio.palette.onInverse);
            button.setPadding(ink.dp(12), 0, 0, 0);
            button.setMinHeight(ink.dp(42));
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(v -> {
                clearNotice(false);
                undo.run();
            });
            row.addView(button);
        }
        row.setOnClickListener(v -> clearNotice(true));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        if (dock != null) p.bottomMargin = dock.getHeight();
        commit = onExpire;
        strip = row;
        root.addView(row, p);
        if (dock != null && dock.getHeight() == 0) row.post(() -> {
            if (strip == row) {
                p.bottomMargin = dock.getHeight();
                row.setLayoutParams(p);
            }
        });
        root.postDelayed(expire, 4000);
    }

    void touched(MotionEvent event) {
        if (strip == null || event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        Rect bounds = new Rect();
        strip.getGlobalVisibleRect(bounds);
        if (!bounds.contains((int) event.getRawX(), (int) event.getRawY())) clearNotice(true);
    }

    private void clearNotice(boolean finish) {
        root.removeCallbacks(expire);
        if (strip != null) root.removeView(strip);
        strip = null;
        Runnable completion = commit;
        commit = null;
        if (finish && completion != null) completion.run();
    }

    void close() {
        dismiss();
        clearNotice(true);
    }
}

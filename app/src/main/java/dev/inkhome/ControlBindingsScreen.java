package dev.inkhome;

import android.text.TextUtils;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.inkhome.Controls.Action;
import dev.inkhome.Controls.Input;
import dev.inkhome.Controls.Layout;

final class ControlBindingsScreen {
    private final MainActivity host;
    private final Controls controls;

    ControlBindingsScreen(MainActivity host) {
        this.host = host;
        controls = host.store.controls();
    }

    void rows(MainActivity.Rows rows) {
        rows.add(text(R.string.controls_layout), label(controls.layout().name()), this::chooseLayout);
        for (Action action : Action.values()) {
            if (action == Action.NONE) continue;
            List<Input> bindings = bindings(action);
            String summary = bindings.isEmpty() ? text(R.string.controls_blank)
                    : TextUtils.join(", ", inputNames(bindings));
            rows.add(label(action.name()), summary, () -> edit(action));
        }
        rows.add(text(R.string.controls_reset), "", () -> host.sheets.message(
                text(R.string.controls_reset), text(R.string.controls_reset_detail),
                text(R.string.controls_reset), () -> { controls.reset(); host.render(); }));
    }

    private void chooseLayout() {
        Layout[] layouts = Layout.values();
        String[] names = new String[layouts.length];
        for (int i = 0; i < names.length; i++) names[i] = label(layouts[i].name());
        host.choose(text(R.string.controls_layout), names, controls.layout().ordinal(), i -> {
            controls.setLayout(layouts[i]);
            host.render();
        });
    }

    private List<Input> bindings(Action action) {
        List<Input> matches = new ArrayList<>();
        for (Input input : controls.inputs()) {
            if (available(input) && controls.action(input) == action) matches.add(input);
        }
        return matches;
    }

    private boolean available(Input input) {
        Layout layout = controls.layout();
        if (input.equals(Input.TAP_TOP) || input.equals(Input.TAP_BOTTOM)) {
            return layout == Layout.ROWS || layout == Layout.CROSS;
        }
        if (input.equals(Input.TAP_LEFT) || input.equals(Input.TAP_RIGHT)) return layout != Layout.ROWS;
        return true;
    }

    private void edit(Action action) {
        List<Input> current = bindings(action);
        List<String> options = inputNames(current);
        options.add(text(R.string.controls_add));
        host.choose(label(action.name()), options.toArray(new String[0]), -1, i -> {
            if (i == current.size()) chooseInput(action, null);
            else editBinding(action, current.get(i));
        });
    }

    private void editBinding(Action action, Input input) {
        host.choose(inputName(input), new String[]{text(R.string.controls_replace),
                text(R.string.controls_remove)}, -1, i -> {
            if (i == 0) chooseInput(action, input);
            else { controls.bind(input, Action.NONE); host.render(); }
        });
    }

    private void chooseInput(Action action, Input old) {
        host.choose(label(action.name()), new String[]{text(R.string.controls_capture),
                text(R.string.controls_gesture)}, -1, i -> {
            if (i == 0) {
                host.sheets.captureKey(code -> assign(action, old, Controls.key(code)));
                return;
            }
            List<Input> gestures = new ArrayList<>();
            for (Input input : controls.inputs()) {
                if (input.keyCode == 0 && available(input)) gestures.add(input);
            }
            host.choose(text(R.string.controls_gesture), inputNames(gestures).toArray(new String[0]),
                    -1, j -> assign(action, old, gestures.get(j)));
        });
    }

    private void assign(Action action, Input old, Input input) {
        Action owner = controls.action(input);
        if (owner == action) return;
        if (owner == Action.NONE) {
            if (old == null) controls.bind(input, action);
            else controls.swap(old, input);
            host.render();
            return;
        }
        String conflict = host.getString(R.string.controls_conflict, inputName(input), label(owner.name()));
        if (old != null) {
            host.sheets.message(conflict, host.getString(R.string.controls_swapped, inputName(old),
                    inputName(input)), host.getString(R.string.controls_swap, inputName(old)), () -> {
                controls.swap(old, input);
                host.render();
            });
            return;
        }
        List<Input> current = bindings(action);
        List<String> options = new ArrayList<>();
        options.add(host.getString(R.string.controls_move, label(action.name())));
        for (Input alternate : current) {
            options.add(host.getString(R.string.controls_swap, inputName(alternate)));
        }
        host.choose(conflict, options.toArray(new String[0]), -1, i -> {
            if (i == 0) controls.bind(input, action);
            else controls.swap(current.get(i - 1), input);
            host.render();
        });
    }

    private List<String> inputNames(List<Input> inputs) {
        List<String> names = new ArrayList<>();
        for (Input input : inputs) names.add(inputName(input));
        return names;
    }

    private String inputName(Input input) {
        if (!input.name().startsWith("KEY_")) return label(input.name());
        String code = KeyEvent.keyCodeToString(input.keyCode);
        if (!code.startsWith("KEYCODE_")) return host.getString(R.string.controls_numeric_key, input.keyCode);
        return code.substring("KEYCODE_".length()).replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String text(int id) {
        return host.getString(id);
    }

    private String label(String name) {
        switch (name) {
            case "NONE": return text(R.string.controls_none);
            case "NEXT_PAGE": return text(R.string.controls_next_page);
            case "PREV_PAGE": return text(R.string.controls_prev_page);
            case "NEXT_CHAPTER": return text(R.string.controls_next_chapter);
            case "PREV_CHAPTER": return text(R.string.controls_prev_chapter);
            case "MENU": return text(R.string.controls_menu);
            case "CONTENTS": return text(R.string.controls_contents);
            case "STRONG_CLEAN": return text(R.string.controls_strong_clean);
            case "TOGGLE_PANEL": return text(R.string.controls_toggle_panel);
            case "PAGE_DOWN": return text(R.string.controls_page_down);
            case "PAGE_UP": return text(R.string.controls_page_up);
            case "VOLUME_DOWN": return text(R.string.controls_volume_down);
            case "VOLUME_UP": return text(R.string.controls_volume_up);
            case "DPAD_RIGHT": return text(R.string.controls_dpad_right);
            case "DPAD_LEFT": return text(R.string.controls_dpad_left);
            case "SWIPE_LEFT": return text(R.string.controls_swipe_left);
            case "SWIPE_RIGHT": return text(R.string.controls_swipe_right);
            case "SWIPE_UP": return text(R.string.controls_swipe_up);
            case "SWIPE_DOWN": return text(R.string.controls_swipe_down);
            case "TAP_LEFT": return text(R.string.controls_tap_left);
            case "TAP_CENTER": return text(R.string.controls_tap_center);
            case "TAP_RIGHT": return text(R.string.controls_tap_right);
            case "TAP_TOP": return text(R.string.controls_tap_top);
            case "TAP_BOTTOM": return text(R.string.controls_tap_bottom);
            case "CLASSIC": return text(R.string.controls_classic);
            case "COLUMNS": return text(R.string.controls_columns);
            case "ROWS": return text(R.string.controls_rows);
            case "CROSS": return text(R.string.controls_cross);
            default: return name;
        }
    }
}

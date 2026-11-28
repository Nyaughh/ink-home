package dev.inkhome;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/** App-local controls. Only explicit bindings are stored, including an explicit NONE. */
final class Controls {
    private static final String PREFIX = "controls.binding.";
    private static final String LAYOUT = "controls.layout";

    enum Action { NONE, NEXT_PAGE, PREV_PAGE, NEXT_CHAPTER, PREV_CHAPTER, MENU, CONTENTS, STRONG_CLEAN, TOGGLE_PANEL }
    enum Layout { CLASSIC, COLUMNS, ROWS, CROSS }

    static final class Input {
        static final Input PAGE_DOWN = new Input("PAGE_DOWN", KeyEvent.KEYCODE_PAGE_DOWN, Action.NEXT_PAGE);
        static final Input PAGE_UP = new Input("PAGE_UP", KeyEvent.KEYCODE_PAGE_UP, Action.PREV_PAGE);
        static final Input VOLUME_DOWN = new Input("VOLUME_DOWN", KeyEvent.KEYCODE_VOLUME_DOWN, Action.NEXT_PAGE);
        static final Input VOLUME_UP = new Input("VOLUME_UP", KeyEvent.KEYCODE_VOLUME_UP, Action.PREV_PAGE);
        static final Input DPAD_RIGHT = new Input("DPAD_RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT, Action.NEXT_PAGE);
        static final Input DPAD_LEFT = new Input("DPAD_LEFT", KeyEvent.KEYCODE_DPAD_LEFT, Action.PREV_PAGE);
        static final Input SWIPE_LEFT = new Input("SWIPE_LEFT", 0, Action.NEXT_PAGE);
        static final Input SWIPE_RIGHT = new Input("SWIPE_RIGHT", 0, Action.PREV_PAGE);
        static final Input SWIPE_UP = new Input("SWIPE_UP", 0, Action.NONE);
        static final Input SWIPE_DOWN = new Input("SWIPE_DOWN", 0, Action.NONE);
        static final Input TAP_LEFT = new Input("TAP_LEFT", 0, Action.PREV_PAGE);
        static final Input TAP_CENTER = new Input("TAP_CENTER", 0, Action.MENU);
        static final Input TAP_RIGHT = new Input("TAP_RIGHT", 0, Action.NEXT_PAGE);
        static final Input TAP_TOP = new Input("TAP_TOP", 0, Action.PREV_PAGE);
        static final Input TAP_BOTTOM = new Input("TAP_BOTTOM", 0, Action.NEXT_PAGE);

        private final String name;
        final int keyCode;
        private final Action defaultAction;

        private Input(String name, int keyCode, Action defaultAction) {
            this.name = name;
            this.keyCode = keyCode;
            this.defaultAction = defaultAction;
        }

        String name() { return name; }

        @Override public boolean equals(Object o) {
            return o instanceof Input && name.equals(((Input) o).name);
        }

        @Override public int hashCode() { return name.hashCode(); }
    }

    private static final Input[] BUILT_INS = {
            Input.PAGE_DOWN, Input.PAGE_UP, Input.VOLUME_DOWN, Input.VOLUME_UP,
            Input.DPAD_RIGHT, Input.DPAD_LEFT, Input.SWIPE_LEFT, Input.SWIPE_RIGHT,
            Input.SWIPE_UP, Input.SWIPE_DOWN, Input.TAP_LEFT, Input.TAP_CENTER,
            Input.TAP_RIGHT, Input.TAP_TOP, Input.TAP_BOTTOM
    };

    private final SharedPreferences prefs;

    Controls(SharedPreferences prefs) { this.prefs = prefs; }

    static Input key(int code) {
        if (code <= 0) return null;
        for (Input input : BUILT_INS) {
            if (input.keyCode == code) return input;
        }
        return new Input("KEY_" + code, code, Action.NONE);
    }

    Input[] inputs() {
        List<Input> result = new ArrayList<>(Arrays.asList(BUILT_INS));
        String customPrefix = PREFIX + "KEY_";
        TreeSet<Integer> codes = new TreeSet<>();
        for (String name : prefs.getAll().keySet()) {
            if (!name.startsWith(customPrefix)) continue;
            try {
                int code = Integer.parseInt(name.substring(customPrefix.length()));
                Input input = key(code);
                if (input != null && name.equals(PREFIX + input.name())) codes.add(code);
            } catch (NumberFormatException ignored) { }
        }
        for (int code : codes) result.add(key(code));
        return result.toArray(new Input[0]);
    }

    Action keyAction(int keyCode, boolean defaultDpadPaging) {
        Input input = key(keyCode);
        if (!defaultDpadPaging && (input == Input.DPAD_LEFT || input == Input.DPAD_RIGHT)
                && !overridden(input)) return Action.NONE;
        return action(input);
    }

    Action action(Input input) {
        if (input == null) return Action.NONE;
        String saved = prefs.getString(PREFIX + input.name(), null);
        if (saved != null) {
            try { return Action.valueOf(saved); }
            catch (IllegalArgumentException ignored) { }
        }
        Action action = input.defaultAction;
        if (input.keyCode > 0 && prefs.getBoolean(Store.PAGE_REVERSED, false)) {
            if (action == Action.NEXT_PAGE) return Action.PREV_PAGE;
            if (action == Action.PREV_PAGE) return Action.NEXT_PAGE;
        }
        return action;
    }

    boolean overridden(Input input) {
        return input != null && prefs.contains(PREFIX + input.name());
    }

    void bind(Input input, Action action) {
        prefs.edit().putString(PREFIX + input.name(), action.name()).apply();
    }

    void swap(Input first, Input second) {
        if (first.equals(second)) return;
        prefs.edit().putString(PREFIX + first.name(), action(second).name())
                .putString(PREFIX + second.name(), action(first).name()).apply();
    }

    Layout layout() {
        try { return Layout.valueOf(prefs.getString(LAYOUT, Layout.CLASSIC.name())); }
        catch (IllegalArgumentException ignored) { return Layout.CLASSIC; }
    }

    void setLayout(Layout value) { prefs.edit().putString(LAYOUT, value.name()).apply(); }

    Input tap(float x, float y) {
        switch (layout()) {
            case CLASSIC:
                if (x > .3f && x < .7f && y > .2f && y < .8f) return Input.TAP_CENTER;
                return x < .3f ? Input.TAP_LEFT : Input.TAP_RIGHT;
            case ROWS:
                if (y < 1f / 3) return Input.TAP_TOP;
                return y < 2f / 3 ? Input.TAP_CENTER : Input.TAP_BOTTOM;
            case CROSS:
                if (y < .25f) return Input.TAP_TOP;
                if (y >= .75f) return Input.TAP_BOTTOM;
                break;
            default: break;
        }
        if (x < 1f / 3) return Input.TAP_LEFT;
        return x < 2f / 3 ? Input.TAP_CENTER : Input.TAP_RIGHT;
    }

    void reset() {
        SharedPreferences.Editor editor = prefs.edit().remove(LAYOUT);
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(PREFIX)) editor.remove(key);
        }
        editor.apply();
    }
}

package dev.inkhome;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.inkhome.folio.Folio;
import dev.inkhome.folio.core.TypeScale;

/** Interactive sheet and notice specimens using the same host as the launcher and reader. */
public final class SheetGallery {
    private SheetGallery() {}

    public static void add(LinearLayout content, Folio f) {
        Activity activity = (Activity) content.getContext();
        Ink ink = new Ink(activity, f.textScale, f.uiScale);
        LinearLayout group = ink.col();
        content.addView(group);
        group.addView(ink.text("Sheets and notices", TypeScale.TITLE_SM, ink.TEXT));
        String[] names = {"Actions", "Choice", "Prompt", "Confirm", "Notice with Undo"};
        String[] choices = {"Paper", "Night"};
        int[] selected = {0};
        SheetHost[] host = {null};
        for (int i = 0; i < names.length; i++) {
            int index = i;
            TextView button = ink.body(names[i]);
            button.setGravity(Gravity.CENTER_VERTICAL);
            button.setMinimumHeight(ink.dp(48));
            button.setBackground(ink.listRow(0));
            group.addView(button);
            button.setOnClickListener(v -> {
                if (host[0] == null) {
                    FrameLayout root = group.getRootView().findViewById(android.R.id.content);
                    host[0] = new SheetHost(activity, root, null, () -> ink);
                }
                SheetHost sheets = host[0];
                switch (index) {
                    case 0:
                        sheets.choose("Book actions", new String[]{"Open", "Details"}, -1,
                                w -> sheets.notice(w == 0 ? "Open selected" : "Details selected", null, null));
                        break;
                    case 1:
                        sheets.choose("Theme", choices, selected[0], w -> selected[0] = w);
                        break;
                    case 2:
                        sheets.prompt("Collection name", "Reading", value -> sheets.notice(value, null, null));
                        break;
                    case 3:
                        sheets.message("Install update?", "An update is available.", "Install",
                                () -> sheets.notice("Install selected", null, null));
                        break;
                    default:
                        button.setText("Notice shown");
                        sheets.notice("Sample removed", () -> button.setText(names[index]), null);
                }
            });
        }
        group.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) {
                if (host[0] != null) host[0].close();
                host[0] = null;
            }
        });
    }
}

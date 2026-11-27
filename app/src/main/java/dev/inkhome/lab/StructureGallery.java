package dev.inkhome.lab;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.inkhome.folio.*;
import dev.inkhome.folio.core.Tokens;
import dev.inkhome.folio.core.TypeScale;
import dev.inkhome.folio.widget.*;

public final class StructureGallery {
    private static final String[] TITLES = {"The time machine", "The lost world", "The secret garden"};
    private static final String[] AUTHORS = {"H. G. Wells", "Arthur Conan Doyle", "Frances Hodgson Burnett"};
    private final LinearLayout sample;
    private final Folio f;
    private final Structure structure;
    private int page = 1, tab, destination = 1;
    private boolean grid;
    private String detail;

    public static void add(LinearLayout content, Folio f) {
        new StructureGallery(content, f).draw();
    }

    private StructureGallery(LinearLayout content, Folio f) {
        this.f = f;
        structure = new Structure(f);
        sample = new LinearLayout(f.context);
        sample.setOrientation(LinearLayout.VERTICAL);
        content.addView(sample, -1, -2);
    }

    private void show(String message) {
        detail = message;
        draw();
    }

    private void selectDestination(int index) {
        destination = index;
        detail = null;
        draw();
    }

    private void move(int delta) {
        page = Math.min(TITLES.length - 1, Math.max(0, page + delta));
        draw();
    }

    private Pager pager(boolean inline) {
        Pager pager = new Pager(f, inline, () -> move(-1), () -> move(1));
        pager.bind(page, TITLES.length);
        return pager;
    }

    private View cover() {
        TextView cover = Type.text(f, TITLES[page], TypeScale.READING, f.palette.ink);
        cover.setTypeface(Type.reading(f.context, false));
        cover.setGravity(Gravity.CENTER);
        cover.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, f.u(grid ? 15 : 9));
        cover.setPadding(f.u(2), 0, f.u(2), 0);
        cover.setBackground(new Shapes(f).stroke(f.palette.paper, f.palette.strong));
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return cover;
    }

    private void draw() {
        sample.removeAllViews();
        String home = destination < 0 ? "Settings" : structure.menuLabels()[destination];
        sample.addView(structure.statusStrip("9:41", 2, 3, 76, false, () -> selectDestination(7)));
        sample.addView(structure.appBar(detail == null ? home : detail, detail == null ? "3" : null,
                detail == null ? null : () -> show(null),
                new Structure.Action(grid ? "list" : "grid", grid ? "Show list" : "Show grid",
                        () -> { grid = !grid; draw(); })));
        sample.addView(structure.tabs(new String[]{"Reading", "All", "Finished"}, tab,
                i -> { tab = i; page = 0; draw(); }));
        String heading = new String[]{"In progress", "All books", "Finished books"}[tab];
        sample.addView(structure.sectionHead(heading, false, pager(true)));
        int percent = tab == 2 ? 100 : 28 + page * 19;
        Runnable open = () -> show(TITLES[page]);
        if (grid) {
            View cell = BookViews.cell(f, cover(), TITLES[page], percent, open);
            sample.addView(cell, f.u(96), -2);
        } else {
            sample.addView(BookViews.row(f, cover(), TITLES[page], AUTHORS[page], percent, true, open));
        }
        sample.addView(structure.listRow(null, "Reading goal", "30 minutes per day", null,
                Tokens.Row.TWO_LINE, true, () -> show("Reading goal")));
        sample.addView(new Progress(f, 5, true, percent));
        sample.addView(pager(false));
        sample.addView(structure.dock(destination == -1, "Today 48 / 30 min", destination,
                () -> selectDestination(destination == -1 ? 0 : -1), () -> selectDestination(2),
                this::selectDestination));
    }
}

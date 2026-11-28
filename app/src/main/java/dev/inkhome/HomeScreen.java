package dev.inkhome;

import android.appwidget.AppWidgetHostView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.List;

final class HomeScreen implements Screen {
    @Override
    public void build(MainActivity host) {
        LinearLayout col = host.page();
        int margin = host.ink.dp(host.ink.folio.windowClass().margin);
        col.setPadding(margin, host.ink.dp(16), margin, host.ink.dp(8));
        host.applyWallpaper();
        HomeSections sections = HomeSections.load(host);
        if (!sections.isDefault()) {
            customHome(host, col, sections);
            return;
        }
        String layout = host.store.homeLayout();
        List<Library.Book> reading = host.reading();

        boolean setupShown = SetupScreen.addHomeCallout(host, col);
        if (!host.hasFileAccess()) {
            if (!setupShown) col.addView(dev.inkhome.folio.widget.Callout.create(host.ink.folio,
                    host.getString(R.string.ui_show_your_books), host.getString(R.string.ui_let_ink_home_read_your_book_files),
                    host.getString(R.string.ui_allow_file_access), v -> host.requestFileAccess()));
        } else if (layout.equals("minimal")) {
            host.homeMinimal(col, reading);
        } else if (layout.equals("shelf")) {
            host.coverPage = !host.books.isEmpty();
            host.homeShelf(col, reading);
        } else {
            host.coverPage = !host.books.isEmpty();
            host.homeCover(col, reading, setupShown);
        }
        // The book list (if any) takes the free space; otherwise a spacer pushes favorites down.
        if (col.findViewWithTag("list") == null) {
            LinearLayout right = col.findViewWithTag("homeRight");
            (right == null ? col : right).addView(host.ink.vflex());
        }
        host.homeFavorites(col);
    }

    private void customHome(MainActivity host, LinearLayout col, HomeSections sections) {
        LinearLayout body = host.ink.col();
        SetupScreen.addHomeCallout(host, body);
        for (String id : sections.order) {
            if (!sections.visible.contains(id)) continue;
            View section;
            int widget = HomeSections.widgetId(id);
            if (widget >= 0) {
                section = host.homeWidgets.view(widget);
                if (section instanceof AppWidgetHostView) {
                    AppWidgetHostView view = (AppWidgetHostView) section;
                    if (view.getAppWidgetInfo() != null) view.setMinimumHeight(view.getAppWidgetInfo().minHeight);
                }
                if (sections.borders) {
                    LinearLayout frame = host.ink.col();
                    frame.setBackground(host.ink.card(0, false));
                    frame.addView(section);
                    section = frame;
                }
            } else {
                section = HomeTiles.build(host, id, sections);
                if (id.equals("current") || id.equals("next")) host.coverPage |= !host.books.isEmpty();
            }
            body.addView(section, host.ink.m(-1, -2, 0, sections.compact ? 4 : 10, 0, 0));
        }
        ScrollView scroll = host.ink.scroller(body);
        host.bindScrollKeys(scroll);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }
}

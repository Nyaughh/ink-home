package dev.inkhome;

import android.widget.LinearLayout;

final class HomeScreen implements Screen {
    @Override public void build(MainActivity host) {
        LinearLayout page = host.page();
        int margin = host.ink.dp(host.ink.folio.windowClass().margin);
        page.setPadding(margin, host.ink.dp(16), margin, host.ink.dp(8));
        host.applyWallpaper();
        boolean setup = SetupScreen.addHomeCallout(host, page);
        if (!host.hasFileAccess() && !setup) {
            page.addView(dev.inkhome.folio.widget.Callout.create(host.ink.folio,
                    host.getString(R.string.ui_show_your_books), host.getString(R.string.ui_let_ink_home_read_your_book_files),
                    host.getString(R.string.ui_allow_file_access), v -> host.requestFileAccess()));
        }
        if (host.homeEditor.active()) {
            for (int i = 0; i < page.getChildCount(); i++) page.getChildAt(i).setEnabled(false);
            host.homeEditor.show(page);
        } else {
            page.setOnLongClickListener(v -> { host.editHome(); return true; });
            page.addView(new HomeGrid(host, HomeLayout.load(host), null, host::editHome, null),
                    new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }
}

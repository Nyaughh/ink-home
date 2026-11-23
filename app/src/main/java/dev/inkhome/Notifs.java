package dev.inkhome;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps the shade's notifications for Ink Home's status bar and quick panel. */
public class Notifs extends NotificationListenerService {

    interface Listener { void onCount(int count); }

    /** One notification, reduced to what the quick panel shows. */
    static final class Item {
        final String key, app, title, text;
        final long when;
        final PendingIntent open;
        Item(String key, String app, String title, String text, long when, PendingIntent open) {
            this.key = key; this.app = app; this.title = title; this.text = text; this.when = when; this.open = open;
        }
    }

    private static volatile int count = -1;
    private static volatile List<Item> items = Collections.emptyList();
    private static volatile Listener listener;
    private static volatile Notifs instance;

    /** -1 when Ink Home has no notification access. */
    static int count() { return count; }

    /** Clearable notifications, newest first. */
    static List<Item> items() { return items; }

    static void setListener(Listener l) { listener = l; }

    static void dismiss(String key) {
        Notifs n = instance;
        if (n != null) try { n.cancelNotification(key); } catch (Exception ignored) { }
    }

    static void clearAll() {
        Notifs n = instance;
        if (n != null) try { n.cancelAllNotifications(); } catch (Exception ignored) { }
    }

    @Override public void onListenerConnected() { instance = this; recount(); }
    @Override public void onListenerDisconnected() { instance = null; count = -1; items = Collections.emptyList(); notifyListener(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { recount(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { recount(); }

    private void recount() {
        List<Item> out = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();
            for (StatusBarNotification s : getActiveNotifications()) {
                Notification no = s.getNotification();
                boolean ongoing = (no.flags & Notification.FLAG_ONGOING_EVENT) != 0;
                boolean summary = (no.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
                if (ongoing || summary || !s.isClearable()) continue;
                Bundle ex = no.extras;
                CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence text = ex.getCharSequence(Notification.EXTRA_TEXT);
                String app;
                try {
                    app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(s.getPackageName(), 0)));
                } catch (Exception e) {
                    app = s.getPackageName();
                }
                out.add(new Item(s.getKey(), app, title == null ? app : title.toString(), text == null ? "" : text.toString(),
                        s.getPostTime(), no.contentIntent));
            }
        } catch (Exception ignored) { }
        out.sort((a, b) -> Long.compare(b.when, a.when));
        items = out;
        count = out.size();
        notifyListener();
    }

    private static void notifyListener() {
        Listener l = listener;
        if (l != null) l.onCount(count);
    }
}

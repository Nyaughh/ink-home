package dev.inkhome;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Keeps Ink Home up to date from its GitHub releases. A release carries ink-home.apk and
 * ink-home.apk.sha256; the download must match that checksum, and Android itself refuses
 * any update not signed with the same key as the installed app.
 */
final class Updater {

    static final String REPO = "Nyaughh/ink-home";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    static final class Release {
        final String version, notes, apkUrl, shaUrl;
        Release(String version, String notes, String apkUrl, String shaUrl) {
            this.version = version; this.notes = notes; this.apkUrl = apkUrl; this.shaUrl = shaUrl;
        }
    }

    static String installedVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    /** The latest release if it's newer than this app, else null. Call off the UI thread. */
    static Release check(Context c) throws Exception {
        JSONObject r = new JSONObject(new String(get(API), "UTF-8"));
        String tag = r.optString("tag_name", "").replaceFirst("^v", "");
        if (tag.isEmpty() || compare(tag, installedVersion(c)) <= 0) return null;
        String apk = null, sha = null;
        JSONArray assets = r.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            String name = a.optString("name"), url = a.optString("browser_download_url");
            if (name.endsWith(".apk")) apk = url;
            else if (name.endsWith(".apk.sha256")) sha = url;
        }
        if (apk == null) return null;
        return new Release(tag, r.optString("body", ""), apk, sha);
    }

    /** Downloads the APK and checks it against the published SHA-256. Call off the UI thread. */
    static File download(Context c, Release rel) throws Exception {
        File dir = new File(c.getCacheDir(), "updates");
        dir.mkdirs();
        File out = new File(dir, "ink-home-" + rel.version + ".apk");
        byte[] data = get(rel.apkUrl);
        if (rel.shaUrl != null) {
            String expected = new String(get(rel.shaUrl), "UTF-8").trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            String actual = sha256(data);
            if (!expected.equals(actual)) throw new SecurityException("Checksum mismatch");
        }
        try (OutputStream o = new FileOutputStream(out)) { o.write(data); }
        return out;
    }

    /** Hands the APK to Android's installer, which asks the user to confirm. */
    static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("ink-home.apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent done = new Intent(c, Result.class);
            PendingIntent pending = PendingIntent.getBroadcast(c, id, done,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            s.commit(pending.getIntentSender());
        }
    }

    /** Receives the installer's answer and shows Android's confirmation screen when it asks for one. */
    public static class Result extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(confirm);
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                android.widget.Toast.makeText(c, "Update didn't install" + (msg == null ? "" : ": " + msg), android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Compares dotted version numbers, e.g. 1.2.10 > 1.2.9. */
    static int compare(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static byte[] get(String url) throws Exception {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(60000);
            h.setInstanceFollowRedirects(false);
            h.setRequestProperty("User-Agent", "InkHome-Updater");
            h.setRequestProperty("Accept", url.equals(API) ? "application/vnd.github+json" : "application/octet-stream");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400 && h.getHeaderField("Location") != null) {
                url = h.getHeaderField("Location");
                h.disconnect();
                continue;
            }
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                return bo.toByteArray();
            } finally {
                h.disconnect();
            }
        }
        throw new java.io.IOException("Too many redirects");
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }
}

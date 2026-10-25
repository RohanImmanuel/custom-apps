package com.rohan.linkrouter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.service.quicksettings.TileService;

import androidx.browser.customtabs.CustomTabsIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Where links go: the chosen browser, or Chrome while a temporary override is running.
// The override just has an expiry time, so it reverts by itself without any alarm or user input.
final class Router {
    static final String CHROME = "com.android.chrome";
    // Chrome honours ephemeral (incognito) Custom Tabs from other apps (verified on Chrome 155).
    // Brave 1.97 ignores both that and the incognito extra, so it isn't listed; other Chromium forks are unverified.
    static final Set<String> CHROMIUM = Set.of(CHROME, "com.chrome.beta", "com.chrome.dev", "com.chrome.canary");
    // Firefox family: private links via Fenix's private_browsing_mode extra (untested)
    static final Set<String> FIREFOX = Set.of("org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix");
    static final String CHROME_INCOGNITO_EXTRA = "com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("router", Context.MODE_PRIVATE);
    }

    /** Installed browsers other than this app: package -> label, in system order. */
    static Map<String, String> browsers(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE);
        Map<String, String> out = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, PackageManager.MATCH_ALL)) {
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(c.getPackageName())) out.putIfAbsent(pkg, ri.loadLabel(pm).toString());
        }
        return out;
    }

    static boolean supportsPrivate(String pkg) {
        return CHROMIUM.contains(pkg) || FIREFOX.contains(pkg);
    }

    static long tempUntil(Context c) {
        return prefs(c).getLong("tempUntil", 0);
    }

    static boolean tempActive(Context c) {
        return tempUntil(c) > System.currentTimeMillis();
    }

    static final int OVERRIDE_MINUTES = 5;
    static final int NOTIFICATION_ID = 1;

    /** Chrome for 5 minutes, with a countdown notification that can switch back early. */
    static void startOverride(Context c) {
        long until = System.currentTimeMillis() + OVERRIDE_MINUTES * 60_000L;
        prefs(c).edit().putLong("tempUntil", until).apply();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("override", "Chrome override", NotificationManager.IMPORTANCE_LOW));
        PendingIntent back = PendingIntent.getBroadcast(c, 0, new Intent(c, StopOverrideReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, HomeActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String name = browsers(c).getOrDefault(chosen(c), "your browser");
        Notification n = new Notification.Builder(c, "override")
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setColor(HudColors.RED)
                .setContentTitle("Chrome override engaged")
                .setContentText("Back to " + name + " automatically")
                .setWhen(until)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(until - System.currentTimeMillis()) // vanishes when the override ends
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Switch back", back).build())
                .build();
        nm.notify(NOTIFICATION_ID, n); // silently skipped if notifications aren't allowed
        refreshTile(c);
    }

    static void stopOverride(Context c) {
        prefs(c).edit().putLong("tempUntil", 0).apply();
        c.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        refreshTile(c);
    }

    static void refreshTile(Context c) {
        TileService.requestListeningState(c, new ComponentName(c, OverrideTile.class));
    }

    static String minutesLeft(Context c) {
        return Math.max(1, (tempUntil(c) - System.currentTimeMillis() + 59_999) / 60_000) + " min left";
    }

    static String chosen(Context c) {
        Map<String, String> installed = browsers(c);
        String pkg = prefs(c).getString("browser", null);
        if (pkg != null && installed.containsKey(pkg)) return pkg;
        return installed.isEmpty() ? null : new ArrayList<>(installed.keySet()).get(0);
    }

    static String target(Context c) {
        return tempActive(c) && browsers(c).containsKey(CHROME) ? CHROME : chosen(c);
    }

    /** Builds the intent that opens the incoming link in the target browser. */
    static Intent forward(Context c, Intent in) {
        Uri uri = in.getData();
        String pkg = target(c);
        if (uri == null || pkg == null) return null;
        boolean priv = !tempActive(c) && prefs(c).getBoolean("private", false) && supportsPrivate(pkg);

        Intent out;
        if (priv && CHROMIUM.contains(pkg)) {
            out = new CustomTabsIntent.Builder().setEphemeralBrowsingEnabled(true).build().intent;
            out.setData(uri);
            out.putExtra(CHROME_INCOGNITO_EXTRA, true); // older Chromium builds
        } else {
            // copy the original so Custom Tab requests from other apps keep their styling/session
            out = new Intent(in);
            out.setComponent(null);
            out.setSelector(null);
            if (priv) out.putExtra("private_browsing_mode", true); // Firefox
        }
        out.setPackage(pkg);
        out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return out;
    }
}

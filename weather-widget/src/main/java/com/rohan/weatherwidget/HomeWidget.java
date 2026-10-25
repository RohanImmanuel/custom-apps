package com.rohan.weatherwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

// IKEA hub widget in the dot-matrix style: room sensors, the main light, and one-tap light modes.
public class HomeWidget extends AppWidgetProvider {
    static final String TOGGLE = "com.rohan.weatherwidget.HOME_TOGGLE", MODE = "com.rohan.weatherwidget.HOME_MODE",
            REFRESH = "com.rohan.weatherwidget.HOME_REFRESH";
    static final int WARM = 0xFFFFB86B, GREY = 0xFF8C8C8C, LINE = 0xFF262626, STUFFY = 1000;
    static final String[] DUST = {"#   #  ", "  #   #", " #  #  ", "#   # #", "  #    ", " #  # #", "#    # "};
    static final String[] TUNE = {"  #    ", "#######", "  #    ", "       ", "     # ", "#######", "     # "};
    static final String[] POWER = {"   #   ", " # # # ", "#  #  #", "#  #  #", "#     #", " #   # ", "  ###  "};
    static final int[] MODE_IDS = {R.id.mode0, R.id.mode1, R.id.mode2, R.id.mode3},
            MODE_ICON_IDS = {R.id.modeIcon0, R.id.modeIcon1, R.id.modeIcon2, R.id.modeIcon3},
            MODE_LABEL_IDS = {R.id.modeLabel0, R.id.modeLabel1, R.id.modeLabel2, R.id.modeLabel3};
    // sun, full glow, smaller glow, crescent moon
    static final String[][] MODE_ICONS = {
            {"   #   ", " #   # ", "  ###  ", "# ### #", "  ###  ", " #   # ", "   #   "},
            {"  ###  ", " ##### ", "#######", "#######", "#######", " ##### ", "  ###  "},
            {"       ", "  ###  ", " ##### ", " ##### ", " ##### ", "  ###  ", "       "},
            {"  ###  ", " ##    ", "##     ", "##     ", "##     ", " ##    ", "  ###  "}};
    // name, brightness %, cool white (true) or warmest white (false), swatch colour
    static final String[] MODE_NAMES = {"BRIGHT", "COSY", "COSIER", "NIGHT"};
    static final int[] MODE_LEVELS = {100, 60, 30, 8};
    static final boolean[] MODE_COOL = {true, false, false, false};
    static final int[] MODE_COLORS = {0xFFEEF3FF, 0xFFFFC07A, 0xFFFFA552, 0xFFFF8A3D};

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        async(() -> refresh(c));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        int arg = i.getIntExtra("mode", 0);
        if (TOGGLE.equals(a) || MODE.equals(a)) async(() -> {
            // show the expected result straight away, then do it, then confirm with the hub's real state
            JSONArray cached = cached(c);
            if (cached == null) { refresh(c); return; }
            JSONArray expected = apply(cached, a, arg);
            render(c, expected, null);
            if (send(c, cached, a, arg)) {
                HomeHub.prefs(c).edit().putString("cache", expected.toString()).apply();
                settle(c, expected);
            } else {
                refresh(c);
                status(c, "COULDN'T REACH HUB", WeatherWidget.RED);
            }
        });
        else if (REFRESH.equals(a)) async(() -> refresh(c));
        else super.onReceive(c, i);
    }

    void async(Runnable r) {
        PendingResult p = goAsync(); // network can't run on the main thread
        new Thread(() -> { try { r.run(); } finally { p.finish(); } }).start();
    }

    /** The light's target attributes after an action: toggle, or preset i. */
    static JSONObject target(JSONObject a, String action, int arg) throws Exception {
        JSONObject t = new JSONObject(a.toString());
        int lo = Math.min(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
        int hi = Math.max(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
        if (TOGGLE.equals(action)) {
            t.put("isOn", !a.optBoolean("isOn"));
        } else {
            t.put("isOn", true).put("lightLevel", MODE_LEVELS[arg]);
            if (hi > 0) t.put("colorTemperature", MODE_COOL[arg] ? hi : lo);
        }
        return t;
    }

    static JSONArray apply(JSONArray devices, String action, int arg) {
        try {
            JSONArray copy = new JSONArray(devices.toString());
            JSONObject light = find(copy, "light", null);
            if (light != null) light.put("attributes", target(light.getJSONObject("attributes"), action, arg));
            return copy;
        } catch (Exception e) {
            return devices;
        }
    }

    /** Sends only what changes, one attribute per request; false if the hub couldn't be reached. */
    static boolean send(Context c, JSONArray devices, String action, int arg) {
        try {
            JSONObject light = find(devices, "light", null);
            if (light == null) return false;
            String id = light.getString("id");
            JSONObject a = light.getJSONObject("attributes"), t = target(a, action, arg);
            if (t.optBoolean("isOn") != a.optBoolean("isOn")) HomeHub.set(c, id, "isOn", t.optBoolean("isOn"));
            if (t.optInt("colorTemperature") != a.optInt("colorTemperature")) HomeHub.set(c, id, "colorTemperature", t.optInt("colorTemperature"));
            if (t.optInt("lightLevel") != a.optInt("lightLevel")) HomeHub.set(c, id, "lightLevel", t.optInt("lightLevel"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Applies a mode for the light overlay. Sends every attribute: the overlay may have moved the light since the cache. */
    static void act(Context c, int mode) {
        try {
            JSONObject light = find(cached(c), "light", null);
            if (light == null) return;
            String id = light.getString("id");
            JSONObject t = target(light.getJSONObject("attributes"), MODE, mode);
            HomeHub.set(c, id, "isOn", true);
            if (t.optInt("colorTemperature") > 0) HomeHub.set(c, id, "colorTemperature", t.optInt("colorTemperature"));
            HomeHub.set(c, id, "lightLevel", t.optInt("lightLevel"));
        } catch (Exception ignored) {
        }
    }

    /**
     * The hub accepts a change before the light reports it, so reading straight back shows the old state.
     * Keep the expected state on screen and re-read once a second until the hub agrees (or ~4 s pass).
     */
    static void settle(Context c, JSONArray expected) {
        try {
            JSONObject want = find(expected, "light", null).getJSONObject("attributes");
            for (int i = 0; i < 4; i++) {
                Thread.sleep(1000);
                JSONArray devices = HomeHub.devices(c);
                JSONObject light = find(devices, "light", null);
                if (light != null && same(light.getJSONObject("attributes"), want) || i == 3) {
                    HomeHub.prefs(c).edit().putString("cache", devices.toString()).apply();
                    render(c, devices, null);
                    return;
                }
            }
        } catch (Exception e) {
            refresh(c);
        }
    }

    static boolean same(JSONObject have, JSONObject want) {
        if (have.optBoolean("isOn") != want.optBoolean("isOn")) return false;
        if (!want.optBoolean("isOn")) return true;
        return Math.abs(have.optInt("lightLevel") - want.optInt("lightLevel")) <= 2
                && Math.abs(have.optInt("colorTemperature") - want.optInt("colorTemperature")) <= 100;
    }

    static JSONArray cached(Context c) {
        try {
            String s = HomeHub.prefs(c).getString("cache", null);
            return s == null ? null : new JSONArray(s);
        } catch (Exception e) {
            return null;
        }
    }

    static void refresh(Context c) {
        if (!HomeHub.paired(c)) {
            render(c, null, null);
            return;
        }
        try {
            JSONArray devices = HomeHub.devices(c);
            HomeHub.prefs(c).edit().putString("cache", devices.toString()).apply();
            render(c, devices, null);
        } catch (Exception e) {
            // off home Wi-Fi or hub down: keep showing the last known state, and say so
            JSONArray cached = cached(c);
            if (cached != null) render(c, cached, "HUB UNREACHABLE · TAP TO RETRY");
        }
    }

    static void status(Context c, String text, int color) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        RemoteViews s = new RemoteViews(c.getPackageName(), R.layout.home_widget);
        s.setTextViewText(R.id.homeStatus, text);
        s.setTextColor(R.id.homeStatus, color);
        s.setViewVisibility(R.id.homeStatus, android.view.View.VISIBLE);
        m.partiallyUpdateAppWidget(m.getAppWidgetIds(new ComponentName(c, HomeWidget.class)), s);
    }

    static void render(Context c, JSONArray devices, String problem) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, HomeWidget.class));
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.home_widget);
        wire(c, v);
        if (devices == null) {
            v.setTextViewText(R.id.homeStatus, "NOT PAIRED");
            v.setViewVisibility(R.id.homeStatus, android.view.View.VISIBLE);
            v.setTextViewText(R.id.lightName, "TAP TO PAIR YOUR IKEA HUB");
            PendingIntent pair = PendingIntent.getActivity(c, 0, new Intent(c, HubPairActivity.class), PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.lightPanel, pair);
            v.setOnClickPendingIntent(R.id.homeRoot, pair);
        } else {
            try {
                draw(v, devices);
            } catch (Exception ignored) {
            }
            if (problem != null) {
                v.setTextViewText(R.id.homeStatus, problem);
                v.setTextColor(R.id.homeStatus, GREY);
                v.setViewVisibility(R.id.homeStatus, android.view.View.VISIBLE);
            }
        }
        m.updateAppWidget(ids, v);
    }

    static void wire(Context c, RemoteViews v) {
        v.setOnClickPendingIntent(R.id.homeRoot, self(c, REFRESH, 0));
        v.setOnClickPendingIntent(R.id.lightPanel, self(c, TOGGLE, 0));
        v.setOnClickPendingIntent(R.id.lightMore, PendingIntent.getActivity(c, 1, new Intent(c, LightActivity.class),
                PendingIntent.FLAG_IMMUTABLE));
        v.setImageViewBitmap(R.id.lightMore, WeatherWidget.matrix(6, GREY, TUNE));
        for (int i = 0; i < 4; i++) {
            v.setTextViewText(MODE_LABEL_IDS[i], MODE_NAMES[i]);
            styleTile(v, i, false);
            v.setOnClickPendingIntent(MODE_IDS[i], self(c, MODE, i));
        }
    }

    static PendingIntent self(Context c, String action, int mode) {
        return PendingIntent.getBroadcast(c, (action + mode).hashCode(),
                new Intent(c, HomeWidget.class).setAction(action).putExtra("mode", mode),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void draw(RemoteViews v, JSONArray devices) throws Exception {
        // temperature from a plain temp sensor if there is one, the rest from the air quality monitor
        JSONObject temp = find(devices, "environmentSensor", "currentTemperature", "currentCO2");
        if (temp == null) temp = find(devices, "environmentSensor", "currentTemperature");
        JSONObject hum = find(devices, "environmentSensor", "currentRH");
        JSONObject air = find(devices, "environmentSensor", "currentCO2");
        if (temp != null) {
            long t = Math.round(temp.getJSONObject("attributes").getDouble("currentTemperature"));
            int col = WeatherWidget.tempColor(t);
            v.setImageViewBitmap(R.id.tempIcon, WeatherWidget.matrix(6, col, WeatherWidget.THERMO));
            v.setImageViewBitmap(R.id.tempVal, WeatherWidget.text(t + "°", col, 6));
        }
        if (hum != null) {
            v.setImageViewBitmap(R.id.humIcon, WeatherWidget.matrix(6, WeatherWidget.HUMID, WeatherWidget.WAVES));
            v.setImageViewBitmap(R.id.humVal, WeatherWidget.text(hum.getJSONObject("attributes").getInt("currentRH") + "%", WeatherWidget.HUMID, 6));
        }
        // status line stays hidden unless the air needs attention — then say what to do about it
        v.setViewVisibility(R.id.homeStatus, android.view.View.GONE);
        if (air != null) {
            JSONObject a = air.getJSONObject("attributes");
            int co2 = a.getInt("currentCO2"), pm = a.optInt("currentPM25");
            v.setImageViewBitmap(R.id.co2Icon, WeatherWidget.matrix(6, co2Color(co2), WeatherWidget.METER));
            v.setImageViewBitmap(R.id.co2Val, WeatherWidget.text(String.valueOf(co2), co2Color(co2), 6));
            v.setImageViewBitmap(R.id.pmIcon, WeatherWidget.matrix(6, pmColor(pm), DUST));
            v.setImageViewBitmap(R.id.pmVal, WeatherWidget.text(String.valueOf(pm), pmColor(pm), 6));
            if (co2 >= STUFFY) {
                v.setTextViewText(R.id.homeStatus, "● AIR STUFFY · OPEN A WINDOW");
                v.setTextColor(R.id.homeStatus, co2Color(co2));
                v.setViewVisibility(R.id.homeStatus, android.view.View.VISIBLE);
            }
        }

        JSONObject light = find(devices, "light", null);
        if (light == null) {
            v.setTextViewText(R.id.lightName, "NO LIGHT FOUND");
        } else {
            JSONObject a = light.getJSONObject("attributes");
            boolean on = a.optBoolean("isOn");
            int level = a.optInt("lightLevel"), active = activeMode(a), colour = lightColor(a);
            // brightness always shown: in the light's colour when on, dim when off (it comes back at this level)
            v.setImageViewBitmap(R.id.lightLevel, WeatherWidget.text(level + "%", on ? colour : 0xFF333333, 12));
            v.setTextViewText(R.id.lightName, a.optString("customName", "LIGHT").toUpperCase(Locale.ROOT));
            v.setTextViewText(R.id.lightMode, !on ? "OFF" : active >= 0 ? MODE_NAMES[active] : "CUSTOM");
            v.setTextColor(R.id.lightMode, on ? colour : GREY);
            v.setImageViewBitmap(R.id.powerIcon, WeatherWidget.matrix(6, on ? colour : 0xFF5C5C5C, POWER));
            v.setImageViewBitmap(R.id.levelBar, meter(on ? level : 0, colour));
            for (int i = 0; i < 4; i++) styleTile(v, i, i == active);
        }
    }

    /** Which mode the light is in right now, or -1. */
    static int activeMode(JSONObject a) {
        if (!a.optBoolean("isOn")) return -1;
        int level = a.optInt("lightLevel"), kelvin = a.optInt("colorTemperature");
        int lo = Math.min(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
        int hi = Math.max(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
        for (int i = 0; i < 4; i++)
            if (Math.abs(level - MODE_LEVELS[i]) <= 4 && (hi == 0 || Math.abs(kelvin - (MODE_COOL[i] ? hi : lo)) <= 200)) return i;
        return -1;
    }

    /** Preset tile: outlined in the mode's colour; solid with black text when it's the active preset. */
    static void styleTile(RemoteViews v, int i, boolean active) {
        int col = MODE_COLORS[i];
        v.setInt(MODE_IDS[i], "setBackgroundResource", active ? R.drawable.fill : R.drawable.outline);
        v.setColorStateList(MODE_IDS[i], "setBackgroundTintList", ColorStateList.valueOf(active ? col : (col & 0x00FFFFFF) | 0x99000000));
        v.setTextColor(MODE_LABEL_IDS[i], active ? Color.BLACK : col);
        v.setImageViewBitmap(MODE_ICON_IDS[i], WeatherWidget.matrix(6, active ? Color.BLACK : col, MODE_ICONS[i]));
    }

    /** The bulb's actual colour: amber at its warmest, cool white at its coolest. */
    static int lightColor(JSONObject a) {
        int lo = Math.min(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
        int hi = Math.max(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
        float t = hi > lo ? Math.max(0, Math.min(1, (a.optInt("colorTemperature") - lo) / (float) (hi - lo))) : 0;
        return (int) new android.animation.ArgbEvaluator().evaluate(t, 0xFFFFA552, 0xFFEEF3FF);
    }

    /** Row of 24 dots lit up to the brightness in the light's colour; the rest stay dim. */
    static android.graphics.Bitmap meter(int level, int colour) {
        int n = 24, w = 1200, h = 40;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        int lit = level <= 0 ? 0 : Math.max(1, Math.round(level / 100f * n));
        float step = w / (float) n;
        for (int i = 0; i < n; i++) {
            p.setColor(i < lit ? colour : 0xFF1F1F1F);
            c.drawCircle(step * (i + 0.5f), h / 2f, h * 0.3f, p);
        }
        return b;
    }

    /** First device of the type that has attribute `has` (if given) and lacks `without` (if given). */
    static JSONObject find(JSONArray devices, String type, String has, String... without) throws Exception {
        for (int i = 0; i < devices.length(); i++) {
            JSONObject d = devices.getJSONObject(i), a = d.optJSONObject("attributes");
            if (!type.equals(d.optString("deviceType")) || a == null) continue;
            if (has != null && !a.has(has)) continue;
            boolean excluded = false;
            for (String w : without) excluded |= a.has(w);
            if (!excluded) return d;
        }
        return null;
    }

    // ventilation guidance: fresh < 800, fine < 1000, stuffy < 1400, open a window beyond
    static int co2Color(int ppm) {
        return ppm < 800 ? 0xFF7BE495 : ppm < 1000 ? 0xFFC5E86C : ppm < 1400 ? 0xFFFF8A3D : WeatherWidget.RED;
    }

    // WHO-style PM2.5 bands (µg/m³)
    static int pmColor(int pm) {
        return pm <= 12 ? 0xFF7BE495 : pm <= 35 ? 0xFFFFD54F : pm <= 55 ? 0xFFFF8A3D : WeatherWidget.RED;
    }
}

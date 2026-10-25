package com.rohan.weatherwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.Manifest;
import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class WeatherWidget extends AppWidgetProvider {
    static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
            + "&current=temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m"
            + "&daily=temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,uv_index_max,precipitation_sum,sunrise,sunset"
            + "&hourly=precipitation_probability,precipitation,temperature_2m&timezone=auto&forecast_days=2";
    static final String AIR_URL = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.2f&longitude=%.2f&current=european_aqi";
    // all of Ireland; each warning lists affected counties by code
    static final String WARNINGS_URL = "https://www.met.ie/Open_Data/json/warning_IRELAND.json";
    // Met Éireann county codes (FIPS 10-4); Republic of Ireland only
    static final Map<String, String> COUNTIES = Map.ofEntries(
            Map.entry("carlow", "EI01"), Map.entry("cavan", "EI02"), Map.entry("clare", "EI03"), Map.entry("cork", "EI04"),
            Map.entry("donegal", "EI06"), Map.entry("dublin", "EI07"), Map.entry("galway", "EI10"), Map.entry("kerry", "EI11"),
            Map.entry("kildare", "EI12"), Map.entry("kilkenny", "EI13"), Map.entry("leitrim", "EI14"), Map.entry("laois", "EI15"),
            Map.entry("limerick", "EI16"), Map.entry("longford", "EI18"), Map.entry("louth", "EI19"), Map.entry("mayo", "EI20"),
            Map.entry("meath", "EI21"), Map.entry("monaghan", "EI22"), Map.entry("offaly", "EI23"), Map.entry("roscommon", "EI24"),
            Map.entry("sligo", "EI25"), Map.entry("tipperary", "EI26"), Map.entry("waterford", "EI27"), Map.entry("westmeath", "EI29"),
            Map.entry("wexford", "EI30"), Map.entry("wicklow", "EI31"));
    static final int HOURS = 12;
    static boolean is24h; // cached from the last clock draw, for sunrise/sunset formatting
    static final String TICK = "com.rohan.weatherwidget.TICK";
    static final ZoneId MUMBAI = ZoneId.of("Asia/Kolkata");
    static final int RED = 0xFFD71921, BLUE = 0xFF4FC3F7, HUMID = 0xFF4DD0E1, SUN_ORANGE = 0xFFFFA94D, GREY = 0xFF8C8C8C, DOT_GREY = 0xFFAAAAAA, OUTLINE = 0xFF262626;

    // 5x7 dot-matrix glyphs; '#' = lit dot
    static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            Map.entry('0', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
            Map.entry('1', new String[]{"  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "}),
            Map.entry('2', new String[]{" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"}),
            Map.entry('3', new String[]{" ### ", "#   #", "    #", "  ## ", "    #", "#   #", " ### "}),
            Map.entry('4', new String[]{"   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "}),
            Map.entry('5', new String[]{"#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "}),
            Map.entry('6', new String[]{" ### ", "#    ", "#    ", "#### ", "#   #", "#   #", " ### "}),
            Map.entry('7', new String[]{"#####", "    #", "   # ", "  #  ", "  #  ", "  #  ", "  #  "}),
            Map.entry('8', new String[]{" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "}),
            Map.entry('9', new String[]{" ### ", "#   #", "#   #", " ####", "    #", "    #", " ### "}),
            Map.entry('-', new String[]{"    ", "    ", "    ", "####", "    ", "    ", "    "}),
            Map.entry('.', new String[]{" ", " ", " ", " ", " ", " ", "#"}),
            Map.entry('°', new String[]{"###", "# #", "###", "   ", "   ", "   ", "   "}),
            Map.entry('%', new String[]{"##  #", "## # ", "   # ", "  #  ", " #   ", " # ##", "#  ##"}),
            Map.entry('A', new String[]{" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"}),
            Map.entry('B', new String[]{"#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "}),
            Map.entry('C', new String[]{" ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### "}),
            Map.entry('D', new String[]{"#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "}),
            Map.entry('E', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"}),
            Map.entry('F', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "}),
            Map.entry('G', new String[]{" ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ####"}),
            Map.entry('H', new String[]{"#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"}),
            Map.entry('I', new String[]{" ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "}),
            Map.entry('J', new String[]{"  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  "}),
            Map.entry('K', new String[]{"#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"}),
            Map.entry('L', new String[]{"#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"}),
            Map.entry('M', new String[]{"#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #"}),
            Map.entry('N', new String[]{"#   #", "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #"}),
            Map.entry('O', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
            Map.entry('P', new String[]{"#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "}),
            Map.entry('Q', new String[]{" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"}),
            Map.entry('R', new String[]{"#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"}),
            Map.entry('S', new String[]{" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "}),
            Map.entry('T', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "}),
            Map.entry('U', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
            Map.entry('V', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  "}),
            Map.entry('W', new String[]{"#   #", "#   #", "#   #", "# # #", "# # #", "# # #", " # # "}),
            Map.entry('X', new String[]{"#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"}),
            Map.entry('Y', new String[]{"#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "}),
            Map.entry('Z', new String[]{"#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"}),
            Map.entry(':', new String[]{" ", " ", "#", " ", "#", " ", " "}),
            Map.entry(' ', new String[]{"  ", "  ", "  ", "  ", "  ", "  ", "  "}));

    static final String[] SUN = {"    #    ", " #     # ", "   ###   ", "  #   #  ", "# #   # #", "  #   #  ", "   ###   ", " #     # ", "    #    "};
    static final String[] CLOUD = {"           ", "    ###    ", "  ##   #   ", " #      ## ", "#         #", "#         #", " ######### "};
    static final String[] PARTLY = {"  #        ", "#   #      ", "  ##  ###  ", " #  ##   # ", "   #      #", "  #       #", "   ####### "};
    static final String[] FOG = {"#########", "         ", " ####### ", "         ", "#########", "         ", "  #####  "};
    static final String[] CLOUD_TOP = {"    ###    ", "  ##   #   ", " #      ## ", "#         #", " ######### ", "           "};
    static final String[] RAIN = concat(CLOUD_TOP, "  #  #  #  ", " #  #  #   ");
    static final String[] SNOW = concat(CLOUD_TOP, " #   #   # ", "           ", "   #   #   ");
    static final String[] THUNDER = concat(CLOUD_TOP, "     ##    ", "    ##     ", "   ####    ", "    ##     ");
    static final String[] THERMO = {"   #   ", "  # #  ", "  # #  ", "  ###  ", "  ###  ", " ##### ", "  ###  "};
    static final String[] UV_SUN = {"   #   ", " #   # ", "  ###  ", "# ### #", "  ###  ", " #   # ", "   #   "};
    static final String[] UMBRELLA = {"  ###  ", " ##### ", "#######", "   #   ", "   #   ", " # #   ", "  #    "};
    static final String[] METER = {"      #", "      #", "    # #", "    # #", "  # # #", "  # # #", "# # # #"};
    static final String[] WIND = {"####   ", "    #  ", "#####  ", "       ", "###### ", "      #", "#####  "};
    static final String[] WAVES = {"       ", " ## ## ", "#  #  #", "       ", " ## ## ", "#  #  #", "       "};
    static final String[] SUNRISE = {"   #   ", " #   # ", "  ###  ", " ##### ", "#######", "       ", "#######"};
    static final String[] DROP = {"   #   ", "   #   ", "  ###  ", " ##### ", " ##### ", "  ###  ", "       "};

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!TICK.equals(a) && !Intent.ACTION_TIME_CHANGED.equals(a) && !Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            super.onReceive(c, i);
            return;
        }
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, WeatherWidget.class));
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        fillClock(c, v);
        m.partiallyUpdateAppWidget(ids, v);
        scheduleTick(c);
    }

    @Override
    public void onDisabled(Context c) {
        c.getSystemService(AlarmManager.class).cancel(tickIntent(c));
    }

    static PendingIntent tickIntent(Context c) {
        return PendingIntent.getBroadcast(c, 1, new Intent(c, WeatherWidget.class).setAction(TICK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // RTC (non-wakeup): ticks while the screen is on, catches up as soon as it wakes.
    // AllowWhileIdle so Battery Saver doesn't defer it (quota ~72/h, we use 60)
    static void scheduleTick(Context c) {
        long next = (System.currentTimeMillis() / 60_000 + 1) * 60_000 + 200; // just past the minute
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, tickIntent(c));
        else am.set(AlarmManager.RTC, next, tickIntent(c));
    }

    static void fillClock(Context c, RemoteViews v) {
        boolean h24 = is24h = DateFormat.is24HourFormat(c);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "hh:mm");
        ZonedDateTime here = ZonedDateTime.now(), mum = ZonedDateTime.now(MUMBAI);
        v.setImageViewBitmap(R.id.clock, text(here.format(hm), Color.WHITE, 20));
        v.setViewVisibility(R.id.ampm, h24 ? View.GONE : View.VISIBLE);
        if (!h24) v.setImageViewBitmap(R.id.ampm, text(fmt(here, "a"), DOT_GREY, 5));
        v.setImageViewBitmap(R.id.date, text(fmt(here, "EEE dd MMM yyyy"), DOT_GREY, 5));
        v.setImageViewBitmap(R.id.mumbaiLabel, text("MUMBAI", RED, 4));
        v.setImageViewBitmap(R.id.mumbaiClock, text(mum.format(hm), Color.WHITE, 9));
        v.setImageViewBitmap(R.id.mumbaiDate, text(fmt(mum, h24 ? "EEE dd" : "a EEE dd"), DOT_GREY, 4));
    }

    static String fmt(ZonedDateTime t, String pattern) {
        return t.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)).toUpperCase(Locale.ROOT);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        scheduleTick(c);
        PendingResult r = goAsync();
        new Thread(() -> {
            try { update(c, m, ids); } finally { r.finish(); }
        }).start();
    }

    static void update(Context c, AppWidgetManager m, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        Intent tap = new Intent(c, WeatherWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        PendingIntent refresh = PendingIntent.getBroadcast(c, 0, tap,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.root, refresh);
        fillClock(c, v);
        Place place = locate(c);
        v.setTextViewText(R.id.place, place.name.toUpperCase(Locale.ROOT));
        boolean warningShown;
        try {
            warningShown = place.county != null && fillWarnings(v, place.county); // no feed outside Ireland: stays hidden
        } catch (Exception e) {
            warningShown = true;
            v.setViewVisibility(R.id.warning, View.VISIBLE);
            v.setTextViewText(R.id.warning, "COULDN'T CHECK MET ÉIREANN WARNINGS");
            v.setTextColor(R.id.warning, GREY);
        }
        try {
            fillWeather(v, place, warningShown);
        } catch (Exception e) {
            // keep the last good forecast on screen, just flag the failure
            v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setOnClickPendingIntent(R.id.root, refresh);
            v.setTextViewText(R.id.updated, "Offline " + now() + " · tap to retry");
            m.partiallyUpdateAppWidget(ids, v);
            return;
        }
        v.setTextViewText(R.id.updated, "Updated " + now() + " · tap to refresh");
        m.updateAppWidget(ids, v);
    }

    static class Place {
        final double lat, lon;
        final String name, county; // county = Met Éireann code, null outside Ireland

        Place(double lat, double lon, String name, String county) {
            this.lat = lat; this.lon = lon; this.name = name; this.county = county;
        }
    }

    // city-level fix; falls back to the last place we resolved, then Dublin
    static Place locate(Context c) {
        SharedPreferences sp = c.getSharedPreferences("place", Context.MODE_PRIVATE);
        Place last = new Place(sp.getFloat("lat", 53.35f), sp.getFloat("lon", -6.26f),
                sp.getString("name", "Dublin"), sp.getString("county", "EI07"));
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return last;

        LocationManager lm = c.getSystemService(LocationManager.class);
        String provider = lm.hasProvider(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER : LocationManager.NETWORK_PROVIDER;
        Location loc = null;
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Location> got = new AtomicReference<>();
            lm.getCurrentLocation(provider, null, c.getMainExecutor(), l -> { got.set(l); done.countDown(); });
            done.await(8, TimeUnit.SECONDS);
            loc = got.get() != null ? got.get() : lm.getLastKnownLocation(provider);
        } catch (Exception ignored) {
        }
        if (loc == null) return last;

        String name = null, county = null;
        try {
            @SuppressWarnings("deprecation") // sync variant is fine: we're already off the main thread
            List<Address> found = new Geocoder(c, Locale.ENGLISH).getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            if (found != null && !found.isEmpty()) {
                Address a = found.get(0);
                name = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea() != null ? a.getSubAdminArea() : a.getAdminArea();
                if ("IE".equals(a.getCountryCode())) county = countyCode(a.getAdminArea(), a.getSubAdminArea(), a.getLocality());
            }
        } catch (Exception ignored) {
        }
        if (name == null) name = String.format(Locale.ROOT, "%.1f, %.1f", loc.getLatitude(), loc.getLongitude());
        name = name.replaceFirst("^(County|Co\\.) ", ""); // "County Dublin" -> "Dublin"
        Place p = new Place(loc.getLatitude(), loc.getLongitude(), name, county);
        sp.edit().putFloat("lat", (float) p.lat).putFloat("lon", (float) p.lon)
                .putString("name", p.name).putString("county", p.county).apply();
        return p;
    }

    // geocoder says "County Dublin", "Co. Cork", "Dublin" etc.
    static String countyCode(String... names) {
        for (String n : names) {
            if (n == null) continue;
            String k = n.toLowerCase(Locale.ROOT).replace("county", "").replace("co.", "").trim();
            if (COUNTIES.containsKey(k)) return COUNTIES.get(k);
        }
        return null;
    }

    static void fillWeather(RemoteViews v, Place place, boolean warningShown) throws Exception {
        JSONObject j = new JSONObject(get(String.format(Locale.ROOT, WEATHER_URL, place.lat, place.lon)));
        JSONObject cur = j.getJSONObject("current"), d = j.getJSONObject("daily"), h = j.getJSONObject("hourly");
        int code = cur.getInt("weather_code");

        long temp = round(cur.getDouble("temperature_2m"));
        v.setImageViewBitmap(R.id.icon, matrix(12, iconColor(code), icon(code)));
        v.setImageViewBitmap(R.id.temp, text(temp + "°", tempColor(temp), 14));
        v.setTextViewText(R.id.cond, describe(code));
        v.setTextViewText(R.id.feels, "Feels " + round(cur.getDouble("apparent_temperature")) + "°");
        v.setTextViewText(R.id.hilo, "H " + round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "°  L "
                + round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "°");

        long lo = round(d.getJSONArray("apparent_temperature_min").getDouble(0)), hi = round(d.getJSONArray("apparent_temperature_max").getDouble(0));
        // icons and values share 7-row grids at one pitch, so they render the same height
        v.setImageViewBitmap(R.id.feelsIcon, matrix(6, tempColor(hi), THERMO));
        v.setImageViewBitmap(R.id.feelsLo, text(lo + "°", tempColor(lo), 6));
        v.setImageViewBitmap(R.id.feelsHi, text(hi + "°", tempColor(hi), 6));
        double uv = d.getJSONArray("uv_index_max").getDouble(0);
        v.setImageViewBitmap(R.id.uvIcon, matrix(6, uvColor(uv), UV_SUN));
        v.setImageViewBitmap(R.id.uv, text(String.valueOf(round(uv)), uvColor(uv), 6));
        v.setTextViewText(R.id.uvLabel, "UV · " + uvLabel(uv).toUpperCase(Locale.ROOT));
        double rain = d.getJSONArray("precipitation_sum").getDouble(0);
        int rainColor = rain > 0 ? BLUE : GREY;
        v.setImageViewBitmap(R.id.rainIcon, matrix(6, rainColor, DROP));
        v.setImageViewBitmap(R.id.rainTotal, text(String.format(Locale.ROOT, "%.1f", rain), rainColor, 6));

        long wind = round(cur.getDouble("wind_speed_10m")), gust = round(cur.getDouble("wind_gusts_10m"));
        v.setImageViewBitmap(R.id.windIcon, matrix(6, windColor(gust), WIND));
        v.setImageViewBitmap(R.id.wind, text(wind + " " + compass(cur.getDouble("wind_direction_10m")), windColor(wind), 6));
        v.setTextViewText(R.id.windLabel, "WIND G" + gust);
        v.setImageViewBitmap(R.id.humidIcon, matrix(6, HUMID, WAVES));
        v.setImageViewBitmap(R.id.humidity, text(cur.getInt("relative_humidity_2m") + "%", HUMID, 6));
        // next sun event: today's sunrise until it's happened, then sunset, then tomorrow's sunrise
        String nowIso = cur.getString("time"), rise = d.getJSONArray("sunrise").getString(0), set = d.getJSONArray("sunset").getString(0);
        boolean beforeRise = nowIso.compareTo(rise) < 0, beforeSet = nowIso.compareTo(set) < 0;
        String sun = beforeRise ? rise : beforeSet ? set : d.getJSONArray("sunrise").getString(1);
        v.setTextViewText(R.id.sunLabel, beforeRise || !beforeSet ? "SUNRISE" : "SUNSET");
        v.setImageViewBitmap(R.id.sunIcon, matrix(6, SUN_ORANGE, SUNRISE));
        v.setImageViewBitmap(R.id.sunTime, text(sunTime(sun), SUN_ORANGE, 6));

        // air quality is a separate feed; a failure there shouldn't sink the forecast
        int aqi = -1;
        try {
            aqi = new JSONObject(get(String.format(Locale.ROOT, AIR_URL, place.lat, place.lon)))
                    .getJSONObject("current").getInt("european_aqi");
        } catch (Exception ignored) {
        }
        v.setImageViewBitmap(R.id.airIcon, matrix(6, aqiColor(aqi), METER));
        v.setImageViewBitmap(R.id.air, text(aqi < 0 ? "--" : String.valueOf(aqi), aqiColor(aqi), 6));
        v.setTextViewText(R.id.airLabel, "AIR · " + aqiLabel(aqi));

        JSONArray times = h.getJSONArray("time"), prob = h.getJSONArray("precipitation_probability"), mm = h.getJSONArray("precipitation"),
                temps = h.getJSONArray("temperature_2m");
        String hourNow = cur.getString("time").substring(0, 13);
        int start = 0;
        while (start < times.length() - 1 && !times.getString(start).startsWith(hourNow)) start++;
        int n = Math.min(HOURS, times.length() - start);
        String[] labels = new String[n];
        int[] p = new int[n];
        double[] amount = new double[n];
        long[] t = new long[n];
        for (int i = 0; i < n; i++) {
            int hr = Integer.parseInt(times.getString(start + i).substring(11, 13));
            labels[i] = i == 0 ? "NOW" : (hr % 12 == 0 ? 12 : hr % 12) + (hr < 12 ? "A" : "P");
            p[i] = prob.optInt(start + i);
            amount[i] = mm.optDouble(start + i, 0);
            t[i] = round(temps.optDouble(start + i, 0));
        }
        // first hour that's likely wet; "DRY" if none in the window
        int next = 0;
        while (next < n && p[next] < 50 && amount[next] < 0.2) next++;
        boolean dry = next == n;
        int nextColor = dry ? 0xFF7BE495 : BLUE;
        v.setTextViewText(R.id.nextLabel, dry ? "NEXT " + n + "H" : "NEXT RAIN");
        v.setImageViewBitmap(R.id.nextIcon, matrix(6, nextColor, UMBRELLA));
        v.setImageViewBitmap(R.id.nextRain, text(dry ? "DRY" : labels[next], nextColor, 6));
        v.setImageViewBitmap(R.id.rain, chart(labels, p, amount, t, warningShown ? 6 : 9));
    }

    static boolean fillWarnings(RemoteViews v, String county) throws Exception {
        JSONArray a = new JSONArray(get(WARNINGS_URL));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE h:mma", Locale.ENGLISH);
        OffsetDateTime now = OffsetDateTime.now();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String worst = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject w = a.getJSONObject(i);
            OffsetDateTime onset = OffsetDateTime.parse(w.getString("onset")), expiry = OffsetDateTime.parse(w.getString("expiry"));
            JSONArray regions = w.optJSONArray("regions"); // missing = nationwide
            if (expiry.isBefore(now) || (regions != null && !regions.toString().contains("\"" + county + "\""))) continue;
            String level = w.getString("level");
            if (rank(level) > rank(worst)) worst = level;
            if (sb.length() > 0) sb.append('\n');
            int dot = sb.length();
            sb.append("▲ ");
            sb.setSpan(new ForegroundColorSpan(levelColor(level)), dot, dot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            String line = level + " " + w.getString("type") + " warning";
            if (onset.isAfter(now)) line += " from " + onset.atZoneSameInstant(ZoneId.systemDefault()).format(f);
            line += " until " + expiry.atZoneSameInstant(ZoneId.systemDefault()).format(f);
            sb.append(line.toUpperCase(Locale.ROOT));
        }
        if (sb.length() == 0) return false; // stays hidden; the rain chart gets the space
        v.setViewVisibility(R.id.warning, View.VISIBLE);
        v.setTextViewText(R.id.warning, sb);
        v.setTextColor(R.id.warning, Color.WHITE);
        v.setColorStateList(R.id.warning, "setBackgroundTintList",
                ColorStateList.valueOf(worst == null ? OUTLINE : levelColor(worst)));
        return true;
    }

    // widgets ignore app fonts, so numbers and icons are drawn as dot grids
    static Bitmap matrix(int pitch, int color, String[]... glyphs) {
        int rows = glyphs[0].length, cols = -1;
        for (String[] g : glyphs) cols += g[0].length() + 1;
        Bitmap b = Bitmap.createBitmap(Math.max(cols, 1) * pitch, rows * pitch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        int x0 = 0;
        for (String[] g : glyphs) {
            for (int r = 0; r < rows; r++)
                for (int k = 0; k < g[r].length(); k++)
                    if (g[r].charAt(k) == '#') c.drawCircle(x0 + k * pitch + pitch / 2f, r * pitch + pitch / 2f, pitch * 0.4f, p);
            x0 += (g[0].length() + 1) * pitch;
        }
        return b;
    }

    static Bitmap text(String s, int color, int pitch) {
        List<String[]> g = new ArrayList<>();
        for (char ch : s.toCharArray()) if (GLYPHS.containsKey(ch)) g.add(GLYPHS.get(ch));
        return matrix(pitch, color, g.toArray(new String[0][]));
    }

    // each hour is a 3-dot-wide column; lit dots = chance of rain, deeper blue = likelier.
    // hourly temperature runs along the top, hour labels along the bottom
    static Bitmap chart(String[] labels, int[] p, double[] mm, long[] temps, int rows) {
        int w = 1150, top = 46, gridBottom = top + rows * 21, h = gridBottom + 44, cols = 3;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint();
        bg.setColor(Color.BLACK);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(24);

        float colW = (float) w / labels.length, pitch = (float) (gridBottom - top) / rows, r = pitch * 0.32f;
        for (int i = 0; i < labels.length; i++) {
            float cx = i * colW + colW / 2;
            int lit = p[i] == 0 ? 0 : Math.max(1, Math.round(p[i] / 100f * rows));
            int on = i == 0 ? RED : p[i] < 30 ? 0xFF2F6F99 : p[i] < 60 ? 0xFF3FA9F5 : 0xFF9AD8FF;
            if (mm[i] == 0 && i != 0) on = (on & 0x00FFFFFF) | 0x99000000; // dimmer when no measurable rain
            for (int row = 0; row < rows; row++) {
                dot.setColor(row < lit ? on : 0xFF1A1A1A);
                float y = gridBottom - pitch / 2 - row * pitch;
                for (int k = 0; k < cols; k++) c.drawCircle(cx + (k - 1) * pitch, y, r, dot);
            }
            if (p[i] >= 20 && lit < rows) {
                float y = gridBottom - lit * pitch - 6;
                c.drawRect(cx - colW / 2 + 4, y - 22, cx + colW / 2 - 4, y + 4, bg);
                text.setColor(i == 0 ? RED : Color.WHITE);
                c.drawText(p[i] + "%", cx, y, text);
            }
            text.setColor(tempColor(temps[i]));
            c.drawText(temps[i] + "°", cx, 28, text);
            text.setColor(i == 0 ? RED : GREY);
            c.drawText(labels[i], cx, h - 6, text);
        }
        return b;
    }

    static String[] concat(String[] a, String... b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream()) {
            return new String(in.readAllBytes());
        } finally {
            con.disconnect();
        }
    }

    static int rank(String level) {
        return level == null ? 0 : switch (level) { case "red" -> 3; case "orange" -> 2; case "yellow" -> 1; default -> 0; };
    }

    static int levelColor(String level) {
        return switch (level) { case "red" -> RED; case "orange" -> 0xFFFF8A00; default -> 0xFFFFD000; };
    }

    static int iconColor(int code) {
        if (code <= 1) return 0xFFFFC94D;          // sun
        if (code <= 3) return 0xFFC7CED6;          // cloud
        if (code <= 48) return 0xFF9E9E9E;         // fog
        if (code <= 67 || (code >= 80 && code <= 82)) return BLUE;
        if (code <= 86) return 0xFFE3F2FD;         // snow
        return 0xFFFFD54F;                         // thunder
    }

    // European AQI bands (-1 = unavailable)
    static int aqiColor(int aqi) {
        return aqi < 0 ? GREY : aqi <= 20 ? 0xFF7BE495 : aqi <= 40 ? 0xFFC5E86C : aqi <= 60 ? 0xFFFFD54F
                : aqi <= 80 ? 0xFFFF8A3D : aqi <= 100 ? RED : 0xFFC77DFF;
    }

    static String aqiLabel(int aqi) {
        return aqi < 0 ? "N/A" : aqi <= 20 ? "GOOD" : aqi <= 40 ? "FAIR" : aqi <= 60 ? "MOD" : aqi <= 80 ? "POOR" : aqi <= 100 ? "V POOR" : "EXTREME";
    }

    // roughly Beaufort: calm, breezy, strong, gale
    static int windColor(long kmh) {
        return kmh < 20 ? 0xFF7BE495 : kmh < 39 ? 0xFFFFD54F : kmh < 62 ? 0xFFFF8A3D : RED;
    }

    static String compass(double deg) {
        return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[(int) Math.round(deg / 45) % 8];
    }

    // Open-Meteo gives local ISO time ("2026-10-25T17:55"); show it in the user's 12/24h style
    static String sunTime(String iso) {
        return LocalTime.parse(iso.substring(11, 16)).format(DateTimeFormatter.ofPattern(is24h ? "HH:mm" : "h:mm"));
    }

    static int tempColor(long t) {
        return t <= 0 ? 0xFF9EC9FF : t <= 10 ? BLUE : t <= 18 ? 0xFF7BE495 : t <= 25 ? 0xFFFFD54F : t <= 32 ? 0xFFFF8A3D : RED;
    }

    // WHO UV index colours
    static int uvColor(double uv) {
        return uv < 3 ? 0xFF7BE495 : uv < 6 ? 0xFFFFD54F : uv < 8 ? 0xFFFF8A3D : uv < 11 ? RED : 0xFFC77DFF;
    }

    static String uvLabel(double uv) {
        return uv < 3 ? "Low" : uv < 6 ? "Moderate" : uv < 8 ? "High" : uv < 11 ? "Very high" : "Extreme";
    }

    // WMO weather codes used by Open-Meteo
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    static String[] icon(int code) {
        if (code <= 1) return SUN;
        if (code == 2) return PARTLY;
        if (code == 3) return CLOUD;
        if (code <= 48) return FOG;
        if (code <= 67 || (code >= 80 && code <= 82)) return RAIN;
        if (code <= 86) return SNOW;
        return THUNDER;
    }

    static long round(double d) { return Math.round(d); }

    static String now() { return LocalTime.now().format(DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH)); }
}

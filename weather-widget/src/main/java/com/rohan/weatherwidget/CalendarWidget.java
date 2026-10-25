package com.rohan.weatherwidget;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.RemoteViews;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Date poster in the weather widget's dot-matrix style: giant today, month grid with event dots.
// Tap a day for the day overlay, tap the date for the year overlay.
public class CalendarWidget extends AppWidgetProvider {
    static final String MIDNIGHT = "com.rohan.weatherwidget.CAL_MIDNIGHT", EXTRA_DAY = "day";
    static final int WHITE = Color.WHITE, GREY = 0xFF8C8C8C, DIM = 0xFF4A4A4A, RED = WeatherWidget.RED;

    /** One occurrence of an event. All-day events are stored at UTC midnight, so {@link #day} handles both kinds. */
    static class Event {
        final long id, begin, end;
        final boolean allDay;
        final int color;
        final String title, location;

        Event(long id, long begin, long end, boolean allDay, int color, String title, String location) {
            this.id = id; this.begin = begin; this.end = end; this.allDay = allDay; this.color = color;
            this.title = title; this.location = location;
        }

        LocalDate day(ZoneId zone) {
            return Instant.ofEpochMilli(begin).atZone(allDay ? ZoneOffset.UTC : zone).toLocalDate();
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        update(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle options) {
        update(c); // resized: refit the grid rows
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (MIDNIGHT.equals(i.getAction()) || Intent.ACTION_TIME_CHANGED.equals(i.getAction())
                || Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction())) update(c);
        else super.onReceive(c, i);
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static void update(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CalendarWidget.class));
        if (ids.length == 0) return;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        YearMonth month = YearMonth.from(today);
        boolean allowed = allowed(c);
        Map<LocalDate, List<Integer>> dots = new HashMap<>();
        Event next = null;
        if (allowed) {
            long now = System.currentTimeMillis();
            for (Event e : events(c, month.atDay(1), today.plusDays(7).isAfter(month.atEndOfMonth()) ? today.plusDays(7) : month.atEndOfMonth(), zone)) {
                LocalDate d = e.day(zone);
                if (YearMonth.from(d).equals(month)) dots.computeIfAbsent(d, k -> new ArrayList<>()).add(e.color);
                boolean ahead = e.allDay ? !d.isBefore(today) : e.end > now;
                if (next == null && ahead && !d.isAfter(today.plusDays(7))) next = e;
            }
        }

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.calendar_widget);
        v.setImageViewBitmap(R.id.calDate, WeatherWidget.text(String.valueOf(today.getDayOfMonth()), WHITE, 18));
        v.setImageViewBitmap(R.id.calWeekday, WeatherWidget.text(
                today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT), RED, 6));
        v.setImageViewBitmap(R.id.calMonth, WeatherWidget.text(
                month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT) + " " + month.getYear(), GREY, 6));
        v.setTextViewText(R.id.calToday, todayLine(allowed, dots.get(today)));
        v.setTextViewText(R.id.calNext, nextLine(next, today, zone));
        v.setImageViewBitmap(R.id.calWeekHeader, weekHeader(today));
        v.setOnClickPendingIntent(R.id.calHero, PendingIntent.getActivity(c, 1,
                allowed ? new Intent(c, YearActivity.class) : new Intent(c, SetupActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        // one cell per day; the template is filled in with the tapped day
        DayOfWeek first = firstDayOfWeek();
        int lead = (month.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
        int rows = (lead + month.lengthOfMonth() + 6) / 7;
        // rows share whatever height is left under the poster, so the month fills the widget
        // ponytail: sized from the first widget; several calendar widgets of different heights share it
        int heightDp = m.getAppWidgetOptions(ids[0]).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        int rowDp = heightDp > 0 ? Math.max(28, (heightDp - 112) / rows) : 30;
        RemoteViews.RemoteCollectionItems.Builder cells = new RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1);
        for (int i = 0; i < lead; i++) {
            RemoteViews blank = new RemoteViews(c.getPackageName(), R.layout.calendar_cell);
            blank.setViewLayoutHeight(R.id.cell, rowDp, android.util.TypedValue.COMPLEX_UNIT_DIP);
            cells.addItem(-1 - i, blank);
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate d = month.atDay(day);
            RemoteViews cell = new RemoteViews(c.getPackageName(), R.layout.calendar_cell);
            cell.setViewLayoutHeight(R.id.cell, rowDp, android.util.TypedValue.COMPLEX_UNIT_DIP);
            cell.setImageViewBitmap(R.id.cellImage, cell(d, today, dots.get(d)));
            cell.setOnClickFillInIntent(R.id.cell, new Intent().putExtra(EXTRA_DAY, d.toEpochDay()));
            cells.addItem(d.toEpochDay(), cell);
        }
        v.setRemoteAdapter(R.id.calGrid, cells.setHasStableIds(true).build());
        v.setPendingIntentTemplate(R.id.calGrid, PendingIntent.getActivity(c, 2,
                allowed ? new Intent(c, DayActivity.class) : new Intent(c, SetupActivity.class),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)); // mutable: each cell fills in its day
        m.updateAppWidget(ids, v);
        scheduleMidnight(c, zone, today);
    }

    /** The date is the hero, so redraw just after midnight rather than waiting for the 30-min refresh. */
    static void scheduleMidnight(Context c, ZoneId zone, LocalDate today) {
        long at = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 5_000;
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, CalendarWidget.class).setAction(MIDNIGHT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi);
        else am.set(AlarmManager.RTC, at, pi);
    }

    static CharSequence todayLine(boolean allowed, List<Integer> colors) {
        if (!allowed) return "TAP TO ALLOW CALENDAR";
        if (colors == null || colors.isEmpty()) return "○ FREE DAY";
        SpannableStringBuilder sb = new SpannableStringBuilder("● " + colors.size() + (colors.size() == 1 ? " EVENT" : " EVENTS"));
        sb.setSpan(new ForegroundColorSpan(colors.get(0)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** "WED · 09:00" then the title, dot in the event's calendar colour. */
    static CharSequence nextLine(Event e, LocalDate today, ZoneId zone) {
        if (e == null) return "";
        LocalDate d = e.day(zone);
        String when = (d.equals(today) ? "TODAY" : d.equals(today.plusDays(1)) ? "TMRW"
                : d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT))
                + " · " + (e.allDay ? "ALL DAY" : Instant.ofEpochMilli(e.begin).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        SpannableStringBuilder sb = new SpannableStringBuilder("● " + when + "\n");
        sb.setSpan(new ForegroundColorSpan(e.color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(GREY), 2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(e.title == null || e.title.isBlank() ? "(No title)" : e.title);
        return sb;
    }

    // weeks start on Sunday (widget, day-view strip and year view all use this)
    static DayOfWeek firstDayOfWeek() {
        return DayOfWeek.SUNDAY;
    }

    /** Visible event occurrences overlapping [fromDay, toDay], in start order. */
    static List<Event> events(Context c, LocalDate fromDay, LocalDate toDay, ZoneId zone) {
        List<Event> out = new ArrayList<>();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        // pad a day each side: all-day events sit at UTC midnight, which can fall outside local bounds
        ContentUris.appendId(b, fromDay.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
        ContentUris.appendId(b, toDay.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli());
        String[] cols = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.TITLE, CalendarContract.Instances.EVENT_LOCATION};
        try (Cursor cur = c.getContentResolver().query(b.build(), cols, CalendarContract.Instances.VISIBLE + "=1",
                null, CalendarContract.Instances.BEGIN)) {
            while (cur != null && cur.moveToNext()) {
                Event e = new Event(cur.getLong(0), cur.getLong(1), cur.getLong(2), cur.getInt(3) == 1,
                        cur.getInt(4) | 0xFF000000, cur.getString(5), cur.getString(6));
                LocalDate d = e.day(zone);
                if (!d.isBefore(fromDay) && !d.isAfter(toDay)) out.add(e);
            }
        }
        return out;
    }

    static Map<LocalDate, List<Integer>> colorsByDay(Context c, LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, List<Integer>> out = new HashMap<>();
        for (Event e : events(c, from, to, zone)) out.computeIfAbsent(e.day(zone), k -> new ArrayList<>()).add(e.color);
        return out;
    }

    static Bitmap weekHeader(LocalDate today) {
        int w = 1050, cellW = w / 7;
        Bitmap b = Bitmap.createBitmap(w, 40, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(26);
        DayOfWeek first = firstDayOfWeek();
        for (int col = 0; col < 7; col++) {
            DayOfWeek dow = first.plus(col);
            text.setColor(dow == today.getDayOfWeek() ? RED : DIM);
            c.drawText(dow.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), col * cellW + cellW / 2f, 30, text);
        }
        return b;
    }

    /** One day: dot-matrix number, red block for today, up to three event dots underneath. */
    static Bitmap cell(LocalDate d, LocalDate today, List<Integer> colors) {
        int w = 150, h = 100;
        float cx = w / 2f;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean isToday = d.equals(today), weekend = d.getDayOfWeek().getValue() >= 6;
        int color = isToday ? WHITE : d.isBefore(today) ? DIM : weekend ? GREY : WHITE;
        if (isToday) {
            fill.setColor(RED);
            c.drawRoundRect(cx - 46, 6, cx + 46, 64, 12, 12, fill);
        }
        Bitmap num = WeatherWidget.text(String.valueOf(d.getDayOfMonth()), color, 5);
        c.drawBitmap(num, cx - num.getWidth() / 2f, 35 - num.getHeight() / 2f, null);
        if (colors != null) {
            int n = Math.min(3, colors.size());
            for (int k = 0; k < n; k++) {
                fill.setColor(d.isBefore(today) ? (colors.get(k) & 0x00FFFFFF) | 0x66000000 : colors.get(k));
                c.drawCircle(cx + (k - (n - 1) / 2f) * 16, 82, 5, fill);
            }
        }
        return b;
    }
}

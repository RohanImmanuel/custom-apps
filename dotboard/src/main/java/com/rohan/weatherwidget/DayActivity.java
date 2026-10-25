package com.rohan.weatherwidget;

import android.content.ContentUris;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

// Day overlay: big date, that day's events, swipe or arrows to move between days.
public class DayActivity extends CardOverlay {
    final ZoneId zone = ZoneId.systemDefault();
    LocalDate day;
    GestureDetector swipe;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        day = LocalDate.ofEpochDay(getIntent().getLongExtra(CalendarWidget.EXTRA_DAY, LocalDate.now(zone).toEpochDay()));
        swipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vx) < Math.abs(vy) || Math.abs(vx) < 600) return false;
                show(day.plusDays(vx < 0 ? 1 : -1));
                return true;
            }
        });
        show(day);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        return swipe.onTouchEvent(e) || super.dispatchTouchEvent(e);
    }

    void show(LocalDate d) {
        day = d;
        card.removeAllViews();
        LocalDate today = LocalDate.now(zone);

        // header: big dot date | weekday + month | arrows
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView big = new ImageView(this);
        big.setImageBitmap(WeatherWidget.text(String.valueOf(d.getDayOfMonth()), CardOverlay.WHITE, 14));
        big.setAdjustViewBounds(true);
        head.addView(big, new LinearLayout.LayoutParams(-2, px(52)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(16), 0, 0, 0);
        ImageView weekday = new ImageView(this);
        weekday.setImageBitmap(WeatherWidget.text(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                .toUpperCase(Locale.ROOT), d.equals(today) ? WeatherWidget.RED : CardOverlay.WHITE, 6));
        weekday.setAdjustViewBounds(true);
        words.addView(weekday, new LinearLayout.LayoutParams(-2, px(14)));
        TextView month = label(d.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)).toUpperCase(Locale.ROOT)
                + (d.equals(today) ? " · TODAY" : ""), 10, CardOverlay.GREY);
        month.setPadding(0, px(8), 0, 0);
        words.addView(month);
        head.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(arrow(true, () -> show(day.minusDays(1))));
        head.addView(arrow(false, () -> show(day.plusDays(1))));
        card.addView(head);

        // the week around this day: tap to jump, dots show which days are busy
        LocalDate weekStart = d.minusDays((d.getDayOfWeek().getValue() - CalendarWidget.firstDayOfWeek().getValue() + 7) % 7);
        WeekStrip strip = new WeekStrip(weekStart, d, today, CalendarWidget.colorsByDay(this, weekStart, weekStart.plusDays(6), zone));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, px(64));
        wp.topMargin = px(16);
        card.addView(strip, wp);

        // events
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<CalendarWidget.Event> events = CalendarWidget.events(this, d, d, zone);
        if (events.isEmpty()) {
            TextView free = label("○ FREE DAY", 12, CardOverlay.GREY);
            free.setPadding(0, px(20), 0, px(20));
            list.addView(free);
        }
        for (CalendarWidget.Event e : events) list.addView(eventRow(e));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = px(16);
        card.addView(scroll, sp);
        scroll.post(() -> { // cap the list so the card never fills the whole screen
            int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.45f);
            if (scroll.getHeight() > max) { scroll.getLayoutParams().height = max; scroll.requestLayout(); }
        });

        // footer: year view | new event
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView year = label("YEAR", 11, CardOverlay.GREY);
        year.setPadding(0, px(12), px(12), px(12));
        year.setOnClickListener(v -> startActivity(new Intent(this, YearActivity.class).putExtra("year", day.getYear())));
        foot.addView(year, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = label("+ NEW EVENT", 11, CardOverlay.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setStroke(px(1), CardOverlay.LINE);
        pill.setCornerRadius(px(100));
        add.setBackground(pill);
        add.setPadding(px(14), px(9), px(14), px(9));
        add.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli())));
        foot.addView(add);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = px(12);
        card.addView(foot, fp);
    }

    /** Seven columns: weekday initial, day number, event dots. Selected day on white, today in red. */
    class WeekStrip extends View {
        final LocalDate start, selected, today;
        final java.util.Map<LocalDate, List<Integer>> colors;
        final android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        WeekStrip(LocalDate start, LocalDate selected, LocalDate today, java.util.Map<LocalDate, List<Integer>> colors) {
            super(DayActivity.this);
            this.start = start; this.selected = selected; this.today = today; this.colors = colors;
            text.setTypeface(android.graphics.Typeface.MONOSPACE);
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float colW = getWidth() / 7f;
            for (int i = 0; i < 7; i++) {
                LocalDate d = start.plusDays(i);
                float cx = (i + 0.5f) * colW;
                boolean sel = d.equals(selected), isToday = d.equals(today);
                if (sel || isToday) {
                    fill.setColor(sel ? CardOverlay.WHITE : 0xFF2A0A0C);
                    c.drawRoundRect(cx - colW * 0.42f, 0, cx + colW * 0.42f, getHeight(), px(12), px(12), fill);
                }
                int ink = sel ? 0xFF000000 : isToday ? WeatherWidget.RED : d.isBefore(today) ? CardOverlay.DIM : CardOverlay.WHITE;
                text.setColor(sel ? 0xFF000000 : isToday ? WeatherWidget.RED : CardOverlay.GREY);
                text.setTextSize(px(10));
                c.drawText(d.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.ENGLISH), cx, px(16), text);
                text.setColor(ink);
                text.setTextSize(px(17));
                c.drawText(String.valueOf(d.getDayOfMonth()), cx, px(39), text);
                List<Integer> ev = colors.get(d);
                if (ev != null) {
                    int n = Math.min(3, ev.size());
                    for (int k = 0; k < n; k++) {
                        fill.setColor(sel ? 0xFF000000 : ev.get(k));
                        c.drawCircle(cx + (k - (n - 1) / 2f) * px(7), px(52), px(2.2f), fill);
                    }
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP)
                show(start.plusDays(Math.max(0, Math.min(6, (int) (e.getX() / (getWidth() / 7f))))));
            return true;
        }
    }

    /** Colour bar · time · title / location. Tap to open it in the calendar app. */
    View eventRow(CalendarWidget.Event e) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, px(12), 0, px(12));
        View bar = new View(this);
        GradientDrawable bd = new GradientDrawable();
        bd.setColor(e.color);
        bd.setCornerRadius(px(2));
        bar.setBackground(bd);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(px(4), -1);
        bp.rightMargin = px(14);
        row.addView(bar, bp);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        String time = e.allDay ? "ALL DAY" : Instant.ofEpochMilli(e.begin).atZone(zone).format(hm)
                + " – " + Instant.ofEpochMilli(e.end).atZone(zone).format(hm);
        text.addView(label(time, 11, CardOverlay.GREY));
        TextView title = new TextView(this);
        title.setText(e.title == null || e.title.isBlank() ? "(No title)" : e.title);
        title.setTextColor(CardOverlay.WHITE);
        title.setTextSize(18);
        title.setPadding(0, px(2), 0, 0);
        text.addView(title);
        if (e.location != null && !e.location.isBlank()) {
            TextView where = label(e.location, 11, CardOverlay.GREY);
            where.setMaxLines(1);
            text.addView(where);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)));
        return row;
    }
}

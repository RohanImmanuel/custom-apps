package com.rohan.weatherwidget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Year overlay: twelve mini months; event days carry a dot in their calendar colour. Tap a day for its day view.
public class YearActivity extends CardOverlay {
    final ZoneId zone = ZoneId.systemDefault();
    int year;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        year = getIntent().getIntExtra("year", LocalDate.now(zone).getYear());
        card.setPadding(px(14), px(18), px(14), px(14));
        ((android.widget.FrameLayout.LayoutParams) card.getLayoutParams()).leftMargin = px(8);
        ((android.widget.FrameLayout.LayoutParams) card.getLayoutParams()).rightMargin = px(8);
        show();
    }

    void show() {
        card.removeAllViews();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView title = new ImageView(this);
        title.setImageBitmap(WeatherWidget.text(String.valueOf(year), CardOverlay.WHITE, 10));
        title.setAdjustViewBounds(true);
        head.addView(title, new LinearLayout.LayoutParams(-2, px(26)));
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        head.addView(arrow(true, () -> { year--; show(); }));
        head.addView(arrow(false, () -> { year++; show(); }));
        card.addView(head);

        Map<LocalDate, List<Integer>> colors = CalendarWidget.allowed(this)
                ? CalendarWidget.colorsByDay(this, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), zone) : new HashMap<>();
        YearView grid = new YearView(this, year, LocalDate.now(zone), colors);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = px(14);
        card.addView(grid, gp);
    }

    /** 3 × 4 mini months drawn on one canvas; maps taps back to a date. */
    class YearView extends View {
        final int year;
        final LocalDate today;
        final Map<LocalDate, List<Integer>> colors;
        final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final DayOfWeek first = CalendarWidget.firstDayOfWeek();
        float cellW, cellH, monthW, monthH, top;

        YearView(Context c, int year, LocalDate today, Map<LocalDate, List<Integer>> colors) {
            super(c);
            this.year = year; this.today = today; this.colors = colors;
            text.setTypeface(Typeface.MONOSPACE);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            monthW = w / 3f;
            cellW = (monthW - px(6)) / 7f;
            cellH = cellW * 0.95f;
            top = px(17); // month label
            monthH = top + 6 * cellH + px(6);
            setMeasuredDimension(w, (int) (4 * monthH));
        }

        @Override
        protected void onDraw(Canvas c) {
            for (int m = 0; m < 12; m++) {
                YearMonth ym = YearMonth.of(year, m + 1);
                float ox = (m % 3) * monthW, oy = (m / 3) * monthH;
                boolean current = ym.equals(YearMonth.from(today));
                text.setTextSize(px(11));
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(current ? WeatherWidget.RED : CardOverlay.GREY);
                c.drawText(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT), ox + px(2), oy + px(11), text);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(px(10f));
                int lead = (ym.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
                for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                    LocalDate date = ym.atDay(d);
                    int idx = lead + d - 1;
                    float cx = ox + (idx % 7 + 0.5f) * cellW, cy = oy + top + (idx / 7 + 0.5f) * cellH;
                    if (date.equals(today)) {
                        fill.setColor(WeatherWidget.RED);
                        c.drawCircle(cx, cy - px(1), cellW * 0.48f, fill);
                    }
                    List<Integer> ev = colors.get(date);
                    boolean past = date.isBefore(today);
                    text.setColor(date.equals(today) ? CardOverlay.WHITE : past ? CardOverlay.DIM : ev != null ? CardOverlay.WHITE : CardOverlay.GREY);
                    c.drawText(String.valueOf(d), cx, cy + px(3.5f), text);
                    if (ev != null && !date.equals(today)) {
                        fill.setColor(past ? (ev.get(0) & 0x00FFFFFF) | 0x66000000 : ev.get(0));
                        c.drawCircle(cx, cy + cellH * 0.42f, px(1.6f), fill);
                    }
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            int m = (int) (e.getY() / monthH) * 3 + (int) (e.getX() / monthW);
            if (m < 0 || m > 11) return true;
            YearMonth ym = YearMonth.of(year, m + 1);
            float lx = e.getX() - (m % 3) * monthW, ly = e.getY() - (m / 3) * monthH - top;
            if (ly < 0 || lx < 0) return true;
            int idx = (int) (ly / cellH) * 7 + (int) (lx / cellW);
            int lead = (ym.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7, d = idx - lead + 1;
            if (d >= 1 && d <= ym.lengthOfMonth())
                startActivity(new Intent(getContext(), DayActivity.class).putExtra(CalendarWidget.EXTRA_DAY, ym.atDay(d).toEpochDay()));
            return true;
        }
    }
}

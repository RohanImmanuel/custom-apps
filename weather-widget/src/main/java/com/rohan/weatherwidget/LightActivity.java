package com.rohan.weatherwidget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Light overlay: power, big brightness, warmth, and the four modes. Sends a change when a slider is let go.
public class LightActivity extends CardOverlay {
    static final int WARM = HomeWidget.WARM;
    final ExecutorService net = Executors.newSingleThreadExecutor(); // hub calls in order, off the UI thread
    String id;
    boolean on;
    int level, kelvin, lo, hi;
    ImageView bigLevel;
    TextView power;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView loading = label("CONNECTING TO HUB…", 11, CardOverlay.GREY);
        card.addView(loading);
        net.execute(() -> {
            try {
                JSONObject light = HomeWidget.find(HomeHub.devices(this), "light", null);
                if (light == null) { runOnUiThread(() -> loading.setText("NO LIGHT FOUND")); return; }
                JSONObject a = light.getJSONObject("attributes");
                id = light.getString("id");
                on = a.optBoolean("isOn");
                level = a.optInt("lightLevel", 100);
                kelvin = a.optInt("colorTemperature");
                lo = Math.min(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
                hi = Math.max(a.optInt("colorTemperatureMin"), a.optInt("colorTemperatureMax"));
                String name = a.optString("customName", "Light");
                runOnUiThread(() -> build(name));
            } catch (Exception e) {
                runOnUiThread(() -> loading.setText("HUB UNREACHABLE · ARE YOU ON HOME WI-FI?"));
            }
        });
    }

    void build(String name) {
        card.removeAllViews();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(label(name.toUpperCase(Locale.ROOT), 13, CardOverlay.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        power = label("", 12, CardOverlay.WHITE);
        power.setPadding(px(18), px(10), px(18), px(10));
        power.setOnClickListener(v -> { on = !on; styleAll(); send("isOn", on, true); });
        head.addView(power);
        card.addView(head);

        bigLevel = new ImageView(this);
        bigLevel.setAdjustViewBounds(true);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, px(46));
        bl.topMargin = px(22);
        card.addView(bigLevel, bl);

        section("BRIGHTNESS");
        Slider bright = new Slider(this, null, level / 100f);
        // live while dragging (throttled so the hub isn't flooded), and once more on release
        bright.onMove = f -> {
            level = Math.max(1, Math.round(f * 100));
            styleAll();
            if (live()) { if (!on) { on = true; styleAll(); send("isOn", true, false); } send("lightLevel", level, false); }
        };
        bright.onRelease = f -> { if (!on) { on = true; styleAll(); send("isOn", true, false); } send("lightLevel", level, true); };
        card.addView(bright, new LinearLayout.LayoutParams(-1, px(48)));

        if (hi > lo) {
            section("WARMTH");
            Slider warmth = new Slider(this, new int[]{0xFFFF9E45, 0xFFFFD9A8, 0xFFEEF3FF}, (kelvin - lo) / (float) (hi - lo));
            warmth.onMove = f -> { kelvin = Math.round(lo + f * (hi - lo)); if (live()) send("colorTemperature", kelvin, false); };
            warmth.onRelease = f -> send("colorTemperature", kelvin, true);
            card.addView(warmth, new LinearLayout.LayoutParams(-1, px(48)));
        }

        section("MODES");
        LinearLayout modes = new LinearLayout(this);
        for (int i = 0; i < 4; i++) {
            int m = i;
            TextView tile = label(HomeWidget.MODE_NAMES[i], 10, HomeWidget.MODE_COLORS[i]);
            tile.setGravity(Gravity.CENTER);
            tile.setTag("mode" + i);
            tile.setOnClickListener(v -> applyMode(m));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, px(44), 1);
            if (i > 0) tp.leftMargin = px(6);
            modes.addView(tile, tp);
        }
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = px(8);
        card.addView(modes, mp);
        styleAll();
    }

    void section(String s) {
        TextView t = label(s, 10, CardOverlay.GREY);
        t.setPadding(0, px(20), 0, px(8));
        card.addView(t);
    }

    void applyMode(int m) {
        on = true;
        level = HomeWidget.MODE_LEVELS[m];
        if (hi > lo) kelvin = HomeWidget.MODE_COOL[m] ? hi : lo;
        build(nameOf());
        net.execute(() -> {
            HomeWidget.act(this, m);
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            HomeWidget.refresh(this);
        });
    }

    String nameOf() {
        return ((TextView) ((LinearLayout) card.getChildAt(0)).getChildAt(0)).getText().toString();
    }

    /** Power pill, big percentage and the active mode ring all follow the local state. */
    void styleAll() {
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(px(100));
        if (on) pill.setColor(WARM); else pill.setStroke(px(1), CardOverlay.LINE);
        power.setBackground(pill);
        power.setText(on ? "ON" : "OFF");
        power.setTextColor(on ? 0xFF000000 : CardOverlay.GREY);
        bigLevel.setImageBitmap(WeatherWidget.text(level + "%", on ? WARM : CardOverlay.DIM, 12));
        JSONObject a = new JSONObject();
        try {
            a.put("isOn", on).put("lightLevel", level).put("colorTemperature", kelvin)
                    .put("colorTemperatureMin", lo).put("colorTemperatureMax", hi);
        } catch (Exception ignored) {
        }
        int active = HomeWidget.activeMode(a);
        // same tiles as the widget: outlined in the preset colour, solid with black text when active
        for (int i = 0; i < 4; i++) {
            View v = card.findViewWithTag("mode" + i);
            if (v == null) continue;
            int col = HomeWidget.MODE_COLORS[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(16));
            if (i == active) bg.setColor(col);
            else bg.setStroke(px(1), (col & 0x00FFFFFF) | 0x99000000); // outlined, like the widget
            v.setBackground(bg);
            ((TextView) v).setTextColor(i == active ? 0xFF000000 : col);
        }
    }

    long lastLive;

    /** True about three times a second while dragging. */
    boolean live() {
        long now = System.currentTimeMillis();
        if (now - lastLive < 300) return false;
        lastLive = now;
        return true;
    }

    void send(String attribute, Object value, boolean refreshWidget) {
        net.execute(() -> {
            try {
                HomeHub.set(this, id, attribute, value);
                if (refreshWidget) {
                    Thread.sleep(1500); // the hub reports the light's new state a moment after accepting it
                    HomeWidget.refresh(this); // keep the widget in step once the change settles
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        net.shutdown(); // let queued changes finish, then stop
        super.onDestroy();
    }

    /** Rounded track you drag across: a warm fill (brightness) or a colour gradient (warmth). */
    class Slider extends View {
        final int[] gradient;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF track = new RectF();
        float value;
        java.util.function.Consumer<Float> onMove = f -> {}, onRelease = f -> {};

        Slider(Context c, int[] gradient, float value) {
            super(c);
            this.gradient = gradient;
            this.value = Math.max(0, Math.min(1, value));
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), r = h / 2, x = r + value * (getWidth() - h);
            track.set(0, 0, getWidth(), h);
            if (gradient != null) {
                paint.setShader(new LinearGradient(0, 0, getWidth(), 0, gradient, null, Shader.TileMode.CLAMP));
                c.drawRoundRect(track, r, r, paint);
                paint.setShader(null);
            } else {
                paint.setColor(0xFF1A1A1A);
                c.drawRoundRect(track, r, r, paint);
                paint.setColor(on ? WARM : 0xFF4A4A4A);
                track.set(0, 0, x + r, h);
                c.drawRoundRect(track, r, r, paint);
            }
            paint.setColor(0xFF000000);
            c.drawCircle(x, r, r * 0.78f, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(x, r, r * 0.62f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float h = getHeight();
            value = Math.max(0, Math.min(1, (e.getX() - h / 2) / (getWidth() - h)));
            onMove.accept(value);
            invalidate();
            if (e.getAction() == MotionEvent.ACTION_UP) onRelease.accept(value);
            return true;
        }
    }
}

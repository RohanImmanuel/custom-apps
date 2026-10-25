package com.rohan.linkrouter;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

// Animated arc reactor: counter-rotating rings, pulsing core, optional countdown arc.
public class ReactorView extends View {
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF oval = new RectF();
    final Path tri = new Path();
    final ValueAnimator clock = ValueAnimator.ofFloat(0, 1);
    int accent = HudColors.CYAN;
    float progress = -1;   // countdown arc, 0..1; -1 hides it
    float boot = 1;        // 0..1 scale for the power-up animation
    float charge;          // 0..1 hold-to-engage ring; also spins the rings faster
    int chargeColor = HudColors.RED;
    float flash;           // 1..0 white burst when a hold completes
    float angle;           // ring rotation in degrees, advanced per frame
    long lastFrame;

    public ReactorView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        clock.setDuration(24_000);
        clock.setRepeatCount(ValueAnimator.INFINITE);
        clock.setInterpolator(new LinearInterpolator());
        clock.addUpdateListener(a -> {
            long now = System.nanoTime();
            if (lastFrame != 0) angle += (now - lastFrame) / 1e9f * 15f * (1 + 5 * charge); // 15°/s idle, faster while charging
            lastFrame = now;
            invalidate();
        });
    }

    void burst() {
        ValueAnimator a = ValueAnimator.ofFloat(1, 0);
        a.setDuration(500);
        a.addUpdateListener(v -> flash = (float) v.getAnimatedValue());
        a.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        clock.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        clock.cancel(); // no drawing work while off screen
        lastFrame = 0;  // so the rings don't jump when it comes back
        super.onDetachedFromWindow();
    }

    void setAccent(int color) {
        ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), accent, color);
        a.setDuration(600);
        a.addUpdateListener(v -> accent = (int) v.getAnimatedValue());
        a.start();
    }

    void setProgress(float p) {
        progress = p;
    }

    void powerUp() {
        ValueAnimator a = ValueAnimator.ofFloat(0, 1);
        a.setDuration(900);
        a.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
        a.addUpdateListener(v -> boot = (float) v.getAnimatedValue());
        a.start();
    }

    int alpha(int color, float a) {
        return Color.argb(Math.round(255 * Math.max(0, Math.min(1, a))), Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float R = Math.min(cx, cy) * 0.94f * boot;
        if (R <= 0) return;
        float t = angle;
        float pulse = (float) (0.5 + 0.5 * Math.sin(Math.toRadians(clock.getAnimatedFraction() * 360f * 6)));
        float d = getResources().getDisplayMetrics().density;

        // halo
        fill.setShader(new RadialGradient(cx, cy, R, alpha(accent, 0.28f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, fill);
        fill.setShader(null);

        // outer tick ring, slow clockwise
        c.save();
        c.rotate(t * 0.5f, cx, cy);
        for (int i = 0; i < 72; i++) {
            boolean major = i % 6 == 0;
            stroke.setStrokeWidth((major ? 2f : 1f) * d);
            stroke.setColor(alpha(accent, major ? 0.9f : 0.45f));
            float r1 = R * 0.96f, r2 = r1 - R * (major ? 0.09f : 0.045f);
            double a = Math.toRadians(i * 5);
            c.drawLine(cx + r1 * (float) Math.cos(a), cy + r1 * (float) Math.sin(a),
                    cx + r2 * (float) Math.cos(a), cy + r2 * (float) Math.sin(a), stroke);
        }
        c.restore();

        // countdown arc, drained clockwise from the top (with a soft wide underlay for glow)
        if (progress >= 0) {
            oval.set(cx - R * 0.84f, cy - R * 0.84f, cx + R * 0.84f, cy + R * 0.84f);
            stroke.setStrokeWidth(14 * d);
            stroke.setColor(alpha(accent, 0.18f));
            c.drawArc(oval, -90, 360 * progress, false, stroke);
            stroke.setStrokeWidth(5 * d);
            stroke.setColor(accent);
            c.drawArc(oval, -90, 360 * progress, false, stroke);
        }

        // hold-to-engage charge, filling clockwise from the top just inside the ticks
        if (charge > 0) {
            oval.set(cx - R * 0.88f, cy - R * 0.88f, cx + R * 0.88f, cy + R * 0.88f);
            stroke.setStrokeWidth(16 * d);
            stroke.setColor(alpha(chargeColor, 0.2f));
            c.drawArc(oval, -90, 360 * charge, false, stroke);
            stroke.setStrokeWidth(6 * d);
            stroke.setColor(chargeColor);
            c.drawArc(oval, -90, 360 * charge, false, stroke);
        }

        // thin ring
        stroke.setStrokeWidth(1.5f * d);
        stroke.setColor(alpha(accent, 0.55f));
        c.drawCircle(cx, cy, R * 0.76f, stroke);

        // three arc segments, counter-clockwise
        oval.set(cx - R * 0.68f, cy - R * 0.68f, cx + R * 0.68f, cy + R * 0.68f);
        stroke.setStrokeWidth(4 * d);
        stroke.setColor(alpha(accent, 0.85f));
        for (int i = 0; i < 3; i++) c.drawArc(oval, -t * 1.5f + i * 120, 70, false, stroke);

        // segmented inner ring, breathing
        oval.set(cx - R * 0.5f, cy - R * 0.5f, cx + R * 0.5f, cy + R * 0.5f);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeWidth(R * 0.09f);
        stroke.setColor(alpha(accent, 0.55f + 0.35f * pulse));
        for (int i = 0; i < 10; i++) c.drawArc(oval, i * 36 + 4, 28, false, stroke);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        // Mark VI triangle
        float tr = R * 0.33f;
        tri.reset();
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(-90 + i * 120);
            float x = cx + tr * (float) Math.cos(a), y = cy + tr * (float) Math.sin(a);
            if (i == 0) tri.moveTo(x, y); else tri.lineTo(x, y);
        }
        tri.close();
        stroke.setStrokeWidth(3 * d);
        stroke.setColor(alpha(accent, 0.9f));
        c.drawPath(tri, stroke);

        // core: white-hot centre fading to the accent
        float cr = R * (0.2f + 0.025f * pulse);
        fill.setShader(new RadialGradient(cx, cy, cr, new int[]{Color.WHITE, accent, alpha(accent, 0)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, cr, fill);
        fill.setShader(null);

        if (flash > 0) {
            fill.setShader(new RadialGradient(cx, cy, R, alpha(Color.WHITE, 0.85f * flash), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, R, fill);
            fill.setShader(null);
        }
    }
}

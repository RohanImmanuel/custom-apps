package com.rohan.linkrouter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

// Faint blueprint grid with a scan line sweeping down every few seconds.
public class HudBackground extends View {
    final Paint grid = new Paint(), scan = new Paint();
    final ValueAnimator sweep = ValueAnimator.ofFloat(0, 1);
    int accent = HudColors.CYAN, gridColor = HudColors.CYAN_GRID;

    public HudBackground(Context c) {
        super(c);
        grid.setStrokeWidth(1);
        sweep.setDuration(7_000);
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.setInterpolator(new LinearInterpolator());
        sweep.addUpdateListener(a -> invalidate());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sweep.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        sweep.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(HudColors.BG);
        grid.setColor(gridColor);
        float step = 28 * getResources().getDisplayMetrics().density;
        for (float x = 0; x < getWidth(); x += step) c.drawLine(x, 0, x, getHeight(), grid);
        for (float y = 0; y < getHeight(); y += step) c.drawLine(0, y, getWidth(), y, grid);
        float y = sweep.getAnimatedFraction() * (getHeight() + 200) - 100, band = 90;
        int a = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent));
        scan.setShader(new LinearGradient(0, y - band, 0, y, Color.TRANSPARENT, a, Shader.TileMode.CLAMP));
        c.drawRect(0, y - band, getWidth(), y, scan);
    }
}

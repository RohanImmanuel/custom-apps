package com.rohan.weatherwidget;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// Shared shell for the day and year overlays: a black card over the dimmed home screen; tap outside to close.
abstract class CardOverlay extends Activity {
    static final int WHITE = 0xFFFFFFFF, GREY = 0xFF8C8C8C, DIM = 0xFF4A4A4A, LINE = 0xFF262626;
    LinearLayout card;
    float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> finish());
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true); // taps inside the card don't fall through and close it
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setStroke(px(1), LINE);
        bg.setCornerRadius(px(28));
        card.setBackground(bg);
        card.setPadding(px(22), px(22), px(22), px(18));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        lp.leftMargin = lp.rightMargin = px(16);
        root.addView(card, lp);
        setContentView(root);
    }

    TextView label(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE);
        t.setLetterSpacing(0.08f);
        return t;
    }

    /** Dot-matrix arrow button, same glyph style as the widgets. */
    ImageView arrow(boolean left, Runnable onTap) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(WeatherWidget.matrix(6, GREY, left
                ? new String[]{"   #", "  # ", " #  ", "#   ", " #  ", "  # ", "   #"}
                : new String[]{"#   ", " #  ", "  # ", "   #", "  # ", " #  ", "#   "}));
        iv.setPadding(px(12), px(10), px(12), px(10));
        iv.setOnClickListener(v -> onTap.run());
        iv.setLayoutParams(new LinearLayout.LayoutParams(px(44), px(40)));
        return iv;
    }

    int px(float v) {
        return Math.round(v * dp);
    }
}

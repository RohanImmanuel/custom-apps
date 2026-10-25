package com.rohan.linkrouter;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Map;

// Day-to-day screen, styled as a workshop HUD: where links go; hold the arc reactor for the Chrome override.
public class HomeActivity extends Activity {
    static final Typeface HUD = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    static final Typeface HUD_BOLD = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    final Handler ticker = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() {
        // every second while the countdown runs (it shows seconds), otherwise just keep the screen honest
        public void run() { render(); ticker.postDelayed(this, Router.tempActive(HomeActivity.this) ? 1_000 : 15_000); }
    };
    HudBackground bg;
    ReactorView reactor;
    TextView title, mk, label, name, status, warn;
    ImageView gear;
    TextView hint;
    ValueAnimator charging;  // hold-to-engage ring
    Boolean shownTemp;       // which state is drawn; null = nothing yet
    String shownName = "";
    ValueAnimator hintPulse;
    float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        FrameLayout root = new FrameLayout(this);
        bg = new HudBackground(this);
        root.addView(bg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // content padding on top of the system bars (the grid itself runs behind them)
        col.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets bars = ins.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(px(20) + bars.left, px(12) + bars.top, px(20) + bars.right, bars.bottom);
            return ins;
        });
        root.addView(col);

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        title = hud("LINK//ROUTER", 15, HudColors.CYAN, true);
        mk = hud("  MK-II", 12, HudColors.CYAN_DIM, false);
        top.addView(title);
        top.addView(mk, new LinearLayout.LayoutParams(0, -2, 1));
        gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setImageTintList(android.content.res.ColorStateList.valueOf(HudColors.CYAN));
        gear.setPadding(px(10), px(10), px(10), px(10));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(gear, new LinearLayout.LayoutParams(px(44), px(44)));
        col.addView(top);

        // only when links are bypassing the router
        warn = hud("⚠  NOT DEFAULT BROWSER · TAP TO FIX", 12, HudColors.AMBER, true);
        warn.setPadding(px(12), px(10), px(12), px(10));
        warn.setBackground(frame(HudColors.AMBER));
        RoleManager rm = getSystemService(RoleManager.class);
        warn.setOnClickListener(v -> startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 1));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
        wp.topMargin = px(12);
        col.addView(warn, wp);

        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));
        reactor = new ReactorView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(px(250), px(250));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(reactor, rp);

        label = hud("ROUTING TARGET", 11, HudColors.CYAN_DIM, false);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(20);
        col.addView(label, lp);
        name = hud("", 40, HudColors.CYAN, true);
        name.setGravity(Gravity.CENTER);
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        status = hud("", 12, HudColors.CYAN_DIM, false);
        status.setGravity(Gravity.CENTER);
        col.addView(status, new LinearLayout.LayoutParams(-1, -2));
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        // pulsing instruction where the thumb rests; the reactor itself is the control
        hint = hud("", 12, HudColors.CYAN, true);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = px(120);
        col.addView(hint, hp);
        hintPulse = ValueAnimator.ofFloat(0.3f, 1f);
        hintPulse.setDuration(1100);
        hintPulse.setRepeatMode(ValueAnimator.REVERSE);
        hintPulse.setRepeatCount(ValueAnimator.INFINITE);
        hintPulse.addUpdateListener(a -> hint.setAlpha((float) a.getAnimatedValue()));

        reactor.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: startCharge(); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: releaseCharge(); return true;
            }
            return true;
        });
        // TalkBack: a double-tap toggles straight away
        reactor.setContentDescription("Arc reactor. Hold to engage or disengage Chrome for " + Router.OVERRIDE_MINUTES + " minutes");
        reactor.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) { toggle(); return true; }
                return super.performAccessibilityAction(host, action, args);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClickable(true);
            }
        });

        setContentView(root);
    }

    /** Hold the reactor: a ring charges for ~1.2 s with haptic ticks; let go early and it drains back. */
    void startCharge() {
        if (!reactor.isEnabled()) return; // no Chrome installed
        boolean engaged = Router.tempActive(this);
        reactor.chargeColor = engaged ? HudColors.CYAN : HudColors.RED;
        if (charging != null) charging.cancel();
        charging = ValueAnimator.ofFloat(reactor.charge, 1f);
        charging.setDuration((long) (1200 * (1 - reactor.charge)));
        charging.setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f));
        int[] lastTick = {(int) (reactor.charge * 4)};
        charging.addUpdateListener(a -> {
            reactor.charge = (float) a.getAnimatedValue();
            int q = (int) (reactor.charge * 4);
            if (q > lastTick[0] && q < 4) { reactor.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); lastTick[0] = q; }
        });
        charging.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled) return;
                reactor.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                reactor.burst();
                reactor.charge = 0;
                toggle();
            }
        });
        charging.start();
    }

    void releaseCharge() {
        if (charging == null || !charging.isRunning()) return;
        charging.cancel();
        ValueAnimator drain = ValueAnimator.ofFloat(reactor.charge, 0f);
        drain.setDuration(250);
        drain.addUpdateListener(a -> reactor.charge = (float) a.getAnimatedValue());
        drain.start();
    }

    void toggle() {
        if (Router.tempActive(this)) {
            Router.stopOverride(this);
            render();
        } else {
            engage();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        shownTemp = null;   // redraw everything, with the power-up sequence
        shownName = "";
        reactor.powerUp();
        tick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
        hintPulse.cancel();
        if (charging != null) charging.cancel();
        reactor.charge = 0;
    }

    void render() {
        Map<String, String> browsers = Router.browsers(this);
        String chosen = Router.chosen(this);
        String chosenName = browsers.getOrDefault(chosen, "No browser");
        boolean hasChrome = browsers.containsKey(Router.CHROME);
        boolean temp = Router.tempActive(this) && hasChrome;
        boolean priv = Router.prefs(this).getBoolean("private", false) && chosen != null && Router.supportsPrivate(chosen);
        int accent = temp ? HudColors.RED : HudColors.CYAN;

        warn.setVisibility(getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_BROWSER) ? View.GONE : View.VISIBLE);

        String target = (temp ? "Chrome" : chosenName).toUpperCase(Locale.ROOT);
        if (!target.equals(shownName)) {
            shownName = target;
            typeOut(name, target);
        }
        long left = Math.max(0, Router.tempUntil(this) - System.currentTimeMillis());
        status.setText(temp ? String.format(Locale.ROOT, "OVERRIDE ACTIVE · T-%02d:%02d · THEN %s",
                left / 60_000, left / 1000 % 60, chosenName.toUpperCase(Locale.ROOT))
                : priv ? "PRIVATE MODE · ENGAGED" : "ALL SYSTEMS NOMINAL");
        reactor.setProgress(temp ? left / (Router.OVERRIDE_MINUTES * 60_000f) : -1);

        reactor.setEnabled(hasChrome);
        hint.setVisibility(hasChrome ? View.VISIBLE : View.INVISIBLE);
        // restyle only when the state flips
        if (shownTemp == null || shownTemp != temp) {
            shownTemp = temp;
            reactor.setAccent(accent);
            bg.accent = accent;
            name.setTextColor(accent);
            name.setShadowLayer(18 * dp, 0, 0, accent);
            int dim = temp ? HudColors.RED_DIM : HudColors.CYAN_DIM;
            status.setTextColor(dim);
            label.setTextColor(dim);
            mk.setTextColor(dim);
            bg.gridColor = temp ? HudColors.RED_GRID : HudColors.CYAN_GRID;
            title.setTextColor(accent);
            gear.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
            hint.setTextColor(accent);
            hint.setText(temp ? "HOLD REACTOR · DISENGAGE" : "HOLD REACTOR · ENGAGE CHROME " + Router.OVERRIDE_MINUTES + " MIN");
            hint.setTranslationY(px(16));
            hint.animate().translationY(0).setDuration(400).setStartDelay(300).setInterpolator(new DecelerateInterpolator()).start();
            if (!hintPulse.isStarted()) hintPulse.start();
        }
    }

    void engage() {
        // the countdown notification needs permission; asked once, the override works either way
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        Router.startOverride(this);
        render();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (Router.tempActive(this)) Router.startOverride(this); // re-post the notification now it's allowed
    }

    // --- HUD building blocks ---

    TextView hud(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? HUD_BOLD : HUD);
        t.setLetterSpacing(0.16f);
        return t;
    }

    /** Sharp 1.5dp outline with a faint fill of the same colour. */
    GradientDrawable frame(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)));
        d.setStroke(px(1.5f), color);
        d.setCornerRadius(px(2));
        return d;
    }

    /** Typewriter reveal, like a HUD readout resolving. */
    void typeOut(TextView t, String text) {
        ValueAnimator a = ValueAnimator.ofInt(0, text.length());
        a.setDuration(Math.min(700, 55L * text.length()));
        a.addUpdateListener(v -> t.setText(text.substring(0, (int) v.getAnimatedValue())));
        a.start();
    }

    int px(float v) {
        return Math.round(v * dp);
    }
}

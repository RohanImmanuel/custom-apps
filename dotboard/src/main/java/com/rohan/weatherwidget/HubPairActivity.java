package com.rohan.weatherwidget;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Finds the DIRIGERA hub on Wi-Fi and pairs with it: press the hub's action button when asked.
public class HubPairActivity extends Activity {
    TextView status;
    Button action;
    volatile boolean pairing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = (int) (32 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        status = new TextView(this);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        action = new Button(this);
        root.addView(status);
        root.addView(action);
        setContentView(root);
        idle(HomeHub.paired(this) ? "Paired with your IKEA hub. Pair again if it moved or was reset."
                : "Pair with your IKEA DIRIGERA hub to show your sensors and control your light. You'll need to be on your home Wi-Fi, next to the hub.");
    }

    void idle(String message) {
        status.setText(message);
        action.setText(HomeHub.paired(this) ? "Pair again" : "Find my hub");
        action.setEnabled(true);
        action.setOnClickListener(v -> pair());
    }

    void pair() {
        if (pairing) return;
        pairing = true;
        action.setEnabled(false);
        status.setText("Looking for your hub…");
        new Thread(() -> {
            try {
                String host = HomeHub.discover(this, 8000);
                if (host == null) { done("Couldn't find a DIRIGERA hub. Check you're on your home Wi-Fi, then try again."); return; }
                HomeHub.Pairing p = new HomeHub.Pairing(host);
                runOnUiThread(() -> status.setText("Found it.\n\nNow press the action button on top of the hub (one short press)."));
                for (int i = 0; i < 60 && pairing; i++) {
                    String token = p.poll();
                    if (token != null) {
                        p.save(this, token);
                        HomeWidget.refresh(this);
                        done("Paired. The home widget now shows your sensors and light.");
                        return;
                    }
                    Thread.sleep(2000);
                }
                done("The button wasn't pressed in time. Try again when you're next to the hub.");
            } catch (Exception e) {
                done("Pairing failed: " + e.getMessage());
            }
        }).start();
    }

    void done(String message) {
        pairing = false;
        runOnUiThread(() -> idle(message));
    }

    @Override
    protected void onDestroy() {
        pairing = false; // stop polling if the screen is closed
        super.onDestroy();
    }
}

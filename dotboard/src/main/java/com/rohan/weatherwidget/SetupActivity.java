package com.rohan.weatherwidget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Widgets can't ask for permissions, so this screen does it: approximate location,
// then "Allow all the time" so the widget can read it while refreshing in the background.
public class SetupActivity extends Activity {
    TextView status;
    Button action;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            status.setText("The weather widget uses your approximate (city-level) location to show local weather and warnings.");
            action.setText("Allow approximate location");
            action.setOnClickListener(x -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1));
        } else if (!granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            status.setText("One more step: choose \"Allow all the time\" so the widget can update your location while it refreshes in the background.");
            action.setText("Allow all the time");
            action.setOnClickListener(x -> requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2));
        } else if (!granted(Manifest.permission.READ_CALENDAR)) {
            status.setText("The month calendar widget reads your calendars to show event dots and what's next.");
            action.setText("Allow calendar access");
            action.setOnClickListener(x -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 3));
        } else {
            status.setText("All set. The widgets now follow your city and your calendar.\nTap the weather widget any time to refresh.");
            action.setText("Done");
            action.setOnClickListener(x -> finish());
            refreshWidgets();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        onResume();
    }

    boolean granted(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    void refreshWidgets() {
        CalendarWidget.update(this);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        int[] ids = m.getAppWidgetIds(new ComponentName(this, WeatherWidget.class));
        sendBroadcast(new Intent(this, WeatherWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids));
    }
}

package com.rohan.linkrouter;

import android.app.Activity;
import android.app.StatusBarManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.Map;

// Set-once options: which browser, private mode, the Quick Settings tile, default-browser status.
public class SettingsActivity extends Activity {
    LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        list = findViewById(R.id.list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    void render() {
        list.removeAllViews();
        Map<String, String> browsers = Router.browsers(this);
        String chosen = Router.chosen(this);

        header("Browser");
        for (Map.Entry<String, String> e : browsers.entrySet()) {
            String pkg = e.getKey();
            View row = row(e.getValue(), Router.supportsPrivate(pkg) ? "Private" : null, appIcon(pkg));
            MaterialRadioButton radio = new MaterialRadioButton(this);
            radio.setChecked(pkg.equals(chosen));
            radio.setClickable(false); // the whole row is the target
            trailing(row, radio);
            row.setOnClickListener(v -> { Router.prefs(this).edit().putString("browser", pkg).apply(); render(); });
        }

        divider();
        header("Privacy");
        boolean canPrivate = chosen != null && Router.supportsPrivate(chosen);
        View prow = row("Private mode", canPrivate ? null : "Not available in " + browsers.getOrDefault(chosen, "this browser"), null);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setEnabled(canPrivate);
        sw.setChecked(canPrivate && Router.prefs(this).getBoolean("private", false));
        sw.setOnCheckedChangeListener((v, on) -> Router.prefs(this).edit().putBoolean("private", on).apply());
        trailing(prow, sw);
        prow.setEnabled(canPrivate);
        prow.setOnClickListener(v -> sw.toggle());

        if (browsers.containsKey(Router.CHROME)) {
            divider();
            header("Chrome · " + Router.OVERRIDE_MINUTES + " min");
            View trow = row("Quick Settings tile", null, null);
            if (Router.prefs(this).getBoolean("tileAdded", false)) {
                TextView added = trow.findViewById(R.id.subtitle);
                added.setText("Added");
                added.setVisibility(View.VISIBLE);
            } else {
                MaterialButton add = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                add.setText("Add");
                add.setOnClickListener(v -> addTile());
                trailing(trow, add);
            }
        }

        divider();
        header("Setup");
        RoleManager rm = getSystemService(RoleManager.class);
        boolean isDefault = rm.isRoleHeld(RoleManager.ROLE_BROWSER);
        View drow = row("Default browser", isDefault ? "Set" : "Not set · tap to fix", null);
        if (!isDefault) drow.setOnClickListener(v -> startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 1));
        row("Open a test link", null, null)
                .setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))));
    }

    void addTile() {
        getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(this, OverrideTile.class), "Chrome",
                Icon.createWithResource(this, R.drawable.ic_launcher_fg), getMainExecutor(), result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                            || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Router.prefs(this).edit().putBoolean("tileAdded", true).apply();
                        render();
                    }
                });
    }

    // --- MD3 list building blocks ---

    View row(String title, String subtitle, android.graphics.drawable.Drawable icon) {
        View r = LayoutInflater.from(this).inflate(R.layout.item_row, list, false);
        ((TextView) r.findViewById(R.id.title)).setText(title);
        if (subtitle != null) {
            TextView s = r.findViewById(R.id.subtitle);
            s.setText(subtitle);
            s.setVisibility(View.VISIBLE);
        }
        if (icon != null) {
            ImageView iv = r.findViewById(R.id.icon);
            iv.setImageDrawable(icon);
            iv.setVisibility(View.VISIBLE);
        }
        list.addView(r);
        return r;
    }

    void trailing(View row, View control) {
        ((android.widget.FrameLayout) row.findViewById(R.id.trailing)).addView(control);
    }

    void header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        t.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0));
        int dp = Math.round(getResources().getDisplayMetrics().density);
        t.setPadding(16 * dp, 20 * dp, 16 * dp, 8 * dp);
        list.addView(t);
    }

    void divider() {
        list.addView(new MaterialDivider(this));
    }

    android.graphics.drawable.Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}

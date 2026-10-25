package com.rohan.doticons;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;

// Preview of every icon plus how to apply the pack; the launcher does the rest.
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding((int) (20 * dp), (int) (64 * dp), (int) (20 * dp), (int) (20 * dp));

        TextView how = new TextView(this);
        how.setText("MONO LINES\n\nTo apply in Niagara: long-press the home screen → Niagara settings → Icons → Icon pack → Mono Lines.\n\nIcons by Arcticons (arcticons.com), CC BY-SA 4.0.");
        how.setTextColor(0xFFB0B0B0);
        how.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(how);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        int size = (int) (56 * dp), gap = (int) (8 * dp);
        for (Field f : R.drawable.class.getFields()) {
            ImageView v = new ImageView(this);
            try { v.setImageResource(f.getInt(null)); } catch (IllegalAccessException ignored) { continue; }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size; lp.height = size;
            lp.setMargins(gap, gap, gap, gap);
            grid.addView(v, lp);
        }
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = (int) (24 * dp);
        root.addView(grid, glp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.addView(root);
        setContentView(scroll);
    }
}

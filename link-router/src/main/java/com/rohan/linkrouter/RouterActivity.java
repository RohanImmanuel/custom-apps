package com.rohan.linkrouter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

// Invisible: hands the link to the target browser and closes.
public class RouterActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent out = Router.forward(this, getIntent());
        try {
            if (out != null) startActivity(out);
            else Toast.makeText(this, "No browser to open this link", Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Couldn't open the link in " + out.getPackage(), Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}

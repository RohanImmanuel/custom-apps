package com.rohan.linkrouter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// "Switch back" on the override notification.
public class StopOverrideReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Router.stopOverride(c);
    }
}

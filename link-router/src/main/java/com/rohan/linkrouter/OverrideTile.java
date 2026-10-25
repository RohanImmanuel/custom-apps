package com.rohan.linkrouter;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Quick Settings tile: start or stop the Chrome override from anywhere, e.g. mid-payment.
public class OverrideTile extends TileService {
    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        if (Router.tempActive(this)) Router.stopOverride(this);
        else Router.startOverride(this);
        refresh();
    }

    void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = Router.tempActive(this);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Chrome");
        t.setSubtitle(on ? Router.minutesLeft(this) : Router.OVERRIDE_MINUTES + " min");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_launcher_fg));
        t.updateTile();
    }
}

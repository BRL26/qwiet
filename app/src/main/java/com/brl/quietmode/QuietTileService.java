package com.brl.quietmode;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class QuietTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        boolean target = !QuietController.isOn(this);
        QuietController.setQuietMode(this, target);
        ControlReceiver.sendState(this);
        updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean on = QuietController.isOn(this);
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("qwiet");
        tile.setSubtitle(on ? "-" + QuietController.getDb(this) + " dB" : "Off");
        tile.updateTile();
    }
}

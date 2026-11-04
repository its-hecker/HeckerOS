// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class HeckerVpnTileService extends TileService {
    private HeckerVpnClient client;
    @Override public void onStartListening() {
        super.onStartListening(); if (client != null) client.close(); client = new HeckerVpnClient(this);
        client.call(HeckerVpnClient.request("state"), this::show);
    }
    @Override public void onStopListening() { if (client != null) client.close(); client = null; super.onStopListening(); }
    @Override public void onClick() {
        super.onClick(); unlockAndRun(() -> {
            if (client == null) client = new HeckerVpnClient(this);
            client.call(HeckerVpnClient.request("state"), state -> {
                if (!state.getString("error", "").isEmpty() || state.getString("selected", "").isEmpty()) { open(); return; }
                client.call(HeckerVpnClient.request(state.getBoolean("desired") || state.getBoolean("cleanup_required") ? "disconnect" : "connect"), result -> {
                    show(result); if (!result.getString("error", "").isEmpty()) open();
                });
            });
        });
    }
    private void show(Bundle state) {
        Tile tile = getQsTile(); if (tile == null) return;
        tile.setLabel(getString(R.string.vpn_title));
        tile.setSubtitle(getString(HeckerVpnActivity.statusLabel(state.getString("status", "disconnected"))));
        tile.setState(state.getBoolean("configured") ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); tile.updateTile();
    }
    private void open() {
        PendingIntent intent = PendingIntent.getActivity(this, 784, new Intent(this, HeckerVpnActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startActivityAndCollapse(intent);
    }
}

// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import org.hecker.vpn.VpnTileState;

public final class HeckerVpnTileService extends TileService {
    private HeckerVpnClient client;
    private Bundle latest;
    private boolean listening, receiving;
    private Boolean pendingEnabled;
    private int requestSerial, pendingCalls;
    private long stateVersion = -1;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (listening && VpnTileState.ACTION_CHANGED.equals(intent.getAction())
                    && intent.getExtras() != null) accept(intent.getExtras());
        }
    };

    @Override public void onStartListening() {
        super.onStartListening(); listening = true;
        if (!receiving) {
            registerReceiver(changes, new IntentFilter(VpnTileState.ACTION_CHANGED),
                    VpnTileState.CONTROL_PERMISSION, null, Context.RECEIVER_EXPORTED);
            receiving = true;
        }
        ensureClient();
        int serial = requestSerial;
        client.call(HeckerVpnClient.request("state"), state -> {
            // A delayed initial read must not undo a newer tap or state broadcast.
            if (serial == requestSerial && pendingEnabled == null) accept(state);
        });
    }
    @Override public void onStopListening() {
        listening = false; stopReceiving(); releaseIfIdle(); super.onStopListening();
    }
    @Override public void onDestroy() {
        listening = false; stopReceiving();
        if (client != null) client.close();
        client = null; super.onDestroy();
    }
    private void ensureClient() { if (client == null) client = new HeckerVpnClient(this); }
    private void stopReceiving() {
        if (receiving) { unregisterReceiver(changes); receiving = false; }
    }
    private void releaseIfIdle() {
        // Keep an accepted tap alive if the user closes QS during service binding/setup.
        if (!listening && pendingCalls == 0 && client != null) { client.close(); client = null; }
    }
    @Override public void onClick() {
        super.onClick(); unlockAndRun(() -> {
            Tile tile = getQsTile(); if (tile == null) return;
            boolean enabled = pendingEnabled != null ? pendingEnabled : latest != null
                    ? enabled(latest) : tile.getState() == Tile.STATE_ACTIVE;
            pendingEnabled = !enabled;
            int serial = ++requestSerial;
            show(); // Paint the requested switch state before binding/DNS/native setup.
            ensureClient(); pendingCalls++;
            Bundle request = HeckerVpnClient.request("set_enabled");
            request.putBoolean("enabled", pendingEnabled);
            client.call(request, result -> {
                pendingCalls--;
                if (serial == requestSerial) {
                    pendingEnabled = null;
                    boolean current = accept(result);
                    if (current && listening && !result.getString("error", "").isEmpty()) open();
                }
                releaseIfIdle();
            });
        });
    }
    private static boolean enabled(Bundle state) {
        return VpnTileState.enabled(state.getBoolean("desired"), state.getBoolean("configured"),
                state.getBoolean("cleanup_required"));
    }
    private boolean accept(Bundle state) {
        long version = state.getLong(VpnTileState.VERSION);
        if (state.containsKey(VpnTileState.VERSION) && version < stateVersion) { show(); return false; }
        if (state.containsKey("desired") && state.containsKey("configured")) {
            latest = new Bundle(state);
            if (state.containsKey(VpnTileState.VERSION)) stateVersion = version;
        } else {
            // A transport/busy error is not proof that an existing tunnel has stopped.
            latest = latest == null ? new Bundle() : new Bundle(latest);
            latest.putAll(state); latest.putString("status", "error");
        }
        show(); return true;
    }
    private void show() {
        if (!listening) return;
        Tile tile = getQsTile(); if (tile == null) return;
        tile.setLabel(getString(R.string.vpn_title));
        int subtitle;
        boolean enabled;
        if (pendingEnabled != null) {
            enabled = pendingEnabled;
            subtitle = enabled ? R.string.vpn_connecting : R.string.vpn_disconnecting;
        } else {
            Bundle state = latest == null ? new Bundle() : latest;
            enabled = enabled(state);
            subtitle = !state.getString("error", "").isEmpty() ? R.string.vpn_error
                    : HeckerVpnActivity.statusLabel(state.getString("status", "disconnected"));
        }
        String description = getString(subtitle);
        tile.setSubtitle(description); tile.setStateDescription(description);
        // Color describes enabled routing; only the controller can report Active peer contact.
        boolean unavailable = pendingEnabled == null && latest != null
                && !latest.getBoolean("controller_available", true);
        tile.setState(unavailable ? Tile.STATE_UNAVAILABLE : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
    private void open() {
        PendingIntent intent = PendingIntent.getActivity(this, 784, new Intent(this, HeckerVpnActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startActivityAndCollapse(intent);
    }
}

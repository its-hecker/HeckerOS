// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class MotionSenseTileService extends TileService {
    private final ContentObserver changes = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean self) { refresh(); }
    };
    private boolean listening;
    private boolean available() {
        return SystemProperties.getBoolean("ro.vendor.aware_available", false)
                && Settings.Secure.getInt(getContentResolver(), "aware_allowed", 1) == 1;
    }
    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = Settings.Secure.getInt(getContentResolver(), "aware_enabled", 1) == 1;
        tile.setState(!available() ? Tile.STATE_UNAVAILABLE : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.motion_sense_title));
        tile.setSubtitle(getString(!available() ? R.string.qs_unavailable : enabled ? R.string.qs_on : R.string.qs_off));
        tile.updateTile();
    }
    @Override public void onStartListening() {
        if (!listening) {
            for (String key : new String[]{"aware_enabled", "aware_allowed"}) {
                getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, changes);
            }
            listening = true;
        }
        refresh();
    }
    @Override public void onStopListening() {
        if (listening) getContentResolver().unregisterContentObserver(changes);
        listening = false;
    }
    @Override public void onDestroy() { onStopListening(); super.onDestroy(); }
    @Override public void onClick() {
        if (!available()) return;
        unlockAndRun(() -> {
            int current = Settings.Secure.getInt(getContentResolver(), "aware_enabled", 1);
            Settings.Secure.putInt(getContentResolver(), "aware_enabled", current == 1 ? 0 : 1);
            refresh();
        });
    }
}

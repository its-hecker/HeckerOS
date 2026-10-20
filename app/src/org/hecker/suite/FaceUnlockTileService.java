// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Opens Android's authenticated biometric setup. Enrollment is never changed by the tile. */
public final class FaceUnlockTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        BiometricManager manager = getSystemService(BiometricManager.class);
        boolean ready = manager != null && manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
        tile.setLabel(getString(R.string.qs_face_unlock));
        tile.setSubtitle(getString(ready ? R.string.qs_ready : R.string.qs_setup));
        tile.setState(ready ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
    @Override public void onClick() {
        Intent intent = new Intent("android.settings.FACE_SETTINGS").setPackage("com.android.settings")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}

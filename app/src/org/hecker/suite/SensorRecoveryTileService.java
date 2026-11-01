// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A deliberate action opens diagnostics; adding or listening to the tile never resets hardware. */
public final class SensorRecoveryTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile=getQsTile();if(tile==null)return;
        tile.setLabel(getString(R.string.sensor_recovery_title));
        tile.setSubtitle(getString(R.string.sensor_recovery_tile_summary));
        tile.setState(SensorRecoveryActivity.supported()?Tile.STATE_INACTIVE:Tile.STATE_UNAVAILABLE);
        tile.updateTile();
    }
    @Override public void onClick() {
        unlockAndRun(()->{
            Intent intent=new Intent(this,SensorRecoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(PendingIntent.getActivity(this,7,intent,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        });
    }
}

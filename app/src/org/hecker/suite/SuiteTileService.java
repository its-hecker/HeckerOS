// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SuiteTileService extends TileService {
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.suite_title));
        tile.setSubtitle(getString(R.string.qs_make_it_yours));
        tile.updateTile();
    }
    @Override public void onClick() {
        Intent intent = new Intent().setClassName("com.android.settings", "org.hecker.suite.SuiteActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}

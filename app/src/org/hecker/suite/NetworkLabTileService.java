// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.*;

/** Opens NetLab after unlock; a blind tile tap never changes anyone's connectivity. */
public final class NetworkLabTileService extends TileService {
    @Override public void onStartListening(){Tile tile=getQsTile();if(tile!=null){tile.setState(Tile.STATE_INACTIVE);tile.setLabel(getString(R.string.netlab_title));tile.setSubtitle(getString(R.string.netlab_open_dashboard));tile.updateTile();}}
    @Override public void onClick(){unlockAndRun(()->startActivityAndCollapse(PendingIntent.getActivity(this,0,new Intent(this,NetworkLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)));}
}

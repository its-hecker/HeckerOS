// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** BackupManagerService binds through the platform BACKUP permission and the ROM allowlist. */
public final class VaultTransportService extends Service {
    private final VaultTransport transport=new VaultTransport();
    @Override public IBinder onBind(Intent intent){
        return intent!=null&&"android.backup.TRANSPORT_HOST".equals(intent.getAction())?transport.getBinder():null;
    }
    @Override public void onDestroy(){transport.closeStreams();super.onDestroy();}
}

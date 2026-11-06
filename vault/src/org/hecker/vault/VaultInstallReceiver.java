// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.*;
import android.content.pm.PackageInstaller;
import java.util.concurrent.CompletableFuture;

public final class VaultInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;int id=intent.getIntExtra("vault_session",-1);
        CompletableFuture<Integer> result=VaultInstaller.results.get(id);
        if(result!=null&&("org.hecker.vault.INSTALL."+id).equals(intent.getAction()))
            result.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE));
    }
}

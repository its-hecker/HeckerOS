// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.content.*;

/** Repairs only our interrupted transport handoff; never schedules or executes a restore. */
public final class VaultRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))return;
        PendingResult result=goAsync();new Thread(()->{try{if(!VaultService.busy){VaultNative.recoverIdle(context);VaultSystemData.recoverSmsWrite(context);}}
            finally{result.finish();}},"HeckerVault-recovery").start();
    }
}

// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.job.*;
import android.content.*;
import android.os.*;

final class VaultJobs {
    private static final int ID=0x48564c54;
    static void configure(Context c,boolean enabled){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(!enabled){scheduler.cancel(ID);VaultStorage.prefs(c).edit().putBoolean("scheduled",false).apply();return;}
        long days=VaultStorage.prefs(c).getBoolean("weekly",false)?7:1;
        JobInfo job=new JobInfo.Builder(ID,new ComponentName(c,VaultJobService.class)).setPersisted(true)
                .setPeriodic(days*86400000L,6*3600000L).setRequiresCharging(VaultStorage.prefs(c).getBoolean("charging",true))
                .setRequiresStorageNotLow(true).build();
        if(scheduler.schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("Android refused the backup schedule");
        VaultStorage.prefs(c).edit().putBoolean("scheduled",true).apply();
    }
}

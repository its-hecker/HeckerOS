// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

import android.app.job.*;
import android.content.Intent;
import android.os.*;
import java.util.*;

public final class VaultJobService extends JobService {
    private static VaultJobService active;private JobParameters parameters;
    @Override public boolean onStartJob(JobParameters params){
        if(!VaultStorage.prefs(this).getBoolean("scheduled",false))return false;
        if(VaultService.busy||!getSystemService(UserManager.class).isUserUnlocked()){
            new Handler(getMainLooper()).post(()->jobFinished(params,true));return true;
        }
        try{
            char[] password=VaultKeys.recalled(this);Set<String> categories=VaultStorage.prefs(this).getStringSet("categories",VaultSystemData.CATEGORIES);
            List<String> apps=VaultStorage.prefs(this).getBoolean("schedule_all",true)?VaultPackages.userApps(this):
                    new ArrayList<>(VaultStorage.prefs(this).getStringSet("selected_apps",Collections.emptySet()));
            VaultRequest request=new VaultRequest("backup",password,apps,categories,null,VaultStorage.prefs(this).getBoolean("include_data",true)?"both":"apk",true);
            active=this;parameters=params;VaultService.launch(this,request);return true;
        }catch(Exception e){VaultStorage.prefs(this).edit().putString("report","Scheduled backup could not start. Unlock the device, check the folder and check Vault encryption settings.").apply();return false;}
    }
    @Override public boolean onStopJob(JobParameters params){if(active==this){active=null;parameters=null;
        startService(new Intent(this,VaultService.class).setAction(VaultService.CANCEL_SCHEDULED));}return true;}
    static void finished(){VaultJobService job=active;if(job!=null){active=null;job.jobFinished(job.parameters,false);job.parameters=null;}}
}

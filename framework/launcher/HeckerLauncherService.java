// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import com.android.server.SystemService;

/** Narrow platform bridge; Pixel Launcher keeps its original HOME/Recents identity. */
public final class HeckerLauncherService extends SystemService {
    private static final String PIXEL="com.google.android.apps.nexuslauncher";
    public HeckerLauncherService(Context c){super(c);}
    @Override public void onStart(){publishBinderService("hecker_launcher",binder);}
    private int enforce(){
        int uid=Binder.getCallingUid(),user=UserHandle.getUserId(uid);
        if(user!=ActivityManager.getCurrentUser())throw new SecurityException("Inactive launcher user");
        Context c=getContext().createContextAsUser(UserHandle.of(user),0);PackageManager pm=c.getPackageManager();
        try{
            ApplicationInfo app=pm.getApplicationInfo(PIXEL,0);
            ResolveInfo home=pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
            if(app.uid==uid&&(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0
                    &&home!=null&&home.activityInfo!=null&&PIXEL.equals(home.activityInfo.packageName))return user;
        }catch(PackageManager.NameNotFoundException ignored){}
        throw new SecurityException("Only the preinstalled default Pixel Launcher may use this bridge");
    }
    private final IHeckerLauncherService.Stub binder=new IHeckerLauncherService.Stub(){
        @Override public Bundle configuration(){
            int user=enforce();long id=Binder.clearCallingIdentity();try{
                Bundle b=new Bundle();String[] keys={"double_tap","glass","glass_targets","corner_tl","corner_tr","corner_br","corner_bl"};
                int[] defaults={1,1,15,28,28,28,28};
                for(int i=0;i<keys.length;i++)b.putInt(keys[i],Settings.Secure.getIntForUser(getContext().getContentResolver(),"hecker_pixel_"+keys[i],defaults[i],user));
                return b;
            }finally{Binder.restoreCallingIdentity(id);}
        }
        @Override public ParcelFileDescriptor wallpaper(){
            int user=enforce();long id=Binder.clearCallingIdentity();try{
                WallpaperManager manager=WallpaperManager.getInstance(getContext().createContextAsUser(UserHandle.of(user),0));
                return manager.getWallpaperInfo()==null?manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM):null;
            }finally{Binder.restoreCallingIdentity(id);}
        }
        @Override public void sleep(){
            int user=enforce();long id=Binder.clearCallingIdentity();try{
                Context c=getContext();if(c.getSystemService(KeyguardManager.class).isKeyguardLocked())return;
                for(ActivityManager.RunningTaskInfo task:c.getSystemService(ActivityManager.class).getRunningTasks(1)){
                    if(task.userId==user&&task.displayId==0&&task.isFocused&&task.isVisible()&&task.topActivity!=null&&PIXEL.equals(task.topActivity.getPackageName()))
                        c.getSystemService(PowerManager.class).goToSleep(SystemClock.uptimeMillis());
                }
            }finally{Binder.restoreCallingIdentity(id);}
        }
    };
}

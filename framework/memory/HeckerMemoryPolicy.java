// SPDX-License-Identifier: Apache-2.0
package com.android.server.am;

import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_RESTRICTION_CHANGE;
import static com.android.server.am.psc.Constants.CACHED_APP_MIN_ADJ;
import static com.android.server.am.psc.Constants.PERCEPTIBLE_LOW_APP_ADJ;
import static com.android.server.am.psc.Constants.SERVICE_ADJ;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.*;
import android.content.pm.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import android.util.Slog;
import com.android.internal.hecker.HeckerMemoryRules;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Opt-in policy in AMS. PM/settings work stays outside process-manager locks. */
public final class HeckerMemoryPolicy {
    private static final String TAG="HeckerMemory";
    private static volatile HeckerMemoryPolicy current;
    private final ActivityManagerService am;
    private final Context context;
    private final Handler worker;
    private volatile Map<Integer,Entry> entries=Collections.emptyMap();
    // Access only while holding both AMS locks; weak keys do not retain dead processes.
    private final Map<ProcessRecord,Integer> originalAdj=new WeakHashMap<>();
    private static final class Entry {
        final ApplicationInfo app;final int flags;
        Entry(ApplicationInfo app,int flags){this.app=app;this.flags=flags;}
    }
    private HeckerMemoryPolicy(ActivityManagerService service){
        am=service;context=service.mContext;
        HandlerThread thread=new HandlerThread("HeckerMemory",android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();worker=new Handler(thread.getLooper());
    }
    static void start(ActivityManagerService service){
        if(current!=null)return;
        HeckerMemoryPolicy owner=new HeckerMemoryPolicy(service);current=owner;
        ServiceManager.addService("hecker_memory",owner.binder);
        owner.context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(HeckerMemoryRules.SETTING),false,
                new ContentObserver(owner.worker){@Override public void onChange(boolean self){owner.reload();}},UserHandle.USER_ALL);
        IntentFilter filter=new IntentFilter(Intent.ACTION_USER_SWITCHED);filter.addAction(Intent.ACTION_USER_UNLOCKED);
        filter.addAction(Intent.ACTION_USER_REMOVED);filter.addAction(Intent.ACTION_INPUT_METHOD_CHANGED);
        filter.addAction("android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED");
        owner.context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){owner.worker.post(owner::reload);}},filter,Context.RECEIVER_NOT_EXPORTED);
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        owner.context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){owner.worker.post(owner::reload);}},packages,Context.RECEIVER_NOT_EXPORTED);
        owner.context.getSystemService(android.app.role.RoleManager.class).addOnRoleHoldersChangedListenerAsUser(
                command -> owner.worker.post(command), (role,user) -> owner.worker.post(owner::reload), UserHandle.ALL);
        owner.worker.post(owner::reload);
    }
    private void reload(){
        Map<Integer,Entry> updated=new HashMap<>();PackageManager pm=context.getPackageManager();
        try{
            for(UserHandle handle:context.getSystemService(UserManager.class).getUserHandles(true)){
                int user=handle.getIdentifier();Context userContext=context.createContextAsUser(handle,0);
                PackageManager userPm=userContext.getPackageManager();
                String raw=Settings.Secure.getStringForUser(context.getContentResolver(),HeckerMemoryRules.SETTING,user);
                JSONObject rules=raw==null||raw.length()>128*1024?new JSONObject():new JSONObject(raw);
                Set<String> protectedApps=new HashSet<>();
                Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
                ResolveInfo resolved=userPm.resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
                if(resolved!=null&&resolved.activityInfo!=null)protectedApps.add(resolved.activityInfo.packageName);
                String ime=Settings.Secure.getStringForUser(context.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD,user);
                ComponentName input=ime==null?null:ComponentName.unflattenFromString(ime);
                if(input!=null)protectedApps.add(input.getPackageName());
                android.app.role.RoleManager roles=userContext.getSystemService(android.app.role.RoleManager.class);
                for(String role:new String[]{android.app.role.RoleManager.ROLE_DIALER,android.app.role.RoleManager.ROLE_SMS,
                        android.app.role.RoleManager.ROLE_ASSISTANT,android.app.role.RoleManager.ROLE_EMERGENCY})
                    if(roles.isRoleAvailable(role))protectedApps.addAll(roles.getRoleHolders(role));
                for(ApplicationInfo app:userPm.getInstalledApplications(0)){
                    if(!UserHandle.isApp(app.uid)||(app.flags&ApplicationInfo.FLAG_PERSISTENT)!=0
                            ||HeckerMemoryRules.protectedPackage(app.packageName)||protectedApps.contains(app.packageName))continue;
                    String[] shared=pm.getPackagesForUid(app.uid);
                    if(shared==null||shared.length!=1||!app.packageName.equals(shared[0]))continue;
                    updated.put(app.uid,new Entry(app,HeckerMemoryRules.validated(rules.optInt(app.packageName))));
                }
            }
        }catch(Exception unavailable){Slog.w(TAG,"Unable to reload app policy; retaining normal Android behavior",unavailable);updated.clear();}
        entries=Collections.unmodifiableMap(updated);
        synchronized(am.mGlobalLock){synchronized(am.mProcLock){
            for(ProcessRecord process:am.mProcessList.getLruProcessesLOSP())applyResident(process);
            am.updateOomAdjLocked(OOM_ADJ_REASON_RESTRICTION_CHANGE);
        }}
        worker.removeCallbacks(background);
        if(updated.values().stream().anyMatch(e->(e.flags&HeckerMemoryRules.BLOCK_BACKGROUND)!=0&&HeckerMemoryRules.clean(e.flags)))
            worker.postDelayed(background,15_000);
    }
    private Entry entry(ApplicationInfo info){
        Entry e=info==null?null:entries.get(info.uid);
        return e!=null&&e.app.packageName.equals(info.packageName)?e:null;
    }
    static boolean blockStart(ApplicationInfo info,HostingRecord hosting){
        HeckerMemoryPolicy owner=current;if(owner==null||hosting==null)return false;
        Entry e=owner.entry(info);if(e==null||(e.flags&HeckerMemoryRules.BLOCK_START)==0)return false;
        String type=hosting.getType();
        boolean foreground=hosting.isTopApp()||type.equals(HostingRecord.HOSTING_TYPE_ACTIVITY)
                ||type.equals(HostingRecord.HOSTING_TYPE_NEXT_ACTIVITY)||type.equals(HostingRecord.HOSTING_TYPE_TOP_ACTIVITY)
                ||type.equals(HostingRecord.HOSTING_TYPE_NEXT_TOP_ACTIVITY);
        if(!foreground)for(ProcessRecord process:owner.am.mProcessList.getLruProcessesLOSP())
            if(process.uid==info.uid&&!process.isKilledByAm()
                    &&process.getCurProcState()<=ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE){foreground=true;break;}
        return HeckerMemoryRules.blockStart(e.flags,foreground);
    }
    static void onProcessStarting(ProcessRecord app){
        HeckerMemoryPolicy owner=current;if(owner!=null)synchronized(owner.am.mProcLock){owner.applyResident(app);}
    }
    private void applyResident(ProcessRecord app){
        Entry e=entry(app.info);Integer original=originalAdj.get(app);
        if(e!=null&&(e.flags&HeckerMemoryRules.RESIDENT)!=0){
            if(original==null||app.getMaxAdj()!=Math.min(original,PERCEPTIBLE_LOW_APP_ADJ)){original=app.getMaxAdj();originalAdj.put(app,original);}
            am.mProcessStateController.setMaxAdj(app,Math.min(original,PERCEPTIBLE_LOW_APP_ADJ));
        }else if(original!=null){
            if(app.getMaxAdj()==Math.min(original,PERCEPTIBLE_LOW_APP_ADJ))am.mProcessStateController.setMaxAdj(app,original);
            originalAdj.remove(app);
        }
    }
    public static void onTaskRemoved(int user,String packageName){
        HeckerMemoryPolicy owner=current;if(owner==null||packageName==null)return;
        // WM calls this under its own lock. Never acquire an AMS lock on that thread.
        owner.worker.postDelayed(()->{
            for(Entry e:owner.entries.values())if(UserHandle.getUserId(e.app.uid)==user&&e.app.packageName.equals(packageName)
                    &&(e.flags&HeckerMemoryRules.CLEAN_TASK)!=0&&HeckerMemoryRules.clean(e.flags))owner.safeClean(user,packageName,SERVICE_ADJ,false);
        },1500);
    }
    private final Runnable background=new Runnable(){public void run(){
        Set<Integer> users=new HashSet<>();
        for(Entry e:entries.values())if((e.flags&HeckerMemoryRules.BLOCK_BACKGROUND)!=0&&HeckerMemoryRules.clean(e.flags))
            users.add(UserHandle.getUserId(e.app.uid));
        for(int user:users)safeClean(user,null,SERVICE_ADJ,true);
        if(!users.isEmpty())worker.postDelayed(this,15_000);
    }};
    private Bundle safeClean(int user,String selected,int minAdj,boolean backgroundOnly){
        try{return clean(user,selected,minAdj,backgroundOnly);}
        catch(RuntimeException unavailable){Slog.w(TAG,"Background cleanup unavailable",unavailable);return new Bundle();}
    }
    private Bundle clean(int user,String selected,int minAdj){return clean(user,selected,minAdj,false);}
    private Bundle clean(int user,String selected,int minAdj,boolean backgroundOnly){
        Bundle result=new Bundle();result.putBoolean("available",true);
        Set<String> killedApps=new HashSet<>();int killed=0;
        synchronized(am.mGlobalLock){synchronized(am.mProcLock){
            am.updateOomAdjLocked(OOM_ADJ_REASON_RESTRICTION_CHANGE);
            ArrayList<ProcessRecord> before=new ArrayList<>(am.mProcessList.getLruProcessesLOSP());
            Set<String> targets=new HashSet<>();Set<ProcessRecord> alive=new HashSet<>();
            for(ProcessRecord app:before){
                if(app.getPid()>0&&!app.isKilledByAm())alive.add(app);
                Entry e=entry(app.info);
                if(e==null||UserHandle.getUserId(app.uid)!=user||!HeckerMemoryRules.shouldClean(e.flags,app.getCurAdj(),minAdj,app.getCurProcState()==ActivityManager.PROCESS_STATE_HOME,backgroundOnly)
                        ||selected!=null&&!selected.equals(app.info.packageName)||app.getPid()<=0||app.isKilledByAm())continue;
                targets.add(app.info.packageName);
            }
            for(ProcessRecord app:before){
                Entry e=entry(app.info);
                if(e==null||UserHandle.getUserId(app.uid)!=user||!targets.contains(app.info.packageName)
                        ||!HeckerMemoryRules.shouldClean(e.flags,app.getCurAdj(),minAdj,app.getCurProcState()==ActivityManager.PROCESS_STATE_HOME,backgroundOnly)||app.getPid()<=0||app.isKilledByAm())continue;
                app.killLocked("Hecker Memory Boost","Hecker Memory Boost",ApplicationExitInfo.REASON_USER_REQUESTED,
                        ApplicationExitInfo.SUBREASON_KILL_BACKGROUND,null,false,false);
            }
            for(ProcessRecord app:alive)if(UserHandle.getUserId(app.uid)==user&&app.isKilledByAm()){
                killed++;killedApps.add(app.info.packageName);
            }
        }}
        result.putInt("apps",killedApps.size());result.putInt("processes",killed);return result;
    }
    private void enforce(boolean suiteOnly){
        int uid=Binder.getCallingUid(),user=UserHandle.getUserId(uid);
        if(!suiteOnly&&uid==android.os.Process.SYSTEM_UID)return;
        if(user!=ActivityManager.getCurrentUser())throw new SecurityException("Inactive user");
        PackageManager pm=context.getPackageManager();
        for(String pkg:suiteOnly?new String[]{"org.hecker.suite"}:new String[]{"org.hecker.suite","com.android.systemui"}){
            try{if(pm.getPackageUidAsUser(pkg,PackageManager.PackageInfoFlags.of(0),user)==uid
                    &&pm.checkSignatures("android",pkg)==PackageManager.SIGNATURE_MATCH)return;}
            catch(PackageManager.NameNotFoundException ignored){}
        }
        // SystemUI normally lives in the system user's shared system UID while users switch.
        throw new SecurityException("Only platform-signed Hecker Suite/SystemUI may use Memory Boost");
    }
    private final IHeckerMemoryService.Stub binder=new IHeckerMemoryService.Stub(){
        @Override public String[] manageablePackages(){
            enforce(true);int user=UserHandle.getUserId(Binder.getCallingUid());
            return entries.values().stream().filter(e->UserHandle.getUserId(e.app.uid)==user).map(e->e.app.packageName).toArray(String[]::new);
        }
        @Override public Bundle boost(){
            enforce(false);int user=ActivityManager.getCurrentUser();
            long identity=Binder.clearCallingIdentity();
            try{
                FutureTask<Bundle> task=new FutureTask<>(()->{
                    if(user!=ActivityManager.getCurrentUser())return new Bundle();
                    return clean(user,null,CACHED_APP_MIN_ADJ);
                });worker.post(task);
                return task.get(4,TimeUnit.SECONDS);
            }catch(Exception unavailable){Slog.w(TAG,"Cleanup did not complete",unavailable);Bundle result=new Bundle();result.putBoolean("available",false);return result;}
            finally{Binder.restoreCallingIdentity(identity);}
        }
    };
}

// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.StatusBarManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.KeyEvent;
import com.android.server.SystemService;
import com.android.internal.statusbar.IStatusBarService;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Trusted controls stay in system_server, outside the overlay process and its SELinux domain. */
public final class HeckerGameService extends SystemService {
    private static final String CHARGE="/sys/class/power_supply/charger/game_charge_disable";
    private static final String GPU="/sys/class/kgsl/kgsl-3d0/gpubusy";
    private static final String EDGE="hecker_game_block_edge",ASSIST="hecker_game_block_assistant";
    private static final String RESTORE="hecker_game_restore_brightness";
    private static final int NAV=1,EDGES=2,ASSISTANT=4,BRIGHTNESS=8,BYPASS=16;
    private static volatile int keyFlags;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final GameTelemetry telemetry=new GameTelemetry();
    private final Object lock=new Object();
    private IBinder owner;
    private IBinder.DeathRecipient death;
    private int user=-1,brightnessMode=-1,appliedFlags;
    private boolean charging,gameFocused;
    private long deadline;
    private final IBinder statusToken=new Binder();
    private int statusUser=-1;
    private final Runnable expiry=()->{synchronized(lock){if(owner!=null&&SystemClock.elapsedRealtime()>=deadline)restore();}};
    public HeckerGameService(Context c){super(c);}
    @Override public void onStart(){publishBinderService("hecker_game",binder);}
    @Override public void onBootPhase(int phase){
        if(phase!=PHASE_SYSTEM_SERVICES_READY)return;
        synchronized(lock){restore();recoverBrightness(ActivityManager.getCurrentUser());}
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_SWITCHED);filter.addAction(Intent.ACTION_SHUTDOWN);
        getContext().registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){synchronized(lock){restore();if(Intent.ACTION_USER_SWITCHED.equals(i.getAction()))recoverBrightness(ActivityManager.getCurrentUser());}}},filter,Context.RECEIVER_NOT_EXPORTED);
    }
    private final IHeckerGameService.Stub binder=new IHeckerGameService.Stub(){
        @Override public Bundle update(IBinder token,int taskId,int flags){
            int uid=Binder.getCallingUid();enforce(uid);
            long identity=Binder.clearCallingIdentity();
            try{synchronized(lock){
                if(token==null||taskId<0||uid/100000!=ActivityManager.getCurrentUser()||!gameVisible(taskId)){restore();return unavailable();}
                if(!token.equals(owner)){restore();owner=token;user=uid/100000;
                    final IBinder linked=token;death=()->{synchronized(lock){if(linked.equals(owner))restore();}};
                    try{token.linkToDeath(death,0);}catch(RemoteException e){restore();return unavailable();}
                }
                deadline=SystemClock.elapsedRealtime()+5000;handler.removeCallbacks(expiry);handler.postDelayed(expiry,5000);
                try{apply(gameFocused?flags&31:flags&(BRIGHTNESS|BYPASS));}catch(RuntimeException e){restore();return unavailable();}
                Bundle result=new Bundle();result.putInt("cpu",telemetry.cpu(read("/proc/stat")));
                result.putInt("gpu",GameTelemetry.gpu(read(GPU)));
                ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();getContext().getSystemService(ActivityManager.class).getMemoryInfo(info);
                result.putInt("ram",GameTelemetry.ram(info.totalMem,info.availMem));result.putInt("bypass",bypass(flags));result.putBoolean("available",true);return result;
            }}finally{Binder.restoreCallingIdentity(identity);}
        }
        @Override public void end(IBinder token){enforce(Binder.getCallingUid());long id=Binder.clearCallingIdentity();try{synchronized(lock){if(token!=null&&token.equals(owner))restore();}}finally{Binder.restoreCallingIdentity(id);}}
    };
    private void enforce(int uid){
        PackageManager pm=getContext().getPackageManager();
        try{if(pm.getPackageUidAsUser("org.hecker.suite",PackageManager.PackageInfoFlags.of(0),uid/100000)==uid&&pm.checkSignatures("android","org.hecker.suite")==PackageManager.SIGNATURE_MATCH)return;}
        catch(PackageManager.NameNotFoundException ignored){}
        throw new SecurityException("Only platform-signed Hecker Suite may control a game session");
    }
    private boolean gameVisible(int taskId){
        if(!getContext().getSystemService(PowerManager.class).isInteractive()||getContext().getSystemService(KeyguardManager.class).isKeyguardLocked())return false;
        for(ActivityManager.RunningTaskInfo task:getContext().getSystemService(ActivityManager.class).getRunningTasks(30))
            if(task.taskId==taskId&&task.isVisible()&&task.displayId==0&&task.userId==ActivityManager.getCurrentUser()){gameFocused=task.isFocused;return true;}
        return false;
    }
    private void apply(int flags){
        if(flags!=appliedFlags){
            int disabled=(flags&NAV)!=0?StatusBarManager.DISABLE_HOME|StatusBarManager.DISABLE_RECENT|StatusBarManager.DISABLE_BACK:0;
            if((flags&ASSISTANT)!=0)disabled|=StatusBarManager.DISABLE_SEARCH;
            disableNavigation(disabled,user);
            Settings.Global.putInt(getContext().getContentResolver(),EDGE,(flags&EDGES)!=0?1:0);
            Settings.Global.putInt(getContext().getContentResolver(),ASSIST,(flags&ASSISTANT)!=0?1:0);
            keyFlags=flags;
            if((flags&BRIGHTNESS)!=0&&brightnessMode<0){
                brightnessMode=Settings.System.getIntForUser(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0,user);
                Settings.Secure.putIntForUser(getContext().getContentResolver(),RESTORE,brightnessMode,user);
                Settings.System.putIntForUser(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,user);
            }else if((flags&BRIGHTNESS)==0)restoreBrightness();
            appliedFlags=flags;
        }
    }
    private int bypass(int flags){
        File node=new File(CHARGE);if(!node.canRead()||!node.canWrite())return -1;
        Intent battery=getContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int state=0;
        if((flags&BYPASS)!=0){
            int plug=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
            int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_SCALE,0);
            if((plug&(BatteryManager.BATTERY_PLUGGED_AC|BatteryManager.BATTERY_PLUGGED_USB))==0)state=2;
            else if(scale<=0||level*100L<=scale*20L)state=3;else state=1;
        }
        boolean wanted=state==1;
        // The independent kernel inhibitor expires in six seconds without these heartbeats.
        if(wanted||charging){boolean success=write(CHARGE,wanted?"1":"0");charging=wanted&&success;
            if(!success||wanted&&!read(CHARGE).equals("1")){write(CHARGE,"0");charging=false;return 4;}}
        return state;
    }
    private void restoreBrightness(){
        if(brightnessMode<0||user<0)return;
        if(Settings.System.getIntForUser(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0,user)==Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putIntForUser(getContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,brightnessMode,user);
        Settings.Secure.putIntForUser(getContext().getContentResolver(),RESTORE,-1,user);brightnessMode=-1;
    }
    private void recoverBrightness(int targetUser){
        int saved=Settings.Secure.getIntForUser(getContext().getContentResolver(),RESTORE,-1,targetUser);
        if(saved>=0){user=targetUser;brightnessMode=saved;restoreBrightness();user=-1;}
    }
    private void restore(){
        handler.removeCallbacks(expiry);
        if(owner!=null&&death!=null)try{owner.unlinkToDeath(death,0);}catch(java.util.NoSuchElementException ignored){}owner=null;death=null;deadline=0;
        if(charging||new File(CHARGE).exists())write(CHARGE,"0");charging=false;
        if(statusUser>=0)disableNavigation(0,statusUser);keyFlags=0;appliedFlags=0;
        Settings.Global.putInt(getContext().getContentResolver(),EDGE,0);Settings.Global.putInt(getContext().getContentResolver(),ASSIST,0);
        restoreBrightness();user=-1;telemetry.reset();
    }
    private void disableNavigation(int flags,int targetUser){
        try{IStatusBarService service=IStatusBarService.Stub.asInterface(ServiceManager.getService("statusbar"));
            if(service!=null){service.disableForUser(flags,statusToken,"android",targetUser);statusUser=flags==0?-1:targetUser;}
        }catch(RemoteException ignored){}
    }
    public static boolean blocksAssistant(){return (keyFlags&ASSISTANT)!=0;}
    public static boolean blocksKey(int key){
        return (keyFlags&NAV)!=0&&(key==KeyEvent.KEYCODE_HOME||key==KeyEvent.KEYCODE_APP_SWITCH||key==KeyEvent.KEYCODE_BACK)
            ||(keyFlags&ASSISTANT)!=0&&(key==KeyEvent.KEYCODE_ASSIST||key==KeyEvent.KEYCODE_VOICE_ASSIST);
    }
    private static Bundle unavailable(){Bundle b=new Bundle();b.putInt("cpu",-1);b.putInt("gpu",-1);b.putInt("ram",-1);b.putInt("bypass",-1);return b;}
    private static String read(String path){try(BufferedReader r=new BufferedReader(new FileReader(path))){String line=r.readLine();return line==null?"":line.trim();}catch(IOException|SecurityException e){return "";}}
    private static boolean write(String path,String value){try(FileOutputStream out=new FileOutputStream(path)){out.write(value.getBytes(StandardCharsets.US_ASCII));return true;}catch(IOException|SecurityException e){return false;}}
}

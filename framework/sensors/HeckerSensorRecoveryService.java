// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.*;
import android.telecom.TelecomManager;
import com.android.server.SystemService;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.Executors;

/** Suite requests a fixed init action; the app receives no root or property-write capability. */
public final class HeckerSensorRecoveryService extends SystemService {
    private volatile boolean ready;
    private final SensorRecoveryEngine engine;
    public HeckerSensorRecoveryService(Context context){
        super(context);
        engine=new SensorRecoveryEngine(new SensorRecoveryEngine.Platform(){
            public String readiness(int user){return checkReady(user);}
            public String get(String key){return SystemProperties.get(key,"");}
            public void set(String key,String value){SystemProperties.set(key,value);}
            public long now(){return SystemClock.elapsedRealtime();}
            public void pause() throws InterruptedException{Thread.sleep(1000);}
        },Executors.newSingleThreadExecutor());
    }
    @Override public void onStart(){publishBinderService("hecker_sensor_recovery",binder);}
    @Override public void onBootPhase(int phase){if(phase==PHASE_SYSTEM_SERVICES_READY)ready=true;}
    private String checkReady(int user){
        if(!ready)return "unavailable";
        if(!"coral".equals(Build.DEVICE)&&!"flame".equals(Build.DEVICE))return "unsupported_device";
        if(ActivityManager.getCurrentUser()!=user)return "wrong_user";
        KeyguardManager keyguard=getContext().getSystemService(KeyguardManager.class);
        PowerManager power=getContext().getSystemService(PowerManager.class);
        AudioManager audio=getContext().getSystemService(AudioManager.class);
        TelecomManager telecom=getContext().getSystemService(TelecomManager.class);
        SensorManager sensors=getContext().getSystemService(SensorManager.class);
        if(keyguard==null||power==null||audio==null||telecom==null||sensors==null)return "unavailable";
        if(!power.isInteractive()||keyguard.isKeyguardLocked()||telecom.isInCall()||audio.getMode()==AudioManager.MODE_IN_CALL
                ||audio.getMode()==AudioManager.MODE_IN_COMMUNICATION)return "blocked";
        if(sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)==null)return "unsupported_device";
        return "ready";
    }
    private int enforceSuite(){
        int uid=Binder.getCallingUid(),user=uid/100000;
        long identity=Binder.clearCallingIdentity();
        try{
            PackageManager pm=getContext().getPackageManager();
            int actual=pm.getPackageUidAsUser("org.hecker.suite",PackageManager.PackageInfoFlags.of(0),user);
            if(SensorRecoveryAccess.suite(uid,ActivityManager.getCurrentUser(),actual,
                    pm.checkSignatures("android","org.hecker.suite")==PackageManager.SIGNATURE_MATCH))return user;
        }catch(PackageManager.NameNotFoundException ignored){}
        finally{Binder.restoreCallingIdentity(identity);}
        throw new SecurityException("Only the foreground platform-signed Hecker Suite may recover sensors");
    }
    private static Bundle bundle(SensorRecoveryEngine.State state){
        Bundle value=new Bundle();value.putString("status",state.status);
        value.putLong("request",state.request);value.putBoolean("busy",state.busy);return value;
    }
    private final IHeckerSensorRecoveryService.Stub binder=new IHeckerSensorRecoveryService.Stub(){
        @Override public Bundle requestRecovery(){
            int user=enforceSuite();long identity=Binder.clearCallingIdentity();
            try{return bundle(engine.request(user));}finally{Binder.restoreCallingIdentity(identity);}
        }
        @Override public Bundle getState(){
            int user=enforceSuite();long identity=Binder.clearCallingIdentity();
            try{return bundle(engine.state(user));}finally{Binder.restoreCallingIdentity(identity);}
        }
        // Hidden Binder hook: signature is checked against the pinned platform source by the host checker.
        public void onShellCommand(FileDescriptor in,FileDescriptor out,FileDescriptor err,
                String[] args,ShellCallback callback,ResultReceiver receiver){
            if(!SensorRecoveryAccess.shell(Binder.getCallingUid()))throw new SecurityException("Shell only");
            long identity=Binder.clearCallingIdentity();
            try{
                final int user=ActivityManager.getCurrentUser();
                new ShellCommand(){
                @Override public int onCommand(String command){
                    if(command==null)return handleDefaultCommands(command);
                    SensorRecoveryEngine.State state;
                    if("recover".equals(command))state=engine.request(user);
                    else if("status".equals(command))state=engine.state(user);
                    else return handleDefaultCommands(command);
                    PrintWriter output=getOutPrintWriter();output.println("RESULT="+state.status);
                    output.println("REQUEST="+state.request);
                    return "running".equals(state.status)||"service_restarted".equals(state.status)
                            ||"idle".equals(state.status)?0:1;
                }
                @Override public void onHelp(){getOutPrintWriter().println("Sensor recovery: recover | status (manual, foreground unlocked user; no active call)");}
            }.exec(this,in,out,err,args,callback,receiver);}finally{Binder.restoreCallingIdentity(identity);}
        }
    };
}

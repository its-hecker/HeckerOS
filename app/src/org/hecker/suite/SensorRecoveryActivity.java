// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Manual recovery through the authenticated ROM broker, with a physical near/far test. */
public final class SensorRecoveryActivity extends Activity implements SensorEventListener {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final java.util.concurrent.ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static volatile String result="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private SensorManager manager;
    private Sensor sensor;
    private TextView reading,changes,status;
    private Button recover;
    private Boolean wasNear;
    private int transitions;
    private boolean resumed;
    private String displayed;
    private final Runnable refresh=new Runnable(){public void run(){
        if(!resumed)return;
        recover.setEnabled(supported()&&sensor!=null&&!BUSY.get());
        String current=BUSY.get()?"running":result;
        if(!current.equals(displayed)){
            displayed=current;status.setText(message(current));
            if("service_restarted".equals(current)){
                transitions=0;wasNear=null;reading.setText(R.string.sensor_recovery_waiting);
                changes.setText(getString(R.string.sensor_recovery_changes,0));subscribe();
            }
        }
        main.postDelayed(this,500);
    }};

    static boolean supported(){return "coral".equals(Build.DEVICE)||"flame".equals(Build.DEVICE);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        manager=getSystemService(SensorManager.class);
        sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        LinearLayout page=Ui.page(this,R.string.sensor_recovery_title);
        LinearLayout info=Ui.card(this);
        info.addView(Ui.text(this,getString(R.string.sensor_recovery_intro),14,false));page.addView(info);
        LinearLayout live=Ui.card(this);
        reading=Ui.text(this,getString(R.string.sensor_recovery_waiting),22,true);live.addView(reading);
        changes=Ui.text(this,getString(R.string.sensor_recovery_changes,0),14,false);live.addView(changes);
        live.addView(Ui.text(this,getString(R.string.sensor_recovery_test),14,false));page.addView(live);
        LinearLayout action=Ui.card(this);
        recover=Ui.button(this);recover.setText(R.string.sensor_recovery_action);action.addView(recover);
        status=Ui.text(this,"",14,false);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        action.addView(status);page.addView(action);
        recover.setEnabled(supported()&&sensor!=null);
        recover.setOnClickListener(v->{
            if(!allowed()){status.setText(R.string.sensor_recovery_blocked);return;}
            new AlertDialog.Builder(this).setTitle(R.string.sensor_recovery_action)
                    .setMessage(R.string.sensor_recovery_confirmation)
                    .setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(R.string.sensor_recovery_action,(dialog,which)->startRecovery()).show();
        });
        if(!supported()||sensor==null)status.setText(R.string.sensor_recovery_unsupported);
        Ui.scroll(this,page);
    }
    private boolean allowed(){
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        AudioManager audio=getSystemService(AudioManager.class);
        return supported()&&sensor!=null&&keyguard!=null&&!keyguard.isDeviceLocked()
                &&audio!=null&&audio.getMode()!=AudioManager.MODE_IN_CALL
                &&audio.getMode()!=AudioManager.MODE_IN_COMMUNICATION;
    }
    private void startRecovery(){
        if(!allowed()){status.setText(R.string.sensor_recovery_blocked);return;}
        if(!BUSY.compareAndSet(false,true)){status.setText(R.string.sensor_recovery_busy);return;}
        result="running";
        WORKER.execute(()->monitor(SensorRecoveryClient.call(true)));
    }
    private static void monitor(Bundle state){
        try{
            for(int n=0;state.getBoolean("busy",false)&&n<50;n++){
                result="running";Thread.sleep(500);state=SensorRecoveryClient.call(false);
            }
            result=state.getBoolean("busy",false)?"restart_not_verified":state.getString("status","unavailable");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();result="restart_not_verified";}
        finally{BUSY.set(false);}
    }
    private int message(String value){
        switch(value){
            case "running":return R.string.sensor_recovery_running;
            case "busy":case "cooldown":return R.string.sensor_recovery_busy;
            case "service_restarted":return R.string.sensor_recovery_restarted;
            case "unsupported_device":case "unsupported_service":return R.string.sensor_recovery_unsupported;
            case "unavailable":return R.string.sensor_recovery_unavailable;
            case "blocked":case "wrong_user":return R.string.sensor_recovery_blocked;
            case "pid_unavailable":case "restart_not_verified":return R.string.sensor_recovery_unverified;
            case "idle":case "":return supported()&&sensor!=null?R.string.sensor_recovery_idle:R.string.sensor_recovery_unsupported;
            default:return R.string.sensor_recovery_unverified;
        }
    }
    private void subscribe(){
        if(manager==null||sensor==null)return;
        manager.unregisterListener(this);
        if(!manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL))reading.setText(R.string.sensor_recovery_waiting);
    }
    @Override protected void onResume(){super.onResume();resumed=true;subscribe();main.post(refresh);if(BUSY.compareAndSet(false,true))WORKER.execute(()->monitor(SensorRecoveryClient.call(false)));}
    @Override protected void onPause(){resumed=false;main.removeCallbacks(refresh);if(manager!=null)manager.unregisterListener(this);super.onPause();}
    @Override public void onSensorChanged(SensorEvent event){
        if(!resumed||event.values.length==0||!Float.isFinite(event.values[0]))return;
        float value=event.values[0];if(value<0||event.sensor.getMaximumRange()<=0)return;
        boolean near=value<event.sensor.getMaximumRange();
        if(wasNear!=null&&wasNear!=near)transitions++;
        wasNear=near;reading.setText(getString(R.string.sensor_recovery_reading,value,getString(near?R.string.sensor_recovery_near:R.string.sensor_recovery_far)));
        changes.setText(getString(R.string.sensor_recovery_changes,transitions));
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}

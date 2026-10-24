// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Long-press actions leave ordinary taps, drags and status-bar swipes native. */
public final class HeckerStatusBarPopup {
    public static final String SETTING="hecker_status_popup";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static SystemUIDialog dialog;
    private static View owner;
    private static float x,y;
    private static boolean opened,dispatching;
    private static Context application;
    private static final Runnable LONG_PRESS=()->{
        View view=owner;if(view==null||!view.isAttachedToWindow()||locked(view.getContext()))return;
        opened=true;view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        MotionEvent cancel=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,x,y,0);
        dispatching=true;try{view.dispatchTouchEvent(cancel);}finally{dispatching=false;cancel.recycle();}
        try{show(view.getContext());}catch(RuntimeException unavailable){cancel();android.util.Log.w("HeckerStatusPopup","Popup unavailable",unavailable);}
    };
    private HeckerStatusBarPopup(){}
    public static void start(Context context){
        if(application!=null)return;application=context;
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_USER_SWITCHED);f.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){dismiss();cancel();}},f,Context.RECEIVER_NOT_EXPORTED);
    }
    private static boolean locked(Context context){return context.getSystemService(KeyguardManager.class).isKeyguardLocked();}
    public static boolean onTouch(View view,MotionEvent event){
        try{return handleTouch(view,event);}catch(RuntimeException unavailable){cancel();return false;}
    }
    private static boolean handleTouch(View view,MotionEvent event){
        if(dispatching)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            cancel();Context c=view.getContext();
            if(Settings.Secure.getIntForUser(c.getContentResolver(),SETTING,1,ActivityManager.getCurrentUser())==0||locked(c)
                    ||c.getSystemService(android.view.accessibility.AccessibilityManager.class).isTouchExplorationEnabled())return false;
            int[] location=new int[2];view.getLocationOnScreen(location);
            float screenX=event.getX()+location[0],screenY=event.getY()+location[1];
            if(!hit(view,"clock",screenX,screenY)&&!hit(view,"system_icon_area",screenX,screenY)
                    &&!hit(view,"statusIcons",screenX,screenY)&&!hit(view,"battery",screenX,screenY))return false;
            owner=view;x=event.getX();y=event.getY();opened=false;
            MAIN.postDelayed(LONG_PRESS,ViewConfiguration.getLongPressTimeout());return false;
        }
        if(owner!=view)return false;
        if(event.getPointerCount()>1||event.getActionMasked()==MotionEvent.ACTION_MOVE
                &&Math.hypot(event.getX()-x,event.getY()-y)>ViewConfiguration.get(view.getContext()).getScaledTouchSlop()){
            boolean consumed=opened;cancel();return consumed;
        }
        boolean consumed=opened;
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)cancel();
        return consumed;
    }
    private static boolean hit(View root,String name,float x,float y){
        int id=root.getResources().getIdentifier(name,"id","com.android.systemui");
        if(id==0)id=root.getResources().getIdentifier(name,"id","com.android.systemui.res");
        View view=id==0?null:root.findViewById(id);Rect bounds=new Rect();
        return view!=null&&view.isShown()&&view.getGlobalVisibleRect(bounds)&&bounds.contains((int)x,(int)y);
    }
    private static void cancel(){MAIN.removeCallbacks(LONG_PRESS);owner=null;opened=false;}
    private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private static void show(Context context){
        dismiss();dialog=new SystemUIDialog(context);dialog.setTitle(R.string.hecker_status_popup_title);
        LinearLayout items=new LinearLayout(context);items.setOrientation(LinearLayout.VERTICAL);items.setPadding(dp(context,16),dp(context,8),dp(context,16),dp(context,16));
        GradientDrawable background=new GradientDrawable();background.setColor(0xE510293F);background.setCornerRadius(dp(context,28));items.setBackground(background);
        action(context,items,R.string.hecker_popup_clock,Settings.ACTION_DATE_SETTINGS);
        action(context,items,R.string.hecker_popup_wifi,Settings.ACTION_WIFI_SETTINGS);
        action(context,items,R.string.hecker_popup_bluetooth,Settings.ACTION_BLUETOOTH_SETTINGS);
        action(context,items,R.string.hecker_popup_mobile,Settings.ACTION_DATA_USAGE_SETTINGS);
        action(context,items,R.string.hecker_popup_battery,Intent.ACTION_POWER_USAGE_SUMMARY);
        Button boost=button(context,R.string.hecker_memory_boost);boost.setOnClickListener(v->{dismiss();boost(context);});items.addView(boost);
        action(context,items,R.string.hecker_popup_settings,Settings.ACTION_SETTINGS);
        ScrollView scroll=new ScrollView(context);scroll.addView(items);dialog.setView(scroll);
        dialog.getWindow().setBackgroundBlurRadius(dp(context,36));dialog.show();
    }
    private static Button button(Context c,int title){Button b=new Button(c);b.setAllCaps(false);b.setText(title);b.setTextColor(0xFFEAF4FF);b.setMinHeight(dp(c,48));return b;}
    private static void action(Context c,LinearLayout items,int label,String action){Button b=button(c,label);b.setOnClickListener(v->{dismiss();HeckerQsActions.settings(action);});items.addView(b);}
    public static void dismiss(){if(dialog!=null){SystemUIDialog old=dialog;dialog=null;old.dismiss();}}
    public static void boost(Context c){
        if(locked(c))return;final int user=ActivityManager.getCurrentUser();
        WORKER.execute(()->{
            Bundle result=null;try{IHeckerMemoryService service=IHeckerMemoryService.Stub.asInterface(ServiceManager.getService("hecker_memory"));if(service!=null)result=service.boost();}
            catch(RemoteException|RuntimeException ignored){}
            final Bundle outcome=result;MAIN.post(()->{
                if(user!=ActivityManager.getCurrentUser()||locked(c))return;
                String text=outcome==null||!outcome.getBoolean("available")?c.getString(R.string.hecker_memory_failed)
                        :outcome.getInt("processes")==0?c.getString(R.string.hecker_memory_none)
                        :c.getString(R.string.hecker_memory_done,outcome.getInt("apps"),outcome.getInt("processes"));
                Toast.makeText(c,text,Toast.LENGTH_LONG).show();
            });
        });
    }
}

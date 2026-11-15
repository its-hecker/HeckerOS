// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import com.android.launcher3.LauncherFiles;
import java.util.WeakHashMap;

/** Called only by Workspace's empty-space touch path; app/widget taps stay native. */
public final class HeckerWorkspaceGestures {
    private static final WeakHashMap<View, GestureDetector> DETECTORS = new WeakHashMap<>();
    private static final WeakHashMap<View, HeckerHomeSwipe> SWIPES = new WeakHashMap<>();
    public static boolean touch(View workspace, MotionEvent event, boolean normal) {
        Context context=workspace.getContext();
        android.content.SharedPreferences prefs=context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,Context.MODE_PRIVATE);
        if(!normal||context.getSystemService(AccessibilityManager.class).isTouchExplorationEnabled()){
            cancelDetector(workspace,event);SWIPES.remove(workspace);return false;
        }
        HeckerHomeSwipe swipe=SWIPES.get(workspace);
        if(swipe==null){swipe=new HeckerHomeSwipe();SWIPES.put(workspace,swipe);}
        // Trigger when the direction is established, not when the finger lifts.
        // Keep native horizontal paging, icon taps and double-tap sleep intact.
        final int action=event.getActionMasked();
        final float slop=android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        final float threshold=Math.max(32f*context.getResources().getDisplayMetrics().density,slop*2.5f);
        boolean openShade=false;
        if(event.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL
                ||action==MotionEvent.ACTION_POINTER_DOWN)swipe.cancel();
        else if(action==MotionEvent.ACTION_DOWN)swipe.down(event.getX(),event.getY(),event.getEventTime());
        else if(action==MotionEvent.ACTION_MOVE)openShade=swipe.move(event.getX(),event.getY(),event.getEventTime(),slop,threshold);
        else if(action==MotionEvent.ACTION_UP)openShade=swipe.up(event.getX(),event.getY(),event.getEventTime(),threshold);
        if(openShade&&prefs.getBoolean("hecker_swipe_down",true)){
            cancelDetector(workspace,event);
            try{
                Object status=context.getSystemService("statusbar");
                Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(status);
                return true;
            }catch(ReflectiveOperationException|RuntimeException failure){
                android.util.Log.w("HeckerLauncher","Could not expand quick settings shade",failure);
            }
        }
        if(!prefs.getBoolean("hecker_double_tap_sleep",true)){cancelDetector(workspace,event);return false;}
        GestureDetector detector=DETECTORS.get(workspace);
        if(detector==null){
            // Application context avoids a detector retaining its weak-key Workspace/Activity.
            Context app=context.getApplicationContext();
            HeckerHomeSwipe capturedSwipe=swipe;
            detector=new GestureDetector(app,new GestureDetector.SimpleOnGestureListener(){
                boolean armed;float downX,downY;
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public void onLongPress(MotionEvent e){armed=false;capturedSwipe.cancel();}
                @Override public boolean onDoubleTap(MotionEvent e){armed=true;downX=e.getX();downY=e.getY();return false;}
                @Override public boolean onDoubleTapEvent(MotionEvent e){
                    if(e.getActionMasked()==MotionEvent.ACTION_CANCEL||e.getPointerCount()>1){armed=false;return false;}
                    if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(e.getX()-downX,e.getY()-downY)
                            >android.view.ViewConfiguration.get(app).getScaledTouchSlop()){armed=false;return false;}
                    // Empty-workspace double taps commit on the second down, without
                    // waiting for finger-up or the native long-press cancellation path.
                    if(!armed||e.getActionMasked()!=MotionEvent.ACTION_DOWN)return false;
                    armed=false;
                    try{
                        PowerManager power=app.getSystemService(PowerManager.class);
                        PowerManager.class.getMethod("goToSleep",long.class).invoke(power,SystemClock.uptimeMillis());
                        return true;
                    }catch(ReflectiveOperationException|RuntimeException failure){
                        android.util.Log.w("HeckerLauncher","Could not put display to sleep",failure);return false;
                    }
                }
            });
            DETECTORS.put(workspace,detector);
        }
        // GestureDetector's onDown must not consume Workspace's normal swipe/long-press.
        boolean handled=detector.onTouchEvent(event);
        return handled&&event.getActionMasked()==MotionEvent.ACTION_UP;
    }
    private static void cancelDetector(View workspace,MotionEvent event){
        GestureDetector previous=DETECTORS.remove(workspace);
        if(previous==null)return;
        MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
        previous.onTouchEvent(cancel);cancel.recycle();
    }
    private HeckerWorkspaceGestures(){}
}

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
    public static boolean touch(View workspace, MotionEvent event, boolean normal) {
        Context context=workspace.getContext();
        if(!normal||context.getSystemService(AccessibilityManager.class).isTouchExplorationEnabled()
                ||!context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY,Context.MODE_PRIVATE)
                        .getBoolean("hecker_double_tap_sleep",true)){
            DETECTORS.remove(workspace);return false;
        }
        GestureDetector detector=DETECTORS.get(workspace);
        if(detector==null){
            // Application context avoids a detector retaining its weak-key Workspace/Activity.
            Context app=context.getApplicationContext();
            detector=new GestureDetector(app,new GestureDetector.SimpleOnGestureListener(){
                boolean armed;float downX,downY;
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onDoubleTap(MotionEvent e){armed=true;downX=e.getX();downY=e.getY();return false;}
                @Override public boolean onDoubleTapEvent(MotionEvent e){
                    if(e.getActionMasked()==MotionEvent.ACTION_CANCEL||e.getPointerCount()>1){armed=false;return false;}
                    if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.hypot(e.getX()-downX,e.getY()-downY)
                            >android.view.ViewConfiguration.get(app).getScaledTouchSlop()){armed=false;return false;}
                    if(!armed||e.getActionMasked()!=MotionEvent.ACTION_UP)return false;
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
    private HeckerWorkspaceGestures(){}
}

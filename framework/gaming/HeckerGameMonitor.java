// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.TaskStackListener;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

/** Event-driven native launch monitor; the dashboard process exists only during a game. */
final class HeckerGameMonitor {
    private static final String KEY="hecker_game_monitor_config";
    private final Context context;
    private final Handler handler;
    private int task=-1,user=-1;
    private JSONObject cachedConfig;
    private boolean configLoaded;
    private String activePackage;
    private final Runnable check=this::check;
    HeckerGameMonitor(Context context,Handler handler){
        this.context=context;this.handler=handler;
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY),false,
            new ContentObserver(handler){@Override public void onChange(boolean self){task=-1;activePackage=null;configLoaded=false;schedule();}},UserHandle.USER_ALL);
        try{ActivityTaskManager.getService().registerTaskStackListener(new TaskStackListener(){
            @Override public void onTaskStackChanged(){schedule();}
            @Override public void onTaskFocusChanged(int id,boolean focused){schedule();}
            @Override public void onTaskRemoved(int id){schedule();}
        });}catch(RemoteException error){android.util.Slog.w("HeckerGame","Task monitor unavailable",error);}
        IntentFilter filter=new IntentFilter(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_SWITCHED);filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){task=-1;schedule();}},filter,null,handler,Context.RECEIVER_NOT_EXPORTED);
        schedule();
    }
    private void schedule(){handler.removeCallbacks(check);handler.postDelayed(check,120);}
    private static boolean contains(JSONArray values,String pkg){
        if(values==null)return false;
        for(int i=0;i<Math.min(4096,values.length());i++)if(pkg.equals(values.optString(i)))return true;
        return false;
    }
    private void check(){
        try{
            int current=ActivityManager.getCurrentUser();
            if(user!=current){user=current;task=-1;activePackage=null;configLoaded=false;}
            if(!context.getSystemService(PowerManager.class).isInteractive()
                    ||context.getSystemService(KeyguardManager.class).isKeyguardLocked()){task=-1;return;}
            // Task-stack changes are frequent (including swipe/Recents). Avoid
            // re-reading Secure settings and reparsing JSON for every event.
            // The content observer and user switch invalidate this snapshot.
            if (!configLoaded) {
                String text = Settings.Secure.getStringForUser(context.getContentResolver(), KEY, user);
                cachedConfig = null;
                if (text != null && text.length() <= 131072) {
                    try {
                        cachedConfig = new JSONObject(text);
                    } catch (org.json.JSONException malformed) {
                        android.util.Slog.w("HeckerGame",
                                "Ignoring malformed game-monitor configuration", malformed);
                    }
                }
                // Keep invalid/disabled states cached until an actual settings or
                // user change, rather than retrying JSON parsing on each task event.
                configLoaded = true;
            }
            JSONObject config = cachedConfig;
            if (config == null || !config.optBoolean("enabled", false)) {
                task = -1; activePackage = null; return;
            }
            for(ActivityManager.RunningTaskInfo top:context.getSystemService(ActivityManager.class).getRunningTasks(8)){
                if(!top.isFocused||!top.isVisible()||top.userId!=user||top.displayId!=0||top.topActivity==null)continue;
                String pkg=top.topActivity.getPackageName();
                // A focused game already being monitored requires no repeated PM
                // category lookup. Still re-check if the top package changes inside
                // the same task; configuration changes reset the task above.
                if (task == top.taskId && pkg.equals(activePackage)) return;
                ApplicationInfo info=context.getPackageManager().getApplicationInfoAsUser(pkg,0,user);
                boolean game=!contains(config.optJSONArray("excluded"),pkg)
                    &&(contains(config.optJSONArray("added"),pkg)
                        ||config.optBoolean("automatic",true)&&(info.category==ApplicationInfo.CATEGORY_GAME
                            ||(info.flags&ApplicationInfo.FLAG_IS_GAME)!=0));
                if(!game){task=-1;activePackage=null;return;}
                context.startForegroundServiceAsUser(new Intent().setComponent(new ComponentName(
                    "org.hecker.suite","org.hecker.suite.GamingService")),UserHandle.of(user));
                task=top.taskId;activePackage=pkg;return;
            }
            task=-1;activePackage=null;
        }catch(Exception failure){task=-1;activePackage=null;android.util.Slog.w("HeckerGame","Game launch monitoring unavailable",failure);}
    }
}

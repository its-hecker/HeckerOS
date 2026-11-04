# SPDX-License-Identifier: Apache-2.0
"""Run the native task monitor through real foreground/user/configuration changes."""
from pathlib import Path
import unittest
from test_motion_island import java_run
ROOT=Path(__file__).resolve().parents[1]
class GameMonitorTests(unittest.TestCase):
    def test_monitor_launches_only_current_user_games_and_rearms_after_exit(self):
        stubs={
'android/os/Handler.java':'''package android.os;public class Handler{public final java.util.Set<Runnable> work=new java.util.LinkedHashSet<>();public void removeCallbacks(Runnable r){work.remove(r);}public void postDelayed(Runnable r,long time){work.add(r);}public void drain(){for(Runnable r:new java.util.ArrayList<>(work)){work.remove(r);r.run();}}}''',
'android/os/RemoteException.java':'package android.os;public class RemoteException extends Exception{}',
'android/os/PowerManager.java':'package android.os;public class PowerManager{public boolean awake=true;public boolean isInteractive(){return awake;}}',
'android/os/UserHandle.java':'package android.os;public class UserHandle{public static final int USER_ALL=-1;public int id;public static UserHandle of(int user){UserHandle v=new UserHandle();v.id=user;return v;}}',
'android/app/KeyguardManager.java':'package android.app;public class KeyguardManager{public boolean locked;public boolean isKeyguardLocked(){return locked;}}',
'android/app/TaskStackListener.java':'package android.app;public class TaskStackListener{public void onTaskStackChanged(){}public void onTaskFocusChanged(int id,boolean focused){}public void onTaskRemoved(int id){}}',
'android/app/ActivityTaskManager.java':'''package android.app;public class ActivityTaskManager{public static TaskStackListener listener;public static ActivityTaskManager getService(){return new ActivityTaskManager();}public void registerTaskStackListener(TaskStackListener l)throws android.os.RemoteException{listener=l;}}''',
'android/app/ActivityManager.java':'''package android.app;public class ActivityManager{public static int user;public static int getCurrentUser(){return user;}public java.util.List<RunningTaskInfo> tasks=new java.util.ArrayList<>();public java.util.List<RunningTaskInfo> getRunningTasks(int max){return tasks;}public static class RunningTaskInfo{public boolean isFocused=true,visible=true;public int taskId,userId,displayId;public android.content.ComponentName topActivity;public boolean isVisible(){return visible;}}}''',
'android/content/ComponentName.java':'package android.content;public record ComponentName(String pkg,String cls){public String getPackageName(){return pkg;}}',
'android/content/Intent.java':'package android.content;public class Intent{public static final String ACTION_USER_PRESENT="present",ACTION_USER_SWITCHED="switch",ACTION_SCREEN_OFF="off";public ComponentName component;public Intent(){}public Intent setComponent(ComponentName value){component=value;return this;}}',
'android/content/IntentFilter.java':'package android.content;public class IntentFilter{public IntentFilter(String a){}public void addAction(String a){}}',
'android/content/BroadcastReceiver.java':'package android.content;public abstract class BroadcastReceiver{public abstract void onReceive(Context c,Intent i);}',
'android/content/ContentResolver.java':'package android.content;public class ContentResolver{public android.database.ContentObserver observer;public void registerContentObserver(Object uri,boolean tree,android.database.ContentObserver observer,int user){this.observer=observer;}}',
'android/database/ContentObserver.java':'package android.database;public class ContentObserver{public ContentObserver(android.os.Handler h){}public void onChange(boolean self){}}',
'android/content/pm/ApplicationInfo.java':'package android.content.pm;public class ApplicationInfo{public static final int CATEGORY_GAME=0,FLAG_IS_GAME=1;public int category=1,flags;}',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager{public final java.util.Map<String,ApplicationInfo> infos=new java.util.HashMap<>();public ApplicationInfo getApplicationInfoAsUser(String pkg,int flags,int user){return infos.get(pkg);}}',
'android/content/Context.java':'''package android.content;public class Context{public static int RECEIVER_NOT_EXPORTED=4;public final ContentResolver resolver=new ContentResolver();public final android.app.ActivityManager am=new android.app.ActivityManager();public final android.app.KeyguardManager keyguard=new android.app.KeyguardManager();public final android.os.PowerManager power=new android.os.PowerManager();public final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();public int starts,startedUser=-1;public Intent last;public ContentResolver getContentResolver(){return resolver;}public android.content.pm.PackageManager getPackageManager(){return pm;}public <T>T getSystemService(Class<T> c){return c.cast(c==android.app.ActivityManager.class?am:c==android.app.KeyguardManager.class?keyguard:power);}public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,String p,android.os.Handler h,int flags){return null;}public ComponentName startForegroundServiceAsUser(Intent intent,android.os.UserHandle user){starts++;startedUser=user.id;last=intent;return intent.component;}}''',
'android/provider/Settings.java':'package android.provider;public class Settings{public static class Secure{public static int requestedUser=-1;public static Object getUriFor(String key){return key;}public static String getStringForUser(android.content.ContentResolver r,String key,int user){requestedUser=user;return "fixture";}}}',
'android/util/Slog.java':'package android.util;public class Slog{public static int w(String t,String m,Throwable e){return 0;}}',
'org/json/JSONArray.java':'package org.json;public class JSONArray{final java.util.List<String> values;public JSONArray(java.util.List<String> values){this.values=values;}public int length(){return values.size();}public String optString(int i){return values.get(i);}}',
'org/json/JSONObject.java':'package org.json;public class JSONObject{public static boolean enabled,automatic=true;public static final java.util.List<String> added=new java.util.ArrayList<>(),excluded=new java.util.ArrayList<>();public JSONObject(String text){}public boolean optBoolean(String key,boolean fallback){return key.equals("enabled")?enabled:automatic;}public JSONArray optJSONArray(String key){return new JSONArray(key.equals("added")?added:excluded);}}',
'com/android/server/hecker/MonitorProbe.java':'''package com.android.server.hecker;
import android.app.*;import android.content.*;import android.content.pm.*;import android.os.*;import org.json.JSONObject;
public class MonitorProbe{
 static void check(boolean v){if(!v)throw new AssertionError();}
 static ActivityManager.RunningTaskInfo task(int id,String pkg){var t=new ActivityManager.RunningTaskInfo();t.taskId=id;t.topActivity=new ComponentName(pkg,"Main");return t;}
 static void event(Handler h){ActivityTaskManager.listener.onTaskStackChanged();h.drain();}
 public static void main(String[]args){
 Context c=new Context();Handler h=new Handler();var game=new ApplicationInfo();game.category=0;c.pm.infos.put("game",game);c.pm.infos.put("home",new ApplicationInfo());
 c.am.tasks.add(task(1,"game"));new HeckerGameMonitor(c,h);h.drain();check(c.starts==0);
 JSONObject.enabled=true;c.resolver.observer.onChange(false);h.drain();check(c.starts==1&&c.startedUser==0&&c.last.component.cls().equals("org.hecker.suite.GamingService"));
 event(h);event(h);check(c.starts==1);
 c.am.tasks=java.util.List.of(task(2,"home"));event(h);check(c.starts==1);
 c.am.tasks=java.util.List.of(task(1,"game"));event(h);check(c.starts==2); // Re-open after the dashboard stopped.
 c.keyguard.locked=true;event(h);check(c.starts==2);c.keyguard.locked=false;c.power.awake=false;event(h);check(c.starts==2);c.power.awake=true;
 JSONObject.excluded.add("game");event(h);check(c.starts==2);JSONObject.excluded.clear();JSONObject.automatic=false;event(h);check(c.starts==2);
 JSONObject.added.add("game");event(h);check(c.starts==3);
 ActivityManager.user=10;event(h);check(c.starts==3);c.am.tasks.get(0).userId=10;event(h);check(c.starts==4&&c.startedUser==10&&android.provider.Settings.Secure.requestedUser==10);
 JSONObject.enabled=false;c.resolver.observer.onChange(false);h.drain();check(c.starts==4);
 System.out.println("Native GameSpace monitor: opt-in, foreground, no duplicate start, re-open, locked/screen/user isolation and manual/automatic/excluded game rules PASS");
 }}'''}
        java_run(stubs,[ROOT/'framework/gaming/HeckerGameMonitor.java'],'com.android.server.hecker.MonitorProbe')
if __name__=='__main__':unittest.main()

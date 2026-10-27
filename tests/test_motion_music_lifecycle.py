#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Drive real coordinator/session callbacks through music, gesture and window lifetimes."""
import unittest
from test_music_sessions import STUBS, ROOT
from test_motion_island import java_run

class MotionMusicLifecycleTests(unittest.TestCase):
    def test_pause_callback_before_event_delayed_resume_and_bounded_windows(self):
        sources = dict(STUBS)
        sources.update({
'android/os/SystemClock.java': 'package android.os;public class SystemClock{public static long now=10000;public static long elapsedRealtime(){return now;}}',
'android/os/Build.java': 'package android.os;public class Build{public static String DEVICE="coral";}',
'android/os/PowerManager.java': 'package android.os;public class PowerManager{public static String ACTION_POWER_SAVE_MODE_CHANGED="power";public boolean save;public boolean isInteractive(){return true;}public boolean isPowerSaveMode(){return save;}}',
'android/os/Handler.java': '''package android.os;public class Handler{
 public static java.util.List<Handler> all=new java.util.ArrayList<>();public java.util.Map<Runnable,Long> tasks=new java.util.LinkedHashMap<>();public Handler(Looper l){all.add(this);}
 public void postDelayed(Runnable r,long delay){tasks.put(r,SystemClock.now+delay);}public void removeCallbacks(Runnable r){tasks.remove(r);}public void removeCallbacksAndMessages(Object o){tasks.clear();}
 public static void due(){for(Handler h:all)for(var e:new java.util.ArrayList<>(h.tasks.entrySet()))if(e.getValue()<=SystemClock.now){h.tasks.remove(e.getKey());e.getKey().run();}}
}''',
'android/animation/ValueAnimator.java': 'package android.animation;public class ValueAnimator{public static boolean areAnimatorsEnabled(){return true;}}',
'android/database/ContentObserver.java': 'package android.database;public class ContentObserver{public ContentObserver(android.os.Handler h){}public void onChange(boolean b){}}',
'android/content/ContentResolver.java': 'package android.content;public class ContentResolver{public void registerContentObserver(Object uri,boolean all,android.database.ContentObserver o,int user){}public void unregisterContentObserver(android.database.ContentObserver o){}}',
'android/provider/Settings.java': 'package android.provider;public class Settings{public static class Secure{public static Object getUriFor(String k){return k;}public static int getIntForUser(android.content.ContentResolver r,String k,int d,int u){return 1;}}}',
'android/content/res/Resources.java': 'package android.content.res;public class Resources{public static class Metrics{public float density=1;}public Metrics getDisplayMetrics(){return new Metrics();}}',
'android/content/Context.java': '''package android.content;public class Context{
 public static int RECEIVER_NOT_EXPORTED=4,RECEIVER_EXPORTED=2;public android.media.session.MediaSessionManager manager=new android.media.session.MediaSessionManager();public android.os.PowerManager power=new android.os.PowerManager();public android.view.WindowManager windows=new android.view.WindowManager();
 public java.util.Map<BroadcastReceiver,IntentFilter> receivers=new java.util.LinkedHashMap<>();
 public <T>T getSystemService(Class<T> t){Object v=t==android.os.PowerManager.class?power:t==android.view.WindowManager.class?windows:manager;return t.cast(v);}
 public java.util.concurrent.Executor getMainExecutor(){return Runnable::run;}
 public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receivers.put(r,f);}
 public void registerReceiver(BroadcastReceiver r,IntentFilter f,String permission,android.os.Handler h,int flags){receivers.put(r,f);}
 public void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}
 public void emit(Intent i){for(var e:new java.util.ArrayList<>(receivers.entrySet()))if(e.getValue().actions.contains(i.getAction()))e.getKey().onReceive(this,i);}
 public ContentResolver getContentResolver(){return new ContentResolver();}public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public String getString(int i,Object... args){return "music";}public String getPackageName(){return "com.android.systemui";}public void sendBroadcastAsUser(Intent i,android.os.UserHandle u){}
}''',
'android/content/IntentFilter.java': 'package android.content;public class IntentFilter{public java.util.Set<String> actions=new java.util.HashSet<>();public IntentFilter(String a){actions.add(a);}public void addAction(String a){actions.add(a);}}',
'android/content/Intent.java': '''package android.content;public class Intent{
 public static String ACTION_USER_SWITCHED="user",ACTION_CONFIGURATION_CHANGED="config",ACTION_SCREEN_OFF="off",ACTION_SCREEN_ON="on";public final String action;public java.util.Map<String,Object> extra=new java.util.HashMap<>();public Intent(String a){action=a;}public String getAction(){return action;}
 public Intent setPackage(String p){return this;}public Intent putExtra(String k,Object v){extra.put(k,v);return this;}
 public int getIntExtra(String k,int d){return extra.containsKey(k)?((Number)extra.get(k)).intValue():d;}public long getLongExtra(String k,long d){return extra.containsKey(k)?((Number)extra.get(k)).longValue():d;}
 public String getStringExtra(String k){return (String)extra.get(k);}public <T>T getParcelableExtra(String k,Class<T> c){return c.cast(extra.get(k));}
}''',
'android/view/View.java': 'package android.view;public class View{public interface OnClickListener{void onClick(View v);}public OnClickListener click;public void setOnClickListener(OnClickListener l){click=l;}public void performClick(){click.onClick(this);}}',
'android/view/Gravity.java': 'package android.view;public class Gravity{public static int TOP=48,CENTER_HORIZONTAL=1;}',
'android/graphics/PixelFormat.java': 'package android.graphics;public class PixelFormat{public static int TRANSLUCENT=-3;}',
'android/view/WindowManager.java': '''package android.view;public class WindowManager{
 public final java.util.Map<View,LayoutParams> views=new java.util.LinkedHashMap<>();public void addView(View v,LayoutParams p){views.put(v,p);}public void removeViewImmediate(View v){views.remove(v);}
 public Metrics getCurrentWindowMetrics(){return new Metrics();}public static class Metrics{public Metrics getBounds(){return this;}public int width(){return 1080;}}
 public static class LayoutParams{public static int FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCHABLE=16,FLAG_NOT_TOUCH_MODAL=32,FLAG_LAYOUT_IN_SCREEN=256,LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER=2;public int width,height,flags,gravity,y,layoutInDisplayCutoutMode;public LayoutParams(int w,int h,int type,int flags,int format){width=w;height=h;this.flags=flags;}public void setFitInsetsTypes(int t){}public void setTitle(String s){}public void setTrustedOverlay(){}}
}''',
'android/app/Dialog.java': 'package android.app;public class Dialog{public boolean showing=true;public Runnable dismissed;public void dismiss(){showing=false;if(dismissed!=null)dismissed.run();}}',
'com/android/systemui/hecker/HeckerMotionMediaDialog.java': 'package com.android.systemui.hecker;public class HeckerMotionMediaDialog{public static android.app.Dialog last;public static android.app.Dialog show(android.content.Context c,android.media.session.MediaSession.Token t,Runnable r){last=new android.app.Dialog();last.dismissed=r;return last;}}',
'com/android/systemui/hecker/MotionIslandView.java': 'package com.android.systemui.hecker;public class MotionIslandView extends android.view.View{public static MotionIslandView last;public MotionIslandView(android.content.Context c){last=this;}public void update(HeckerQsMedia.Snapshot m,String l,boolean a){}public void stop(){}}',
'com/android/systemui/hecker/MotionAstronautView.java': 'package com.android.systemui.hecker;public class MotionAstronautView extends android.view.View{public static int direction,action;public MotionAstronautView(android.content.Context c){}public void update(MotionIslandState s,boolean animate){direction=s.direction;action=s.action;}public void stop(){}}',
        })
        sources['android/os/UserHandle.java'] = sources['android/os/UserHandle.java'].replace('public int id;', 'public static int USER_ALL=-1;public int id;')
        names = ('playing play paused pause skipped next previous_done previous volume volume_up volume_down forward rewind track_mode volume_mode seek_mode').split()
        sources['com/android/systemui/res/R.java'] = 'package com.android.systemui.res;public class R{public static class string{' + ''.join('public static int hecker_motion_'+n+'=1;' for n in names) + '}}'
        sources['com/android/systemui/hecker/LifecycleTest.java'] = r'''
package com.android.systemui.hecker;
import android.content.*;import android.media.session.*;import android.os.*;import android.view.*;
public class LifecycleTest{
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static void event(Context c,MediaController m,int action,int side){SystemClock.now++;c.emit(new Intent("com.google.oslo.HECKER_MOTION_FEEDBACK").putExtra("version",1).putExtra("action",action).putExtra("user",0).putExtra("when",SystemClock.now).putExtra("direction",side).putExtra("session",m.token));}
 public static void main(String[] args){
  Context c=new Context();MediaController m=new MediaController("player");m.state.state=3;c.manager.list=java.util.List.of(m);
  HeckerMotionIsland host=HeckerMotionIsland.create(c);check(host!=null&&c.windows.views.isEmpty());host.setAmbient(false);
  check(c.windows.views.size()==1);var params=c.windows.views.values().iterator().next();
  check(params.width==148&&params.height==32&&(params.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)==0);
  event(c,m,3,-1);check(c.windows.views.size()==2&&MotionAstronautView.direction==-1);
  check(c.windows.views.entrySet().stream().anyMatch(e->e.getKey() instanceof MotionAstronautView&&(e.getValue().flags&16)!=0));
  SystemClock.now+=2500;Handler.due();check(c.windows.views.size()==1); // Capsule persists, companion expires.
  SystemClock.now=600000;m.playback(2);check(c.windows.views.isEmpty()); // Long playback, callback before event.
  event(c,m,2,0);check(c.windows.views.size()==2&&MotionAstronautView.action==2);
  SystemClock.now+=2500;Handler.due();check(c.windows.views.isEmpty());
  SystemClock.now+=60000;event(c,m,1,0);check(c.windows.views.isEmpty()); // Pending PLAY never shows paused music.
  m.playback(3);check(c.windows.views.size()==2&&MotionAstronautView.action==1);
  MotionIslandView.last.performClick();check(c.windows.views.size()==1&&HeckerMotionMediaDialog.last.showing);
  m.playback(2);check(c.windows.views.isEmpty()&&HeckerMotionMediaDialog.last.showing); // Expanded resume remains usable.
  c.emit(new Intent(Intent.ACTION_SCREEN_OFF));check(c.windows.views.isEmpty()&&!HeckerMotionMediaDialog.last.showing&&m.callbacks.isEmpty());
  c.emit(new Intent(Intent.ACTION_SCREEN_ON));m.playback(3);check(c.windows.views.size()==1);
  host.close();check(c.windows.views.isEmpty()&&m.callbacks.isEmpty()&&c.receivers.isEmpty()&&c.manager.listeners.isEmpty());
  SystemClock.now+=10000;Handler.due();check(c.windows.views.isEmpty());
  System.out.println("Motion music coordinator: "+checks+" checks PASS");
 }
}'''
        java_run(sources, [ROOT/'framework/motionisland/HeckerMotionIsland.java',
                          ROOT/'framework/motionisland/MotionIslandState.java',
                          ROOT/'framework/qs/HeckerQsMedia.java', ROOT/'framework/qs/MusicSessionPolicy.java'],
                 'com.android.systemui.hecker.LifecycleTest')

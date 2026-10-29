# SPDX-License-Identifier: Apache-2.0
"""Execute the shipped home-depth controller and weather subscription against lifecycle fixtures."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from check_launcher_studio import method


class LauncherDepthWeatherTests(unittest.TestCase):
    def test_controller_ownership_aligned_layers_lifecycle_and_all_clock_subscriptions(self):
        restart=method((ROOT/'aosp-launcher/src/com/android/launcher3/hecker/HeckerClockView.java').read_text(),'    private void restartMotion()')
        sources={
        'android/net/Uri.java':'package android.net;public record Uri(String value){public static Uri parse(String s){return new Uri(s);}}',
        'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
        'android/os/Handler.java':'''package android.os;public class Handler{
        static final java.util.Queue<Runnable> tasks=new java.util.concurrent.ConcurrentLinkedQueue<>();public Handler(Looper l){}
        public boolean post(Runnable r){tasks.add(r);return true;}public void removeCallbacksAndMessages(Object token){tasks.clear();}
        public static void drain(){Runnable r;while((r=tasks.poll())!=null)r.run();}}''',
        'android/os/PowerManager.java':'package android.os;public class PowerManager{public static final String ACTION_POWER_SAVE_MODE_CHANGED="power";public boolean interactive=true,save;public boolean isInteractive(){return interactive;}public boolean isPowerSaveMode(){return save;}}',
        'android/app/KeyguardManager.java':'package android.app;public class KeyguardManager{public boolean locked;public boolean isKeyguardLocked(){return locked;}}',
        'android/app/WallpaperManager.java':'package android.app;public class WallpaperManager{public static final int FLAG_SYSTEM=1;public int id=51;public boolean live;public static WallpaperManager getInstance(android.content.Context c){return c.wallpaper;}public Object getWallpaperInfo(){return live?this:null;}public int getWallpaperId(int flag){return id;}}',
        'android/database/ContentObserver.java':'package android.database;public abstract class ContentObserver{public ContentObserver(android.os.Handler h){}public void onChange(boolean self){}}',
        'android/content/Intent.java':'''package android.content;public class Intent{public static final String ACTION_WALLPAPER_CHANGED="wallpaper",ACTION_USER_UNLOCKED="unlocked",ACTION_SCREEN_ON="on",ACTION_SCREEN_OFF="off",ACTION_USER_PRESENT="present";final String action;public Intent(String a){action=a;}public String getAction(){return action;}}''',
        'android/content/IntentFilter.java':'package android.content;public class IntentFilter{public IntentFilter(String a){}public void addAction(String a){}}',
        'android/content/BroadcastReceiver.java':'package android.content;public abstract class BroadcastReceiver{public abstract void onReceive(Context c,Intent i);}',
        'android/content/SharedPreferences.java':'''package android.content;public class SharedPreferences{
        public final java.util.Map<String,Boolean> values=new java.util.HashMap<>();public final java.util.List<OnSharedPreferenceChangeListener> listeners=new java.util.ArrayList<>();
        public interface OnSharedPreferenceChangeListener{void onSharedPreferenceChanged(SharedPreferences p,String k);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
        public boolean getBoolean(String k,boolean d){return values.getOrDefault(k,d);}public void put(String k,boolean v){values.put(k,v);for(var l:new java.util.ArrayList<>(listeners))l.onSharedPreferenceChanged(this,k);}}''',
        'android/content/ContentResolver.java':'''package android.content;public class ContentResolver{
        public final java.util.Map<String,String> values=new java.util.HashMap<>();public final java.util.List<android.database.ContentObserver> observers=new java.util.ArrayList<>();
        public boolean available=true,denyRegistration;public Runnable onOpen;public int opens;
        public void registerContentObserver(android.net.Uri uri,boolean d,android.database.ContentObserver observer){if(denyRegistration)throw new SecurityException();observers.add(observer);}
        public void unregisterContentObserver(android.database.ContentObserver observer){observers.removeIf(o->o==observer);}
        public java.io.InputStream openInputStream(android.net.Uri uri){opens++;if(!available)throw new IllegalStateException();if(onOpen!=null){Runnable action=onOpen;onOpen=null;action.run();}return new java.io.ByteArrayInputStream(new byte[]{(byte)(uri.value().endsWith("foreground")?1:0)});}
        public void notifyChange(){for(var observer:new java.util.ArrayList<>(observers))observer.onChange(false);}}''',
        'android/content/Context.java':'''package android.content;public class Context{
        public static final int RECEIVER_NOT_EXPORTED=4;public final ContentResolver resolver=new ContentResolver();public final SharedPreferences prefs=new SharedPreferences();
        public final android.app.WallpaperManager wallpaper=new android.app.WallpaperManager();public final android.app.KeyguardManager keyguard=new android.app.KeyguardManager();public final android.os.PowerManager power=new android.os.PowerManager();
        public final java.util.List<BroadcastReceiver> receivers=new java.util.ArrayList<>();public Context getApplicationContext(){return this;}public ContentResolver getContentResolver(){return resolver;}
        public <T>T getSystemService(Class<T> type){return type.cast(type==android.os.PowerManager.class?power:keyguard);}
        public Intent registerReceiver(BroadcastReceiver r,IntentFilter filter,int flags){receivers.add(r);return null;}public void unregisterReceiver(BroadcastReceiver r){if(!receivers.remove(r))throw new AssertionError("Unregistered absent receiver");}
        public void send(String action){for(var receiver:new java.util.ArrayList<>(receivers))receiver.onReceive(this,new Intent(action));}}''',
        'android/provider/Settings.java':'''package android.provider;public class Settings{public static class Secure{
        public static android.net.Uri getUriFor(String key){return android.net.Uri.parse("content://settings/"+key);}public static String getString(android.content.ContentResolver r,String key){return r.values.get(key);}
        public static int getInt(android.content.ContentResolver r,String key,int fallback){String v=getString(r,key);return v==null?fallback:Integer.parseInt(v);}}}''',
        'android/graphics/Bitmap.java':'package android.graphics;public class Bitmap{public final int kind;public Bitmap(int kind){this.kind=kind;}public int getWidth(){return 800;}public int getHeight(){return 1600;}}',
        'android/graphics/BitmapFactory.java':'''package android.graphics;public class BitmapFactory{public static boolean oversized;public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight;}
        public static Bitmap decodeStream(java.io.InputStream input,Object padding,Options options){options.outWidth=oversized?2000:800;options.outHeight=1600;try{int kind=input.read();return options.inJustDecodeBounds?null:new Bitmap(kind);}catch(java.io.IOException e){throw new RuntimeException(e);}}}''',
        'android/graphics/Paint.java':'package android.graphics;public class Paint{public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public Paint(int flags){}}',
        'android/graphics/RectF.java':'package android.graphics;public class RectF{public float l,t,r,b;public void set(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;}}',
        'android/graphics/Canvas.java':'''package android.graphics;public class Canvas{
        public final java.util.List<Integer> layers=new java.util.ArrayList<>();public final java.util.List<float[]> bounds=new java.util.ArrayList<>();public int depth,clips;public float dx,dy;
        public void drawBitmap(Bitmap image,Object src,RectF dst,Paint paint){layers.add(image.kind);bounds.add(new float[]{dst.l,dst.t,dst.r,dst.b});}
        public int save(){return depth++;}public void restoreToCount(int value){depth=value;}public void clipRect(int l,int t,int r,int b){clips++;}public void translate(float x,float y){dx=x;dy=y;}}''',
        'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable{}',
        'android/view/ViewParent.java':'package android.view;public interface ViewParent{}',
        'android/view/View.java':'''package android.view;public class View implements ViewParent{
        public static final int VISIBLE=0;public int invalidates,x,y,width=720,height=1520;public float sx=1,sy=1,rotation;public boolean attached=true,shown=true;public ViewParent parent;final android.content.Context context;
        public final java.util.List<OnAttachStateChangeListener> attaches=new java.util.ArrayList<>();public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}
        public interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
        public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attaches.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attaches.remove(l);}
        public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return shown;}public int getWindowVisibility(){return 0;}public int getWidth(){return width;}public int getHeight(){return height;}
        public void invalidate(){invalidates++;}public ViewParent getParent(){return parent;}public float getScaleX(){return sx;}public float getScaleY(){return sy;}public float getRotation(){return rotation;}
        public void getLocationInWindow(int[] location){location[0]=x;location[1]=y;}public void detach(){attached=false;for(var l:new java.util.ArrayList<>(attaches))l.onViewDetachedFromWindow(this);}}''',
        'android/view/animation/PathInterpolator.java':'package android.view.animation;public class PathInterpolator{public PathInterpolator(float a,float b,float c,float d){}}',
        'android/view/animation/LinearInterpolator.java':'package android.view.animation;public class LinearInterpolator{}',
        'android/animation/ValueAnimator.java':'''package android.animation;public class ValueAnimator{
        public static boolean enabled=true;public static final int INFINITE=-1;public static final java.util.List<ValueAnimator> active=new java.util.ArrayList<>();public float value;public long duration;public AnimatorUpdateListener listener;
        public interface AnimatorUpdateListener{void onAnimationUpdate(ValueAnimator a);}public static boolean areAnimatorsEnabled(){return enabled;}public static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}
        public void setDuration(long d){duration=d;}public void setInterpolator(Object i){}public void setRepeatCount(int n){}public void addUpdateListener(AnimatorUpdateListener l){listener=l;}
        public Object getAnimatedValue(){return value;}public void start(){active.add(this);if(listener!=null)listener.onAnimationUpdate(this);}public void cancel(){active.remove(this);}
        public static void finish(){for(var a:new java.util.ArrayList<>(active)){a.value=1;if(a.listener!=null)a.listener.onAnimationUpdate(a);}active.clear();}}''',
        'com/android/launcher3/hecker/HeckerStudio.java':'''package com.android.launcher3.hecker;public class HeckerStudio{
        public static final String PREFIX="hecker_studio_";public static android.content.SharedPreferences prefs(android.content.Context c){return c.prefs;}
        public static boolean enabled(android.content.Context c,String key,boolean fallback){return c.prefs.getBoolean(PREFIX+key,fallback);}}''',
        'com/android/launcher3/hecker/HeckerClockView.java':'''package com.android.launcher3.hecker;public class HeckerClockView extends android.view.View{public int updates;public HeckerClockView(android.content.Context c){super(c);}void weatherChanged(){updates++;invalidate();}}''',
        'com/android/launcher3/hecker/HeckerWeather.java':'''package com.android.launcher3.hecker;public class HeckerWeather{
        public static int starts,stops;public static HeckerWeather current;final Runnable changed;String value;
        public HeckerWeather(android.content.Context c,Runnable changed){this.changed=changed;current=this;}public void start(){starts++;}public void stop(){stops++;}
        public String text(){return value;}public android.graphics.drawable.Drawable icon(){return null;}public void emit(String text){value=text;changed.run();}}''',
        'ClockSubscription.java':'''import com.android.launcher3.hecker.*;import android.animation.ValueAnimator;
        class ClockSubscription extends android.view.View{boolean running;String style;ValueAnimator animator;float phase;
        ClockSubscription(android.content.Context c){super(c);}void restart(){restartMotion();}
        '''+restart+'}',
        'HomeDepthProbe.java':r'''import android.content.*;import android.view.View;import android.graphics.Canvas;import android.os.Handler;import android.animation.ValueAnimator;
import com.android.launcher3.hecker.*;import java.util.*;import java.util.concurrent.*;
class HomeDepthProbe{
static void check(boolean v){if(!v)throw new AssertionError();}
static ExecutorService io()throws Exception{var f=HeckerHomeDepth.class.getDeclaredField("IO");f.setAccessible(true);return (ExecutorService)f.get(null);}
static void settle()throws Exception{io().submit(()->{}).get(3,TimeUnit.SECONDS);Handler.drain();}
static Canvas draw(View root){Canvas c=new Canvas();HeckerHomeDepth.background(root,c);return c;}
public static void main(String[]args)throws Exception{try{
String id="ca2aca2da00000000000000000000002";
check(HomeDepthPolicy.eligible(true,false,id,51,51));check(!HomeDepthPolicy.eligible(false,false,id,51,51));check(!HomeDepthPolicy.eligible(true,true,id,51,51));
check(!HomeDepthPolicy.eligible(true,false,id,50,51));check(!HomeDepthPolicy.eligible(true,false,"../x",51,51));check(!HomeDepthPolicy.eligible(true,false,id,-1,-1));
for(int w:new int[]{320,720,1440})for(int h:new int[]{400,1520,3040})for(int i=0;i<=100;i++){
 float reveal=i/100f;float[] home=new float[3];HomeDepthPolicy.crop(800,1600,w,h,reveal,home);
 float[] lock=com.android.systemui.hecker.DepthWallpaperGeometry.crop(800,1600,w,h,reveal,false);
 for(int n=0;n<3;n++)check(Math.abs(home[n]-lock[n])<.0001f);
}
Context c=new Context();c.resolver.values.put("hecker_living_flow","0");c.resolver.values.put("hecker_depth_wallpaper_id",id);c.resolver.values.put("hecker_home_depth_wallpaper","1");c.resolver.values.put("hecker_home_depth_wallpaper_wallpaper_id","51");
View root=new View(c);check(draw(root).layers.isEmpty());settle();Canvas bg=draw(root);check(bg.layers.equals(List.of(0,1)));check(Arrays.equals(bg.bounds.get(0),bg.bounds.get(1)));
check(ValueAnimator.active.size()==1&&ValueAnimator.active.get(0).duration==750);
View clock=new View(c);clock.parent=root;clock.x=18;clock.y=80;clock.width=600;clock.height=220;Canvas fg=new Canvas();HeckerHomeDepth.foreground(clock,fg);
check(fg.layers.equals(List.of(1))&&fg.clips==1&&fg.depth==0&&fg.dx==-18&&fg.dy==-80);
int before=clock.invalidates;ValueAnimator.finish();check(clock.invalidates>before);bg=draw(root);
float[] finalCrop=new float[3];HomeDepthPolicy.crop(800,1600,720,1520,1,finalCrop);check(Math.abs(bg.bounds.get(0)[0]-finalCrop[1])<.001f);
root.sx=.8f;fg=new Canvas();HeckerHomeDepth.foreground(clock,fg);check(fg.layers.isEmpty());root.sx=1;
c.prefs.put(HeckerStudio.PREFIX+"home_depth",false);settle();check(draw(root).layers.isEmpty());
c.prefs.put(HeckerStudio.PREFIX+"home_depth",true);settle();check(draw(root).layers.size()==2);
c.wallpaper.id=99;c.send(Intent.ACTION_WALLPAPER_CHANGED);settle();check(draw(root).layers.isEmpty());
c.wallpaper.id=51;c.wallpaper.live=true;c.send(Intent.ACTION_WALLPAPER_CHANGED);settle();check(draw(root).layers.isEmpty());
c.wallpaper.live=false;c.resolver.available=false;c.send(Intent.ACTION_USER_UNLOCKED);settle();check(draw(root).layers.isEmpty());
c.resolver.available=true;android.graphics.BitmapFactory.oversized=true;c.send(Intent.ACTION_USER_UNLOCKED);settle();check(draw(root).layers.isEmpty());android.graphics.BitmapFactory.oversized=false;
c.resolver.onOpen=()->c.wallpaper.id=52;c.send(Intent.ACTION_USER_UNLOCKED);settle();check(draw(root).layers.isEmpty());c.wallpaper.id=51;
c.power.save=true;c.send(Intent.ACTION_USER_UNLOCKED);settle();check(ValueAnimator.active.isEmpty()&&draw(root).layers.size()==2);
c.power.save=false;c.keyguard.locked=true;c.send(Intent.ACTION_SCREEN_ON);check(ValueAnimator.active.isEmpty());c.keyguard.locked=false;c.send(Intent.ACTION_USER_PRESENT);check(ValueAnimator.active.size()==1);
ValueAnimator.finish();c.resolver.values.put("hecker_living_flow","1");c.send(Intent.ACTION_USER_PRESENT);
check(ValueAnimator.active.isEmpty());bg=draw(root);check(Math.abs(bg.bounds.get(0)[0]-finalCrop[1])<.001f);c.resolver.values.put("hecker_living_flow","0");
c.power.interactive=false;c.send(Intent.ACTION_SCREEN_OFF);check(ValueAnimator.active.isEmpty());c.power.interactive=true;ValueAnimator.enabled=false;c.send(Intent.ACTION_USER_PRESENT);check(ValueAnimator.active.isEmpty());ValueAnimator.enabled=true;
c.prefs.put(HeckerStudio.PREFIX+"home_depth_wake",false);settle();check(ValueAnimator.active.isEmpty());
root.detach();check(c.resolver.observers.isEmpty()&&c.receivers.isEmpty()&&c.prefs.listeners.isEmpty());check(draw(root).layers.isEmpty());
Context denied=new Context();denied.resolver.denyRegistration=true;View failed=new View(denied);draw(failed);check(failed.attaches.isEmpty()&&denied.receivers.isEmpty());
Context other=new Context();View otherRoot=new View(other);draw(otherRoot);settle();check(draw(otherRoot).layers.isEmpty());otherRoot.detach();
Context weather=new Context();HeckerClockView a=new HeckerClockView(weather),b=new HeckerClockView(weather);HeckerClockWeather.start(a);HeckerClockWeather.start(b);check(HeckerWeather.starts==1);
HeckerWeather.current.emit("Islamabad 29°C");check("Islamabad 29°C".equals(HeckerClockWeather.text())&&a.updates==1&&b.updates==1);
HeckerClockWeather.stop(a);check(HeckerWeather.stops==0);HeckerWeather.current.emit(null);check(HeckerClockWeather.text()==null&&b.updates==2);HeckerClockWeather.stop(b);check(HeckerWeather.stops==1);
ValueAnimator.enabled=false;
for(String style:StudioPolicy.CLOCKS){ClockSubscription probe=new ClockSubscription(weather);probe.style=style;probe.running=true;probe.restart();HeckerWeather.current.emit("29°C");check("29°C".equals(HeckerClockWeather.text()));
weather.prefs.put(HeckerStudio.PREFIX+"clock_weather",false);probe.restart();check(HeckerClockWeather.text()==null);weather.prefs.put(HeckerStudio.PREFIX+"clock_weather",true);probe.restart();probe.running=false;probe.restart();}
io().shutdownNow();System.out.println("Home depth: 909 crop parity cases, identity/photo/live safeguards, aligned clipping, invalid/oversized loads, stale result, unlock/power/reduced-motion/detach; weather: shared lifecycle and all ten clock styles PASS");
}finally{io().shutdownNow();}}}'''
        }
        with tempfile.TemporaryDirectory(prefix='hecker-home-depth-') as temporary:
            folder=Path(temporary);files=[]
            for name,source in sources.items():
                p=folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);files.append(str(p))
            launcher=ROOT/'aosp-launcher/src/com/android/launcher3/hecker'
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(folder/'classes'),*files,
                *[str(launcher/name) for name in ['HomeDepthPolicy.java','HeckerHomeDepth.java','HeckerClockWeather.java','StudioPolicy.java']],
                str(ROOT/'framework/lockscreen/DepthWallpaperGeometry.java')],check=True)
            subprocess.run(['java','-cp',str(folder/'classes'),'HomeDepthProbe'],check=True,timeout=15)

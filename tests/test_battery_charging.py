# SPDX-License-Identifier: Apache-2.0
"""Exercise the production battery drawable's geometry and frame scheduling on the host."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BatteryChargingTest(unittest.TestCase):
    def test_liquid_fill_and_animation_lifecycle(self):
        files = {
            'android/animation/ValueAnimator.java': '''package android.animation;
public class ValueAnimator {public static float scale=1;public static boolean areAnimatorsEnabled(){return scale>0;}
public static float getDurationScale(){return scale;}}''',
            'android/os/SystemClock.java': '''package android.os;
public class SystemClock {public static long now=1000;public static long uptimeMillis(){return now;}}''',
            'android/os/UserHandle.java': 'package android.os;public class UserHandle {public static final int USER_CURRENT=-2;}',
            'android/provider/Settings.java': '''package android.provider;
public class Settings {public static class Secure {public static int getIntForUser(Object r,String n,int d,int u){return d;}}}''',
            'android/content/Context.java': 'package android.content;public class Context {public Object getContentResolver(){return null;}}',
            'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
 public static boolean horizontal=true;private final java.util.Map<Integer,String> names=new java.util.HashMap<>();
 public int getIdentifier(String name,String type,String pkg){int id=name.hashCode();names.put(id,name);return id;}
 public int getInteger(int id){return names.get(id).endsWith("Width")?(horizontal?28:12):(horizontal?12:28);}
 public boolean getBoolean(int id){return names.get(id).equals("config_batterymeterHorizontalFill")?horizontal:names.get(id).equals("config_themedBatteryPercentWhileCharging");}
 public int getColor(int id,Object theme){return 0xff64e4bd;}public String getString(int id){return names.get(id);}
}''',
            'android/graphics/Rect.java': '''package android.graphics;
public class Rect {public int left,top,right,bottom;public int width(){return right-left;}public int height(){return bottom-top;}}''',
            'android/graphics/RectF.java': '''package android.graphics;
public class RectF {public float left,top,right,bottom;public float width(){return right-left;}public float height(){return bottom-top;}}''',
            'android/graphics/Path.java': '''package android.graphics;
public class Path {
 public enum Direction {CW}public final java.util.List<float[]> points=new java.util.ArrayList<>();
 public void rewind(){points.clear();}public void moveTo(float x,float y){points.add(new float[]{x,y});}
 public void lineTo(float x,float y){moveTo(x,y);}public void close(){}
 public void addRect(float l,float t,float r,float b,Direction d){moveTo(l,t);lineTo(r,t);lineTo(r,b);lineTo(l,b);close();}
 public void computeBounds(RectF b,boolean exact){b.left=b.top=Float.POSITIVE_INFINITY;b.right=b.bottom=Float.NEGATIVE_INFINITY;
  for(float[] p:points){b.left=Math.min(b.left,p[0]);b.right=Math.max(b.right,p[0]);b.top=Math.min(b.top,p[1]);b.bottom=Math.max(b.bottom,p[1]);}}
 public String signature(){return points.stream().map(java.util.Arrays::toString).collect(java.util.stream.Collectors.joining());}
 public double area(){double area=0;for(int i=0;i<points.size();i++){float[] a=points.get(i),b=points.get((i+1)%points.size());area+=(double)a[0]*b[1]-(double)a[1]*b[0];}return Math.abs(area/2);}
}''',
            'android/util/PathParser.java': '''package android.util;
public class PathParser {public static android.graphics.Path createPathFromPathData(String data){
 android.graphics.Path path=new android.graphics.Path();float w=android.content.res.Resources.horizontal?28:12,h=android.content.res.Resources.horizontal?12:28;
 path.addRect(1,1,w-1,h-1,android.graphics.Path.Direction.CW);return path;}}''',
            'android/graphics/Color.java': '''package android.graphics;
public class Color {public static final int WHITE=-1,BLACK=0xff000000;public static float luminance(int color){return .7f;}}''',
            'android/graphics/ColorFilter.java': 'package android.graphics;public class ColorFilter {}',
            'android/graphics/PixelFormat.java': 'package android.graphics;public class PixelFormat {public static final int TRANSLUCENT=-3;}',
            'android/graphics/Paint.java': '''package android.graphics;
public class Paint {public static final int ANTI_ALIAS_FLAG=1;public enum Style{FILL}public enum Align{CENTER}
 public Paint(int flags){}public void setStyle(Style s){}public void setColor(int c){}public void setAlpha(int a){}
 public void setTextAlign(Align a){}public void setFakeBoldText(boolean b){}public void setTextSize(float s){}
 public void setColorFilter(ColorFilter f){}public FontMetrics getFontMetrics(){return new FontMetrics();}
 public static class FontMetrics {public float ascent=-8,descent=2;}}''',
            'android/graphics/Canvas.java': '''package android.graphics;
public class Canvas {public final java.util.List<String> clips=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();
 public int save(){return 1;}public void translate(float x,float y){}public void scale(float x,float y){}
 public boolean clipPath(Path p){clips.add(p.signature());return true;}public void drawPath(Path p,Paint paint){}
 public void drawText(String text,float x,float y,Paint paint){labels.add(text);}public void restoreToCount(int n){}
}''',
            'android/graphics/drawable/Animatable.java': 'package android.graphics.drawable;public interface Animatable {void start();void stop();boolean isRunning();}',
            'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
public abstract class Drawable {
 public interface Callback {void invalidateDrawable(Drawable d);void scheduleDrawable(Drawable d,Runnable r,long when);void unscheduleDrawable(Drawable d,Runnable r);}
 private Callback callback;private boolean visible=true;private final android.graphics.Rect bounds=new android.graphics.Rect();
 public void setCallback(Callback c){callback=c;}public Callback getCallback(){return callback;}
 public void invalidateSelf(){if(callback!=null)callback.invalidateDrawable(this);}
 public void scheduleSelf(Runnable r,long when){if(callback!=null)callback.scheduleDrawable(this,r,when);}
 public void unscheduleSelf(Runnable r){if(callback!=null)callback.unscheduleDrawable(this,r);}
 public boolean isVisible(){return visible;}public boolean setVisible(boolean v,boolean restart){boolean changed=v!=visible;visible=v;return changed;}
 public android.graphics.Rect getBounds(){return bounds;}public void setBounds(int l,int t,int r,int b){bounds.left=l;bounds.top=t;bounds.right=r;bounds.bottom=b;}
 public int getIntrinsicWidth(){return -1;}public int getIntrinsicHeight(){return -1;}
 public abstract void draw(android.graphics.Canvas c);public abstract void setAlpha(int alpha);
 public abstract void setColorFilter(android.graphics.ColorFilter f);public abstract int getOpacity();
}''',
            'com/android/systemui/hecker/HeckerThemeAssets.java': '''package com.android.systemui.hecker;
public class HeckerThemeAssets {public static String pkg="com.android.systemui.battery.oneui7";
 public static String packageName(android.content.Context c,String type){return pkg;}
 public static android.content.res.Resources resources(android.content.Context c,String type){return new android.content.res.Resources();}}''',
            'BatteryChargingProbe.java': r'''import android.animation.ValueAnimator;
import android.content.Context;import android.content.res.Resources;import android.graphics.*;
import android.graphics.drawable.Drawable;import android.os.SystemClock;
import com.android.systemui.hecker.*;
public class BatteryChargingProbe {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Object field(Object d,String name)throws Exception{var f=d.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(d);}
 static class Frames implements Drawable.Callback {
  Runnable pending;long due;int schedules,invalidations;
  public void invalidateDrawable(Drawable d){invalidations++;}
  public void scheduleDrawable(Drawable d,Runnable r,long when){check(pending==null,"duplicate frame");pending=r;due=when;schedules++;}
  public void unscheduleDrawable(Drawable d,Runnable r){if(pending==r)pending=null;}
  void tick(long millis){SystemClock.now+=millis;Runnable r=pending;pending=null;if(r!=null)r.run();}
 }
 static HeckerBatteryDrawable battery(boolean horizontal,String pkg,Frames frames){
  Resources.horizontal=horizontal;HeckerThemeAssets.pkg=pkg;
  var d=HeckerBatteryDrawable.create(new Context());check(d!=null,"valid overlay");
  d.setBounds(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());d.setCallback(frames);return d;
 }
 static void geometry(HeckerBatteryDrawable d,int level)throws Exception {
  Canvas c=new Canvas();d.draw(c);Path path=(Path)field(d,"fillPath"),mask=(Path)field(d,"mask");
  RectF b=new RectF();mask.computeBounds(b,true);
  for(float[] p:path.points)check(p[0]>=b.left-.0001&&p[0]<=b.right+.0001&&p[1]>=b.top-.0001&&p[1]<=b.bottom+.0001,"wave escaped mask bounds");
  check(Math.abs(path.area()-b.width()*b.height()*level/100d)<.001,"wave changed mean level: "+level);
  check(c.clips.size()==4&&c.clips.get(1).equals(c.clips.get(3)),"percentage contrast must follow moving fill");
  check(c.labels.equals(java.util.List.of(""+level,""+level)),"percentage changed with animation");
 }
 public static void main(String[] args)throws Exception {
  for(boolean horizontal:new boolean[]{false,true})for(int level:new int[]{0,1,5,15,25,50,63,70,99,100}){
   Frames f=new Frames();var d=battery(horizontal,"com.android.systemui.battery.oneui7",f);
   String mask=((Path)field(d,"mask")).signature();
   d.update(level,true,false,true,Color.WHITE);d.start();
   check(d.isRunning()==(level>0&&level<100),"only chargeable levels animate");
   int schedules=f.schedules;d.start();check(schedules==f.schedules,"start must be idempotent");
   for(int frame=0;frame<30;frame++){
    geometry(d,level);
    if(d.isRunning())check(f.pending!=null&&f.due-SystemClock.now==33,"bounded frame cadence");
    f.tick(83);
   }
   check(mask.equals(((Path)field(d,"mask")).signature()),"original overlay path mutated");
   d.stop();check(!d.isRunning()&&f.pending==null,"stop must remove frames");
   int afterStop=f.schedules;d.run();check(afterStop==f.schedules,"stale callback restarted animation");
   geometry(d,level);
  }
  Frames f=new Frames();var d=battery(true,"com.android.systemui.battery.oneui7",f);
  d.update(63,true,false,true,Color.WHITE);d.start();d.draw(new Canvas());
  String initial=((Path)field(d,"fillPath")).signature();f.tick(600);d.draw(new Canvas());
  check(!initial.equals(((Path)field(d,"fillPath")).signature()),"fill did not move");
  check(Math.abs((Float)field(d,"wavePhase")-.25f)<.0001,"normal animation speed");
  d.stop();ValueAnimator.scale=2;d.start();f.tick(600);
  check(Math.abs((Float)field(d,"wavePhase")-.125f)<.0001,"system duration scale ignored");
  ValueAnimator.scale=0;f.tick(33);check(!d.isRunning()&&f.pending==null,"reduced motion did not stop");
  d.start();check(!d.isRunning(),"reduced motion started");
  ValueAnimator.scale=1;d.start();check(d.isRunning(),"animation cannot resume");
  d.setVisible(false,false);check(!d.isRunning()&&f.pending==null,"hidden battery kept animating");
  d.start();check(!d.isRunning(),"hidden battery restarted");d.setVisible(true,false);d.start();
  d.update(63,false,false,true,Color.WHITE);check(!d.isRunning()&&f.pending==null,"unplug did not stop");
  d.update(63,true,true,true,Color.WHITE);d.start();check(!d.isRunning(),"power saver animated");
  d.update(100,true,false,true,Color.WHITE);d.start();check(!d.isRunning(),"full battery animated");
  d.update(-4,true,false,true,Color.WHITE);geometry(d,0);
  d.update(110,true,false,true,Color.WHITE);geometry(d,100);
  var other=battery(true,"other.battery.style",new Frames());
  other.update(63,true,false,true,Color.WHITE);other.start();check(!other.isRunning(),"another theme animated");
  d.update(63,true,false,true,Color.WHITE);d.setCallback(null);d.start();check(!d.isRunning(),"detached drawable started");
  System.out.println("Production battery charging: level geometry, wave motion, percentage contrast, cadence, visibility, charging and reduced-motion lifecycle PASS");
 }
}''',
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, content in files.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
            subprocess.run([
                'java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root),
                *map(str, root.rglob('*.java')),
                str(ROOT / 'framework/themes/HeckerBatteryDrawable.java'),
            ], check=True)
            subprocess.run(['java', '-cp', str(root), 'BatteryChargingProbe'], check=True)


if __name__ == '__main__':
    unittest.main()

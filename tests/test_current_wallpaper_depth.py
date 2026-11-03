"""Run the production current-wallpaper pipeline with OS/image boundaries replaced by host fakes."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CurrentWallpaperDepthTests(unittest.TestCase):
    def test_builtin_saved_lock_cache_force_stale_and_independent_home(self):
        sources = {
            'android/content/ContentResolver.java': 'package android.content;public class ContentResolver{public java.util.Map<String,String> values=new java.util.HashMap<>();}',
            'android/content/Context.java': '''package android.content;public class Context{
 public java.io.File files;public ContentResolver resolver=new ContentResolver();public android.app.WallpaperManager manager=new android.app.WallpaperManager();
 public java.io.File getFilesDir(){return files;}public ContentResolver getContentResolver(){return resolver;}}''',
            'android/provider/Settings.java': '''package android.provider;public class Settings{public static class Secure{
 public static String getString(android.content.ContentResolver r,String k){return r.values.get(k);}
 public static int getInt(android.content.ContentResolver r,String k,int fallback){String v=r.values.get(k);return v==null?fallback:Integer.parseInt(v);}
 public static boolean putString(android.content.ContentResolver r,String k,String v){r.values.put(k,v);return true;}
 public static boolean putInt(android.content.ContentResolver r,String k,int v){return putString(r,k,Integer.toString(v));}}}''',
            'android/app/WallpaperManager.java': '''package android.app;public class WallpaperManager{
 public static final int FLAG_LOCK=2,FLAG_SYSTEM=1;public int wallpaper=10,lock=10,builtins,lastRead;public boolean live,saved;
 public static WallpaperManager getInstance(android.content.Context c){return c.manager;}
 public Object getWallpaperInfo(int flag){return live?this:null;}public int getWallpaperId(int flag){return flag==FLAG_LOCK?lock:wallpaper;}
 public android.os.ParcelFileDescriptor getWallpaperFile(int flag){lastRead=flag;return saved?new android.os.ParcelFileDescriptor():null;}
 public android.graphics.drawable.Drawable getBuiltInDrawable(int a,int b,boolean c,float d,float e,int flag){builtins++;return new android.graphics.drawable.Drawable();}}''',
            'android/os/ParcelFileDescriptor.java': '''package android.os;public class ParcelFileDescriptor implements AutoCloseable{
 public java.io.FileDescriptor getFileDescriptor(){return new java.io.FileDescriptor();}public void close(){}}''',
            'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap{
 public enum Config{ARGB_8888}public enum CompressFormat{PNG}public int width,height;
 public Bitmap(int w,int h){width=w;height=h;}public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
 public int getWidth(){return width;}public int getHeight(){return height;}public void recycle(){}
 public boolean compress(CompressFormat f,int quality,java.io.OutputStream out){try{out.write(new byte[]{1,2,3});return true;}catch(java.io.IOException e){return false;}}}''',
            'android/graphics/BitmapFactory.java': '''package android.graphics;public class BitmapFactory{
 public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;public Bitmap.Config inPreferredConfig;}
 public static Bitmap decodeFileDescriptor(java.io.FileDescriptor fd,Object o,Options options){options.outWidth=800;options.outHeight=1200;
 return options.inJustDecodeBounds?null:new Bitmap(800/options.inSampleSize,1200/options.inSampleSize);}}''',
            'android/graphics/Canvas.java': 'package android.graphics;public class Canvas{public Canvas(Bitmap b){}}',
            'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;public class Drawable{
 public int getIntrinsicWidth(){return 2160;}public int getIntrinsicHeight(){return 3840;}
 public void setBounds(int a,int b,int c,int d){}public void draw(android.graphics.Canvas canvas){}}''',
            'android/util/AtomicFile.java': '''package android.util;public class AtomicFile{
 private final java.io.File file;public AtomicFile(java.io.File f){file=f;}
 public java.io.FileOutputStream startWrite()throws java.io.IOException{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream out)throws java.io.IOException{out.close();}public void failWrite(java.io.FileOutputStream out){try{out.close();}catch(Exception e){}}}''',
            'org/hecker/suite/DefaultAppearance.java': '''package org.hecker.suite;public class DefaultAppearance{
 public static final String WALLPAPER_ID="expected";public static int wallpaperId(android.content.Context c){return c.manager.wallpaper;}}''',
            'org/hecker/suite/DepthWallpaperProvider.java': 'package org.hecker.suite;public class DepthWallpaperProvider{public static final String ID="pair";}',
            'org/hecker/suite/HomeAppearance.java': 'package org.hecker.suite;public class HomeAppearance{public static final String KEY="home";}',
            'org/hecker/suite/DepthSubjectExtractor.java': '''package org.hecker.suite;public class DepthSubjectExtractor{
 public static int calls;public static boolean change;public static android.graphics.Bitmap extract(android.content.Context c,android.graphics.Bitmap b){
 calls++;if(change)c.manager.wallpaper++;if(Math.max(b.getWidth(),b.getHeight())>1600)throw new AssertionError("Unbounded image");
 return new android.graphics.Bitmap(b.getWidth(),b.getHeight());}}''',
            'org/hecker/suite/CurrentDepthProbe.java': '''package org.hecker.suite;
import android.content.Context;import android.provider.Settings;import java.io.File;
public class CurrentDepthProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static String active(Context c){return Settings.Secure.getString(c.resolver,DepthWallpaperProvider.ID);}
 public static void main(String[] args)throws Exception{
  Context c=new Context();c.files=new File(args[0]);c.files.mkdirs();
  Settings.Secure.putInt(c.resolver,"hecker_lockscreen_clock_style",1);
  check(CurrentWallpaperDepth.extract(c,()->true,false));String first=active(c);
  check(c.manager.builtins==1&&DepthSubjectExtractor.calls==1);
  check(Settings.Secure.getInt(c.resolver,"hecker_lockscreen_clock_style",0)==1);
  check(Settings.Secure.getInt(c.resolver,DefaultAppearance.WALLPAPER_ID,0)==10);
  check(CurrentWallpaperDepth.extract(c,()->true,false)&&DepthSubjectExtractor.calls==1);
  Settings.Secure.putString(c.resolver,HomeAppearance.KEY,first);
  check(CurrentWallpaperDepth.extract(c,()->true,true)&&DepthSubjectExtractor.calls==2);
  check(!first.equals(active(c))&&new File(c.files,"depth/"+first+"/foreground.png").isFile());
  c.manager.saved=true;check(CurrentWallpaperDepth.extract(c,()->true,true));
  check(c.manager.lastRead==2&&c.manager.builtins==2);
  c.manager.lock=-1;c.manager.wallpaper=11;check(CurrentWallpaperDepth.extract(c,()->true,false));check(c.manager.lastRead==1);
  String before=active(c);c.manager.live=true;check(!CurrentWallpaperDepth.extract(c,()->true,true));check(before.equals(active(c)));c.manager.live=false;
  check(!CurrentWallpaperDepth.extract(c,()->false,true));check(before.equals(active(c)));
  DepthSubjectExtractor.change=true;check(!CurrentWallpaperDepth.extract(c,()->true,true));check(before.equals(active(c)));DepthSubjectExtractor.change=false;
  c.manager.saved=false;c.manager.wallpaper=0;c.manager.lock=-1;check(CurrentWallpaperDepth.extract(c,()->true,true));
  check(Settings.Secure.getInt(c.resolver,DefaultAppearance.WALLPAPER_ID,-99)==0);
  // Both native/plugin and Hecker selections survive fresh and cached extraction.
  for(int custom:new int[]{0,1})for(String plugin:new String[]{"DEFAULT","DIGITAL","PLUGIN_CUSTOM"}){
   Settings.Secure.putInt(c.resolver,"hecker_custom_lockscreen_clock",custom);
   Settings.Secure.putInt(c.resolver,"hecker_lockscreen_clock_style",5);
   Settings.Secure.putString(c.resolver,"lock_screen_custom_clock_face",plugin);
   check(CurrentWallpaperDepth.extract(c,()->true,true));
   check(CurrentWallpaperDepth.extract(c,()->true,false));
   check(Settings.Secure.getInt(c.resolver,"hecker_custom_lockscreen_clock",-1)==custom);
   check(Settings.Secure.getInt(c.resolver,"hecker_lockscreen_clock_style",-1)==5);
   check(plugin.equals(Settings.Secure.getString(c.resolver,"lock_screen_custom_clock_face")));
  }
  System.out.println("Current wallpaper: bounded sources, cache/force, stock/plugin/Hecker selection retention, Home retention and stale/cancel/live rejection PASS");
 }
}''',
        }
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder); files = []
            for name, content in sources.items():
                path = work / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content); files.append(str(path))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(work / 'classes'),
                            *files, str(ROOT / 'app/src/org/hecker/suite/CurrentWallpaperDepth.java')], check=True)
            subprocess.run(['java', '-cp', str(work / 'classes'), 'org.hecker.suite.CurrentDepthProbe', str(work / 'data')], check=True)


if __name__ == '__main__': unittest.main()

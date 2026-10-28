# SPDX-License-Identifier: Apache-2.0
"""Execute fresh-user wallpaper defaults without overwriting existing wallpaper/clock choices."""
from pathlib import Path
import subprocess
import tempfile
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/content/ContentResolver.java':'''package android.content;public class ContentResolver{public final java.util.Map<String,String> values=new java.util.HashMap<>();}''',
'android/content/SharedPreferences.java':'''package android.content;public class SharedPreferences{
 java.util.Map<String,Boolean> values=new java.util.HashMap<>();public boolean getBoolean(String key,boolean fallback){return values.getOrDefault(key,fallback);}
 public Editor edit(){return new Editor();}public class Editor{public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public boolean commit(){return true;}}}''',
'android/content/Context.java':'''package android.content;public class Context{
 public static final int MODE_PRIVATE=0;public Context device;public java.io.File files;
 public final ContentResolver resolver=new ContentResolver();public final SharedPreferences prefs=new SharedPreferences();
 public final android.app.WallpaperManager wallpaper=new android.app.WallpaperManager();
 public Context createDeviceProtectedStorageContext(){return device;}public SharedPreferences getSharedPreferences(String s,int m){return prefs;}
 public java.io.File getFilesDir(){return files;}public ContentResolver getContentResolver(){return resolver;}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}}''',
'android/content/res/AssetManager.java':'''package android.content.res;public class AssetManager{
 public java.io.InputStream open(String name){return new java.io.ByteArrayInputStream(new byte[]{(byte)(name.contains("foreground")?1:0)});}}''',
'android/app/WallpaperManager.java':'''package android.app;public class WallpaperManager{
 public static final int FLAG_SYSTEM=1,FLAG_LOCK=2;public int homeId=23,lockId=-1,sets;public boolean savedHome,savedLock,live;
 public static WallpaperManager getInstance(android.content.Context c){return c.wallpaper;}
 public int getWallpaperId(int flag){return flag==FLAG_SYSTEM?homeId:lockId;}
 public Object getWallpaperInfo(){return live?this:null;}
 public android.os.ParcelFileDescriptor getWallpaperFile(int flag){return (flag==FLAG_SYSTEM?savedHome:savedLock)?new android.os.ParcelFileDescriptor():null;}
 public int setBitmap(android.graphics.Bitmap b,Object crop,boolean backup,int flags){
 if(flags!=3||!backup||b.getHeight()>1600)throw new AssertionError("Default wallpaper flags/bounds");
 sets++;homeId=80+sets;lockId=-1;savedHome=true;savedLock=false;return homeId;}}''',
'android/os/ParcelFileDescriptor.java':'''package android.os;public class ParcelFileDescriptor implements java.lang.AutoCloseable{public void close()throws java.io.IOException{}}''',
'android/graphics/Bitmap.java':'''package android.graphics;public class Bitmap{
 public enum Config{ARGB_8888}public enum CompressFormat{PNG}final int w,h;Bitmap(int w,int h){this.w=w;this.h=h;}
 public int getWidth(){return w;}public int getHeight(){return h;}
 public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean filter){return new Bitmap(w,h);}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
 public boolean compress(CompressFormat f,int quality,java.io.OutputStream s){try{s.write(1);return true;}catch(java.io.IOException e){return false;}}}''',
'android/graphics/BitmapFactory.java':'''package android.graphics;public class BitmapFactory{public static boolean mismatch;
 public static Bitmap decodeStream(java.io.InputStream s){try{return new Bitmap(mismatch&&s.read()==1?900:860,1828);}catch(java.io.IOException e){return null;}}}''',
'android/graphics/Canvas.java':'''package android.graphics;public class Canvas{public Canvas(Bitmap b){}public void drawBitmap(Bitmap b,Object source,RectF destination,Paint p){}}''',
'android/graphics/Paint.java':'''package android.graphics;public class Paint{public static final int FILTER_BITMAP_FLAG=2;public Paint(int flags){}}''',
'android/graphics/RectF.java':'''package android.graphics;public class RectF{public RectF(int l,int t,int r,int b){}}''',
'android/util/AtomicFile.java':'''package android.util;public class AtomicFile{final java.io.File file;public AtomicFile(java.io.File f){file=f;}
 public java.io.FileOutputStream startWrite()throws java.io.IOException{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream s){try{s.close();}catch(java.io.IOException e){throw new RuntimeException(e);}}
 public void failWrite(java.io.FileOutputStream s){finishWrite(s);file.delete();}}''',
'android/provider/Settings.java':'''package android.provider;public class Settings{public static class Secure{
 public static String getString(android.content.ContentResolver r,String key){return r.values.get(key);}
 public static int getInt(android.content.ContentResolver r,String key,int fallback){String value=getString(r,key);return value==null?fallback:Integer.parseInt(value);}
 public static boolean putString(android.content.ContentResolver r,String key,String value){r.values.put(key,value);return true;}
 public static boolean putInt(android.content.ContentResolver r,String key,int value){return putString(r,key,String.valueOf(value));}}}''',
'org/hecker/suite/DepthWallpaperProvider.java':'''package org.hecker.suite;public class DepthWallpaperProvider{public static final String ID="hecker_depth_wallpaper_id";}''',
'DefaultAppearanceProbe.java':'''import org.hecker.suite.DefaultAppearance;import android.content.Context;import android.provider.Settings.Secure;
class DefaultAppearanceProbe{
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static Context user(String root,String name){Context c=new Context();c.device=new Context();c.files=new java.io.File(root,name+"-ce");c.device.files=new java.io.File(root,name+"-de");return c;}
 static int value(Context c,String key){return Secure.getInt(c.getContentResolver(),key,-99);}
 public static void main(String[]args)throws Exception{
  Context fresh=user(args[0],"fresh0");check(DefaultAppearance.apply(fresh,false),"Built-in wallpaper's positive native ID was mistaken for custom");
  check(fresh.wallpaper.sets==1&&value(fresh,"hecker_lockscreen_clock_style")==2,"Fresh clock/pair not installed");
  check(value(fresh,"hecker_lockscreen_clock_opacity")==76&&value(fresh,"hecker_depth_wallpaper")==1,"Translucent depth defaults");
  check(value(fresh,DefaultAppearance.HOME_DEPTH)==1&&value(fresh,DefaultAppearance.HOME_WALLPAPER_ID)==fresh.wallpaper.homeId,"Home depth not bound to actual wallpaper");
  check(new java.io.File(fresh.device.files,"depth/"+DefaultAppearance.ID+"/foreground.png").isFile(),"Direct boot layer missing");
  Secure.putInt(fresh.getContentResolver(),"hecker_lockscreen_clock_opacity",91);
  check(!DefaultAppearance.apply(fresh,false)&&fresh.wallpaper.sets==1&&value(fresh,"hecker_lockscreen_clock_opacity")==91,"Reboot overwrote a user choice");
  fresh.wallpaper.homeId++;DefaultAppearance.reconcile(fresh);check(value(fresh,"hecker_depth_wallpaper")==0&&value(fresh,DefaultAppearance.HOME_DEPTH)==0,"Changing wallpaper retained old subject overlay");
  Context split=user(args[0],"split");DefaultAppearance.apply(split,false);split.wallpaper.lockId=split.wallpaper.homeId;split.wallpaper.homeId++;
  DefaultAppearance.reconcile(split);check(value(split,"hecker_depth_wallpaper")==1&&value(split,DefaultAppearance.HOME_DEPTH)==0,"Home change disabled separate lock wallpaper");
  Context lockOnly=user(args[0],"lock-only");DefaultAppearance.apply(lockOnly,false);lockOnly.wallpaper.lockId=999;
  DefaultAppearance.reconcile(lockOnly);check(value(lockOnly,"hecker_depth_wallpaper")==0&&value(lockOnly,DefaultAppearance.HOME_DEPTH)==1,"Lock change disabled owned home wallpaper");
  Context custom=user(args[0],"custom");custom.wallpaper.savedHome=true;
  check(!DefaultAppearance.apply(custom,false)&&custom.wallpaper.sets==0&&custom.resolver.values.isEmpty(),"Existing photo overwritten");
  Context clock=user(args[0],"clock");Secure.putInt(clock.getContentResolver(),"hecker_custom_lockscreen_clock",0);
  check(!DefaultAppearance.apply(clock,false)&&clock.wallpaper.sets==0,"Existing disabled clock overwritten");
  Context live=user(args[0],"live");live.wallpaper.live=true;
  check(!DefaultAppearance.apply(live,false)&&live.wallpaper.sets==0,"Live wallpaper overwritten");
  check(DefaultAppearance.apply(custom,true)&&custom.wallpaper.sets==1,"Explicit Apply Caracal did not restore defaults");
  Context previous=user(args[0],"previous");previous.wallpaper.savedHome=true;
  Secure.putString(previous.resolver,"hecker_depth_wallpaper_id","ca2aca1da00000000000000000000001");Secure.putInt(previous.resolver,DefaultAppearance.WALLPAPER_ID,previous.wallpaper.homeId);
  Secure.putInt(previous.resolver,"hecker_depth_wallpaper",1);Secure.putInt(previous.resolver,"hecker_lockscreen_clock_opacity",91);
  previous.device.prefs.edit().putBoolean("applied_v1",true).commit();
  check(DefaultAppearance.apply(previous,false)&&DefaultAppearance.ID.equals(Secure.getString(previous.resolver,"hecker_depth_wallpaper_id")),"Owned v1 did not migrate to modest v2");
  check(value(previous,"hecker_lockscreen_clock_opacity")==91&&value(previous,DefaultAppearance.HOME_DEPTH)==1,"Migration reset chosen clock or omitted home depth");
  Context changed=user(args[0],"changed-v1");changed.wallpaper.savedHome=true;changed.wallpaper.lockId=23;changed.wallpaper.homeId=555;
  Secure.putString(changed.resolver,"hecker_depth_wallpaper_id","ca2aca1da00000000000000000000001");Secure.putInt(changed.resolver,DefaultAppearance.WALLPAPER_ID,23);Secure.putInt(changed.resolver,"hecker_depth_wallpaper",1);
  check(!DefaultAppearance.apply(changed,false)&&changed.wallpaper.sets==0,"Migration overwrote a changed home photo");
  Context other=user(args[0],"user10");check(DefaultAppearance.apply(other,false)&&other.wallpaper.sets==1,"Other user's fresh default was suppressed");
  Context bad=user(args[0],"bad");android.graphics.BitmapFactory.mismatch=true;
  try{DefaultAppearance.apply(bad,false);throw new AssertionError("Mismatched pair accepted");}catch(java.io.IOException expected){}
  check(bad.wallpaper.sets==0&&bad.resolver.values.isEmpty(),"Invalid pair published wallpaper/settings");
  System.out.println("Production default appearance: fresh positive-ID built-in, saved/live/clock preservation, restart, explicit restore, per-user DE layers and wallpaper-change reset PASS");
 }
}'''
}


class DefaultAppearanceTests(unittest.TestCase):
    def test_fresh_defaults_and_existing_user_choices(self):
        with tempfile.TemporaryDirectory(prefix='hecker-appearance-') as directory:
            work=Path(directory);files=[]
            for name,source in STUBS.items():
                path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);files.append(str(path))
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work/'classes'),*files,
                str(ROOT/'app/src/org/hecker/suite/DefaultAppearance.java')],check=True)
            subprocess.run(['java','-cp',str(work/'classes'),'DefaultAppearanceProbe',str(work)],check=True)

    def test_bundled_layers_are_aligned_and_foreground_has_real_transparency(self):
        folder=ROOT/'app/assets/wallpapers/caracal-dawn-v2'
        with Image.open(folder/'background.png') as back, Image.open(folder/'foreground.png') as front:
            self.assertEqual(back.size,front.size)
            self.assertEqual(front.mode,'RGBA')
            self.assertLessEqual(max(front.size),4096)
            alpha=front.getchannel('A')
            self.assertEqual(alpha.getextrema(),(0,255))
            self.assertLessEqual(alpha.crop((0,0,front.width,int(front.height*.1))).getextrema()[1],1)
            self.assertIsNotNone(alpha.getbbox())

if __name__=='__main__':unittest.main()

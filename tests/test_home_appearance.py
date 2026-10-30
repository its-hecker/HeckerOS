# SPDX-License-Identifier: Apache-2.0
"""Run the separate production Home installer with actual per-display wallpaper ID behavior."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import json, hashlib
from PIL import Image
from test_default_appearance import STUBS

ROOT=Path(__file__).resolve().parents[1]

class HomeAppearanceTests(unittest.TestCase):
    def test_separate_home_ownership_migration_and_disabled_choice(self):
        sources=dict(STUBS)
        sources.pop('DefaultAppearanceProbe.java')
        sources.pop('org/hecker/suite/HomeAppearance.java')
        sources['android/graphics/Bitmap.java']=sources['android/graphics/Bitmap.java'].replace('public int getWidth()', 'public boolean hasAlpha(){return true;}public int getWidth()')
        sources['android/graphics/Canvas.java']=sources['android/graphics/Canvas.java'].replace('public Canvas(Bitmap b){}','public Canvas(Bitmap b){}public void drawBitmap(Bitmap b,float x,float y,Paint p){}')
        sources['android/app/WallpaperManager.java']='''package android.app;public class WallpaperManager {
 public static final int FLAG_SYSTEM=1,FLAG_LOCK=2;public int homeId=19,lockId=-1,sets,lastFlags;public boolean live,savedHome,savedLock;
 public static WallpaperManager getInstance(android.content.Context c){return c.wallpaper;}
 public int getWallpaperId(int flag){return flag==FLAG_SYSTEM?homeId:lockId;}public Object getWallpaperInfo(){return live?this:null;}
 public android.os.ParcelFileDescriptor getWallpaperFile(int flag){return (flag==FLAG_SYSTEM?savedHome:savedLock)?new android.os.ParcelFileDescriptor():null;}
 public int setBitmap(android.graphics.Bitmap b,Object crop,boolean backup,int flags){
  if(!backup||b.getHeight()>1600)throw new AssertionError();lastFlags=flags;sets++;
  if(flags==3){homeId=80+sets;lockId=-1;savedHome=true;savedLock=false;}
  else if(flags==1){if(lockId<0&&savedHome){lockId=homeId;savedLock=true;}homeId=80+sets;savedHome=true;}
  else throw new AssertionError("Unexpected installer target");return homeId;
 }}'''
        sources['HomeInstallerProbe.java']='''import android.content.Context;import android.provider.Settings.Secure;import org.hecker.suite.*;
public class HomeInstallerProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static Context user(String root,String name){Context c=new Context();c.device=new Context();c.files=new java.io.File(root,name+"-ce");c.device.files=new java.io.File(root,name+"-de");return c;}
 static void set(Context c,String k,int n){Secure.putInt(c.resolver,k,n);}static int get(Context c,String k){return Secure.getInt(c.resolver,k,-1);}
 public static void main(String[] args)throws Exception {
  Context c=user(args[0],"fresh");DefaultAppearance.apply(c,false);int lock=c.wallpaper.homeId;
  set(c,"hecker_lockscreen_clock_opacity",91);check(HomeAppearance.apply(c,false));
  check(c.wallpaper.lastFlags==1&&c.wallpaper.lockId==lock&&c.wallpaper.homeId!=lock);
  check(get(c,DefaultAppearance.WALLPAPER_ID)==lock&&get(c,DefaultAppearance.HOME_WALLPAPER_ID)==c.wallpaper.homeId);
  check(DefaultAppearance.ID.equals(Secure.getString(c.resolver,DepthWallpaperProvider.ID)));
  check(HomeAppearance.ID.equals(Secure.getString(c.resolver,HomeAppearance.KEY))&&get(c,"hecker_lockscreen_clock_opacity")==91);
  check(new java.io.File(c.device.files,"depth/"+HomeAppearance.ID+"/foreground.png").isFile());
  check(!HomeAppearance.apply(c,false)&&c.wallpaper.sets==2);
  c.wallpaper.homeId++;DefaultAppearance.reconcile(c);check(get(c,DefaultAppearance.HOME_DEPTH)==0&&get(c,"hecker_depth_wallpaper")==1);
  Context disabled=user(args[0],"disabled");DefaultAppearance.apply(disabled,false);set(disabled,DefaultAppearance.HOME_DEPTH,0);
  check(HomeAppearance.apply(disabled,false)&&get(disabled,DefaultAppearance.HOME_DEPTH)==0);
  Context custom=user(args[0],"custom");custom.wallpaper.savedHome=true;check(!HomeAppearance.apply(custom,false)&&custom.wallpaper.sets==0);
  check(HomeAppearance.apply(custom,true)&&get(custom,DefaultAppearance.HOME_DEPTH)==1);
  Context live=user(args[0],"live");DefaultAppearance.apply(live,false);live.wallpaper.live=true;check(!HomeAppearance.apply(live,false));
  Context other=user(args[0],"user10");DefaultAppearance.apply(other,false);check(HomeAppearance.apply(other,false));
  System.out.println("Independent Home artwork: shared-lock retention, per-display IDs, ownership migration, custom/live preservation, disabled choice, explicit restore and per-user DE layers PASS");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='hecker-home-art-') as directory:
            work=Path(directory);files=[]
            for name,source in sources.items():
                p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);files.append(str(p))
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(work/'classes'),*files,
                str(ROOT/'app/src/org/hecker/suite/DefaultAppearance.java'),str(ROOT/'app/src/org/hecker/suite/HomeAppearance.java')],check=True)
            subprocess.run(['java','-cp',str(work/'classes'),'HomeInstallerProbe',str(work)],check=True)

    def test_bounded_distinct_layers_and_recorded_hashes(self):
        directory=ROOT/'app/assets/wallpapers/caracal-harbor'
        origin=json.loads((directory/'ORIGIN.json').read_text())
        with Image.open(directory/'background.webp') as back,Image.open(directory/'foreground.webp') as front:
            self.assertEqual(back.size,front.size);self.assertLessEqual(max(front.size),1600)
            self.assertEqual(front.mode,'RGBA');self.assertEqual(front.getchannel('A').getextrema(),(0,255))
            self.assertLessEqual(front.getchannel('A').crop((0,0,front.width,int(front.height*.14))).getextrema()[1],1)
        for name in ['background','foreground']:
            self.assertEqual(hashlib.sha256((directory/(name+'.webp')).read_bytes()).hexdigest(),origin['layers'][name]['sha256'])

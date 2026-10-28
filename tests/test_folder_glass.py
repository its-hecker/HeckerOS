# SPDX-License-Identifier: Apache-2.0
"""Exercise the prepared native folder draw path through open/close/cancel clip states."""
from pathlib import Path
import json
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from check_launcher_studio import method


class FolderGlassTests(unittest.TestCase):
    def test_native_reveal_clips_glass_on_every_open_and_close_frame(self):
        revision = json.loads((ROOT / 'integration/source-revisions.json').read_text())['pixelos_inputs']['packages/apps/Launcher3']['revision']
        source = ROOT / 'out/native-source-cache' / revision / 'packages/apps/Launcher3/src/com/android/launcher3/folder/Folder.java'
        # Fresh CI can use the exact published method as a small immutable fixture.
        if not source.exists(): source = ROOT / 'tests/fixtures/folder-dispatch-android17.snapshot'
        native = source.read_text()
        draw = method(native, 'protected void dispatchDraw(Canvas canvas)')
        patches = json.loads((ROOT / 'aosp-launcher/patches.json').read_text())['src/com/android/launcher3/folder/Folder.java']
        for pair in patches:
            self.assertEqual(draw.count(pair['old']), 1)
            draw = draw.replace(pair['old'], pair['new'])
        probe = r'''
import java.util.*;
class Canvas {
 int clip=100,depth; final ArrayList<Integer> stack=new ArrayList<>();
 final ArrayList<String> events=new ArrayList<>();
 int save(){stack.add(clip);return ++depth;}
 void clipPath(int value){clip=Math.min(clip,value);events.add("clip:"+clip);}
 void restoreToCount(int count){clip=stack.remove(stack.size()-1);depth--;}
}
class PathWrapper {int value; PathWrapper(int x){value=x;}int getPath(){return value;}}
class BaseFolder {protected void dispatchDraw(Canvas c){c.events.add("icons:"+c.clip);}}
class NativeGradient {void draw(Canvas c){c.events.add("native:"+c.clip);}}
class Blur {void drawFolderBlur(Canvas c,PathWrapper p,FolderDrawProbe f){c.events.add("blur:"+c.clip);}}
class Glass {static boolean enabled=true;static boolean drawFolder(FolderDrawProbe f,Canvas c,NativeGradient b){
 if(!enabled)return false;c.events.add("glass:"+c.clip);return true;}}
class FolderDrawProbe extends BaseFolder {
 PathWrapper mClipPath;boolean mIsAnimatingClosed;
 final NativeGradient mBackground=new NativeGradient();final Blur mBlurBackgroundHelper=new Blur();
''' + draw.replace('com.android.launcher3.hecker.HeckerLauncherGlass.drawFolder', 'Glass.drawFolder') + r'''
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[]args){
  FolderDrawProbe folder=new FolderDrawProbe();
  for(boolean glass:new boolean[]{false,true})for(boolean closing:new boolean[]{false,true}){
   Glass.enabled=glass;folder.mIsAnimatingClosed=closing;
   for(int frame=0;frame<101;frame++){
    // A null clip is the steady or cancelled state. The clip changes on every animation frame.
    folder.mClipPath=frame==100?null:new PathWrapper(frame);
    Canvas c=new Canvas();folder.dispatchDraw(c);
    int expected=frame==100?100:frame;
    check(c.events.contains((glass?"glass:":"native:")+expected),"Background escaped live reveal: "+c.events);
    check(c.events.stream().filter(e->e.startsWith("glass:")||e.startsWith("native:")).count()==1,"Duplicate background");
    check(c.events.contains("icons:"+(closing?100:expected)),"Native protruding close icons changed");
    check(c.depth==0&&c.clip==100,"Clip state leaked into dock or later frames");
    check(glass==c.events.stream().noneMatch(e->e.startsWith("blur:")),"Native blur fallback changed");
   }
  }
  System.out.println("Actual native folder dispatch: 404 reveal/steady/cancel cases, no trailing un-clipped glass, native icon/fallback flow PASS");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='hecker-folder-clip-') as directory:
            path = Path(directory) / 'FolderDrawProbe.java'; path.write_text(probe)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', directory, str(path)], check=True)
            subprocess.run(['java', '-cp', directory, 'FolderDrawProbe'], check=True)

    def test_monochrome_masks_retain_alpha_and_remove_legacy_square(self):
        probe = r'''
import java.util.Arrays;import com.android.launcher3.hecker.GlassIconMask;
class GlassMaskProbe{
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){
  int[] mono=new int[100];mono[44]=0x80ffffff;mono[45]=0xffffffff;int[] before=mono.clone();
  GlassIconMask.extract(mono,10,10);check(Arrays.equals(mono,before));
  check(Arrays.equals(GlassIconMask.bounds(mono,10,10),new int[]{4,4,6,5}));
  int[] legacy=new int[100];Arrays.fill(legacy,0xff222222);legacy[44]=0xffeeeeee;legacy[45]=0xffeeeeee;
  GlassIconMask.extract(legacy,10,10);check(legacy[0]>>>24==0);check(legacy[44]>>>24==255);
  check(Arrays.equals(GlassIconMask.bounds(legacy,10,10),new int[]{4,4,6,5}));
  System.out.println("Monochrome icons: native partial-alpha edges preserved; legacy opaque background removed PASS");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='hecker-glass-mask-') as directory:
            path=Path(directory)/'GlassMaskProbe.java';path.write_text(probe)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',directory,str(path),
                str(ROOT/'aosp-launcher/src/com/android/launcher3/hecker/GlassIconMask.java')],check=True)
            subprocess.run(['java','-cp',directory,'GlassMaskProbe'],check=True)


if __name__ == '__main__': unittest.main()

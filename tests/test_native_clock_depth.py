# SPDX-License-Identifier: Apache-2.0
"""Native clock-mask coordinate mapping and immutable factory integration regression."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from prepare import replace_guarded
import customization_port

FACTORY = ('frameworks/base/packages/SystemUI/compose/features/src/com/android/systemui/'
           'keyguard/ui/composable/elements/LockscreenElementFactoryImpl.kt')


class NativeClockDepthTests(unittest.TestCase):
    def test_root_mask_stays_registered_during_native_clock_transforms(self):
        program = r'''import com.android.systemui.hecker.DepthWallpaperGeometry;
public class NativeClockDepthGeometryProbe {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static void near(float a,float b){if(Math.abs(a-b)>.02f)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  int count=0;
  for(float sx:new float[]{.5f,1f,1.8f,-1f})for(float sy:new float[]{.6f,1f,2f})
  for(float angle:new float[]{-.4f,0f,.6f})for(float tx:new float[]{-120f,0f,360f})
  for(float ty:new float[]{-48f,0f,640f}){
   float c=(float)Math.cos(angle),s=(float)Math.sin(angle);
   // The three samples come from LayoutCoordinates.localPositionOf(root, ...).
   float ox=(-c*tx-s*ty)/sx,oy=(s*tx-c*ty)/sy;
   float rx=(c*(1024-tx)-s*ty)/sx,ry=(-s*(1024-tx)-c*ty)/sy;
   float dx=(-c*tx+s*(1024-ty))/sx,dy=(s*tx+c*(1024-ty))/sy;
   float[] matrix=new float[9];
   check(DepthWallpaperGeometry.rootToLocal(matrix,ox,oy,rx,ry,dx,dy,1024));
   for(float x:new float[]{0,24,180,420})for(float y:new float[]{0,72,240,480}){
    float rootX=c*sx*x-s*sy*y+tx,rootY=s*sx*x+c*sy*y+ty;
    near(matrix[0]*rootX+matrix[1]*rootY+matrix[2],x);
    near(matrix[3]*rootX+matrix[4]*rootY+matrix[5],y);
    count++;
   }
   check(matrix[6]==0&&matrix[7]==0&&matrix[8]==1);
  }
  float[] out=new float[]{1,2,3,4,5,6,7,8,9};
  check(!DepthWallpaperGeometry.rootToLocal(out,0,0,0,0,0,0,1024));
  check(!DepthWallpaperGeometry.rootToLocal(out,Float.NaN,0,1024,0,0,1024,1024));
  check(!DepthWallpaperGeometry.rootToLocal(out,0,0,Float.POSITIVE_INFINITY,0,0,1024,1024));
  check(!DepthWallpaperGeometry.rootToLocal(out,0,0,1024,0,0,1024,0));
  check(!DepthWallpaperGeometry.rootToLocal(new float[8],0,0,1024,0,0,1024,1024));
  check(!DepthWallpaperGeometry.rootToLocal(null,0,0,1024,0,0,1024,1024));
  for(int i=0;i<out.length;i++)check(out[i]==i+1);
  System.out.println(count+" native clock scale/rotation/translation mask registrations and invalid geometry guards PASS");
 }
}'''
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            probe = work/'NativeClockDepthGeometryProbe.java'
            probe.write_text(program)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', temporary,
                            str(ROOT/'framework/lockscreen/DepthWallpaperGeometry.java'), str(probe)], check=True)
            subprocess.run(['java', '-cp', temporary, 'NativeClockDepthGeometryProbe'], check=True)

    def test_factory_adapters_apply_repeat_and_revert_on_real_native_input(self):
        # Exact source from frameworks/base@4af276bc5ceea071ef0da5ea440cda4470c08df6.
        original = (ROOT/'tests/fixtures/clock-element-factory-4af276b.snapshot').read_text()
        original += '\n// unrelated maintainer comment\n'
        pairs = json.loads((ROOT/'framework/customization-patches.json').read_text())[FACTORY]
        prepared = original
        for pair in pairs:
            prepared = replace_guarded(prepared, pair['old'], pair['new'], FACTORY)
        repeated = prepared
        for pair in pairs:
            repeated = replace_guarded(repeated, pair['old'], pair['new'], FACTORY)
        self.assertEqual(repeated, prepared)
        for pair in reversed(pairs):
            prepared = replace_guarded(prepared, pair['old'], pair['new'], FACTORY, True)
        self.assertEqual(prepared, original)

    def test_prepared_coral_depth_copies_upgrade_in_place_and_reject_unknown_edits(self):
        legacy = json.loads((ROOT/'tests/fixtures/native-depth-53caa8d.snapshot.json').read_text())
        all_copies = json.loads((ROOT/'framework/customization-copies.json').read_text())
        history = json.loads((ROOT/'framework/customization-copy-history.json').read_text())
        names = {'HeckerDepthWallpaper.kt', 'DepthWallpaperGeometry.java',
                 'HeckerClockHandoff.kt', 'HeckerNativeClockDepth.kt'}
        copies = {target: source for target, source in all_copies.items() if Path(target).name in names}
        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary); source = work/'suite'; rom = work/'rom'
            (source/'framework').mkdir(parents=True)
            for target, relative in copies.items():
                dest = source/relative; dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes((ROOT/relative).read_bytes())
            (source/'framework/customization-copies.json').write_text(json.dumps(copies))
            (source/'framework/customization-copy-history.json').write_text(json.dumps(history))
            patches = json.loads((ROOT/'framework/customization-patches.json').read_text())
            (source/'framework/customization-patches.json').write_text(json.dumps({FACTORY: patches[FACTORY]}))
            marker = rom/'frameworks/base/core/java/android/content/res/Resources.java'
            marker.parent.mkdir(parents=True); marker.touch()
            factory = rom/FACTORY; factory.parent.mkdir(parents=True)
            original = (ROOT/'tests/fixtures/clock-element-factory-4af276b.snapshot').read_text()
            factory.write_text(original + '\n// maintainer edit\n')
            for relative, text in legacy['files'].items():
                dest = rom/relative; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_text(text)
            with patch.object(customization_port, 'ROOT', source):
                changes = customization_port.customization_plan(rom, replace_guarded)
                self.assertEqual(len(changes), 5)
                for path, text in changes.items():
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(text if isinstance(text, bytes) else text.encode())
                for target, relative in copies.items():
                    self.assertEqual((rom/target).read_bytes(), (ROOT/relative).read_bytes())
                self.assertIn('// maintainer edit', factory.read_text())
                self.assertEqual({}, customization_port.customization_plan(rom, replace_guarded))
                relative = next(iter(legacy['files']))
                path = rom/relative
                unexpected = legacy['files'][relative] + '\n// unreviewed private adapter edit\n'
                path.write_text(unexpected)
                with self.assertRaises(ValueError):
                    customization_port.customization_plan(rom, replace_guarded)
                self.assertEqual(path.read_text(), unexpected)


if __name__ == '__main__':
    unittest.main()

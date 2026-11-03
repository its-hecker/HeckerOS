"""Execute depth alignment and information layout; check the complete provider permission graph."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from aosp_launcher_port import EXTRA_PERMISSIONS


class UiDepthFeedbackTests(unittest.TestCase):
    def test_layer_alignment_information_rectangles_and_search_clearance(self):
        program = '''import org.hecker.suite.DepthLayerGeometry;
import com.android.launcher3.hecker.ClockInfoLayout;
import com.android.launcher3.hecker.DockSearchLayout;
public class FeedbackGeometryProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  float[] aligned=DepthLayerGeometry.fit(1440,3040,720,1520,100,0,0);
  check(aligned[0]==2&&aligned[1]==0&&aligned[2]==0);
  float[] cutout=DepthLayerGeometry.fit(1000,2000,500,500,100,0,0);
  check(cutout[0]==2&&cutout[1]==0&&cutout[2]==500);
  float[] moved=DepthLayerGeometry.fit(1000,2000,500,500,50,-20,25);
  check(moved[0]==1&&moved[1]==50&&moved[2]==1250);
  try{DepthLayerGeometry.fit(0,100,10,10,100,0,0);throw new AssertionError();}
  catch(IllegalArgumentException expected){}
  int cases=0;
  for(int w:new int[]{120,240,360,720,1440})for(int h:new int[]{56,112,184,320,720})
  for(float d:new float[]{1,1.5f,2,3,4})for(int position=0;position<4;position++)
  for(boolean message:new boolean[]{false,true})for(boolean weather:new boolean[]{false,true}){
   float[] r=ClockInfoLayout.bounds(w,h,d,position,message,weather);
   check(r[0]>=0&&r[1]>=0&&r[2]>0&&r[3]>0&&r[0]+r[2]<=w+.01&&r[1]+r[3]<=h+.01);
   check(r[4]>=0&&r[5]>=0&&r[6]>=0&&r[7]>=0&&r[4]+r[6]<=w+.01&&r[5]+r[7]<=h+.01);
   if(message||weather)check(r[0]+r[2]<=r[4]+.01||r[4]+r[6]<=r[0]+.01
      ||r[1]+r[3]<=r[5]+.01||r[5]+r[7]<=r[1]+.01);
   cases++;
  }
  for(int d=1;d<=4;d++)for(int height=36;height<=64;height++)for(int nav:new int[]{0,16,48}){
   int touch=Math.max(48,height)*d,offset=(nav+8)*d,gap=12*d,padding=48*d;
   int extra=DockSearchLayout.extraHeight(padding,offset,touch,gap);
   check(padding+extra>=offset+touch+gap);
  }
  System.out.println(cases+" clock/info layouts remain disjoint; scaled/cropped subject registration and variable search sizes PASS");
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            work = Path(folder); probe = work / 'FeedbackGeometryProbe.java'; probe.write_text(program)
            files = [ROOT / 'app/src/org/hecker/suite/DepthLayerGeometry.java',
                     ROOT / 'aosp-launcher/src/com/android/launcher3/hecker/ClockInfoLayout.java',
                     ROOT / 'aosp-launcher/src/com/android/launcher3/hecker/DockSearchLayout.java', probe]
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', folder,
                            *map(str, files)], check=True)
            subprocess.run(['java', '-cp', folder, 'FeedbackGeometryProbe'], check=True)

    def test_launcher_and_systemui_can_pass_the_provider_manifest_gate(self):
        android = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT / 'app/AndroidManifest.xml').getroot()
        provider = manifest.find("application/provider[@android:name='.DepthWallpaperProvider']", {'android': android[1:-1]})
        permission = provider.get(android + 'readPermission')
        self.assertEqual(permission, 'org.hecker.suite.permission.READ_DEPTH')
        declaration = next(node for node in manifest.findall('permission') if node.get(android + 'name') == permission)
        self.assertEqual(declaration.get(android + 'protectionLevel'), 'signature')
        self.assertIn(permission, EXTRA_PERMISSIONS)
        self.assertNotIn('android.permission.STATUS_BAR', EXTRA_PERMISSIONS)
        patches = json.loads((ROOT / 'framework/customization-patches.json').read_text())
        self.assertTrue(any(permission in item['new'] for item in patches['frameworks/base/packages/SystemUI/AndroidManifest.xml']))

    def test_new_labels_have_russian_and_chinese_fallbacks(self):
        for module, filename in [('app', 'ui_depth_feedback.xml'), ('aosp-launcher', 'ui_feedback.xml')]:
            default = {node.get('name') for node in ET.parse(ROOT / module / 'res/values' / filename).getroot()}
            for locale in ['values-ru', 'values-zh-rCN']:
                translated = {node.get('name') for node in ET.parse(ROOT / module / 'res' / locale / filename).getroot()}
                self.assertEqual(default, translated)


if __name__ == '__main__': unittest.main()

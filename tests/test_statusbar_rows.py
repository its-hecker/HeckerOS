# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
class StatusBarRowsTests(unittest.TestCase):
    def test_native_window_geometry(self):
        java=r'''import com.android.systemui.hecker.StatusBarRowsPolicy;
public class RowsProbe{
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[]args){int cases=0;
 for(int h:new int[]{0,24,44,52,60,96,192,360})for(int top:new int[]{0,2,5,12,90})
 for(int bottom:new int[]{0,2,8})for(int gap=0;gap<=16;gap++)for(boolean dual:new boolean[]{false,true}){
  var r=StatusBarRowsPolicy.rows(h,top,bottom,gap,dual);
  check(r.top>=0&&r.height>=0&&r.second>=r.top);
  check(r.top+r.height<=h&&r.second+r.height<=h);
 }
 for(int available=0;available<=480;available++)for(int width:new int[]{0,48,80,160,240,360,900})
 for(int reserve:new int[]{0,24,48})for(int alignment=0;alignment<3;alignment++){
  int left=17,right=left+available;
  var s=StatusBarRowsPolicy.slot(left,right,width,48,reserve,4,alignment);
  check(s.lyricWidth>=0&&s.iconsWidth>=0&&s.lyricLeft>=left&&s.iconsLeft>=left);
  check(s.lyricLeft+s.lyricWidth<=right&&s.iconsLeft+s.iconsWidth<=right);
  if(s.lyricWidth>0&&s.iconsWidth>0)check(s.lyricLeft+s.lyricWidth<=s.iconsLeft||s.iconsLeft+s.iconsWidth<=s.lyricLeft);
  cases++;
 }
 check(StatusBarRowsPolicy.heightDp(-1)==44&&StatusBarRowsPolicy.heightDp(1)==52&&StatusBarRowsPolicy.heightDp(999)==60);
 System.out.println("Status bar: "+cases+" bounded lyric/icon placements, row spacing, tiny viewports and height presets PASS");
}}'''
        with tempfile.TemporaryDirectory(prefix="hecker-status-rows-") as temp:
            path=Path(temp);(path/"RowsProbe.java").write_text(java)
            subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(path/"classes"),str(path/"RowsProbe.java"),
                str(ROOT/"framework/statusbar/StatusBarRowsPolicy.java")],check=True)
            subprocess.run(["java","-cp",str(path/"classes"),"RowsProbe"],check=True)
    def test_framework_height_overlay_contract(self):
        android="{http://schemas.android.com/apk/res/android}"
        for name,height in [("Compact",44),("Balanced",52),("Tall",60)]:
            source=ROOT/"rom/overlays/StatusBarHeight"/name
            manifest=ET.parse(source/"AndroidManifest.xml").getroot()
            overlay=manifest.find("overlay")
            self.assertEqual(overlay.get(android+"targetPackage"),"android")
            self.assertEqual(overlay.get(android+"isStatic"),"false")
            self.assertEqual(overlay.get(android+"category"),"org.hecker.statusbar.height")
            dimensions={n.get("name"):n.text for n in ET.parse(source/"res/values/dimens.xml").getroot()}
            self.assertEqual(set(dimensions),{"status_bar_height_default","status_bar_height","status_bar_height_portrait","status_bar_height_landscape"})
            self.assertEqual(set(dimensions.values()),{str(height)+"dp"})
            self.assertIn('certificate: "platform"',(source/"Android.bp").read_text())
            self.assertIn("HeckerStatusBarHeight"+name,(ROOT/"rom/common.mk").read_text())
if __name__=="__main__":unittest.main()

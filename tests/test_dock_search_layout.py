from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DockSearchLayoutTest(unittest.TestCase):
    def test_dock_row_cannot_intersect_search_or_navigation(self):
        program = '''import com.android.launcher3.hecker.DockSearchLayout;
public class DockSearchProbe {
 public static void main(String[] args) {
  for(int density:new int[]{1,2,3,4})for(int nav:new int[]{0,16,24,48})
  for(int nativeBottom:new int[]{0,24,48,80,112})for(int nativeOffset:new int[]{0,8,20,36}) {
   int height=48*density,gap=12*density;
   int offset=Math.max(nativeOffset*density,(nav+8)*density),padding=nativeBottom*density;
   int extra=DockSearchLayout.extraHeight(padding,offset,height,gap);
   int parent=200*density+extra;
   int iconBottom=parent-padding-extra,barTop=parent-offset-height,barBottom=parent-offset;
   if(iconBottom+gap>barTop)throw new AssertionError("dock/search collision");
   if(barBottom>parent-(nav+8)*density)throw new AssertionError("navigation collision");
   if(extra<0)throw new AssertionError("negative reservation");
  }
  System.out.println("Dock reservation: 320 density/navigation/native-slot combinations keep icons, search and navigation apart PASS");
 }
}'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'DockSearchProbe.java'
            path.write_text(program)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', directory,
                            str(ROOT / 'aosp-launcher/src/com/android/launcher3/hecker/DockSearchLayout.java'), str(path)], check=True)
            subprocess.run(['java', '-cp', directory, 'DockSearchProbe'], check=True)

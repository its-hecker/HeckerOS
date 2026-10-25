# SPDX-License-Identifier: Apache-2.0
"""The production constructor must retain the selected overlay's paths and dimensions."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class BatteryGeometryTest(unittest.TestCase):
    def test_oneui_and_other_themes_keep_their_own_silhouette(self):
        source = (ROOT / 'framework/themes/HeckerBatteryDrawable.java').read_text()
        constructor = source[source.index('    private HeckerBatteryDrawable('):source.index('    public static HeckerBatteryDrawable create(')]
        constructor = constructor.replace('HeckerBatteryDrawable(', 'BatteryProbe(')
        constructor = constructor.replace('android.provider.Settings.Secure', 'Secure').replace('android.os.UserHandle.USER_CURRENT', '-2')
        program = '''public class BatteryProbe {
 final boolean oneUi,levelColors,horizontal;final Resources res;final String pkg;final int width,height;final Path perimeter,mask,bolt,saver;
 static class Context {Object getContentResolver(){return null;}}
 static class Secure {static int getIntForUser(Object resolver,String name,int fallback,int user){return 1;}}
 static class Path {boolean original=true;enum Direction {CW}void reset(){original=false;}void addRoundRect(float a,float b,float c,float d,float e,float f,Direction direction){original=false;}}
 static class Resources {int width,height;boolean horizontal;java.util.Map<String,Path> paths=new java.util.HashMap<>();Resources(int w,int h,boolean horizontal){width=w;height=h;this.horizontal=horizontal;}}
 int integer(String name,int fallback){return name.endsWith("Width")?res.width:res.height;}
 boolean bool(String name,boolean fallback){return res.horizontal;}
 Path path(String name){return res.paths.computeIfAbsent(name,k->new Path());}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
''' + constructor + '''
 public static void main(String[] args){
  for(String pkg:new String[]{"com.android.systemui.battery.oneui7","another.battery"})for(boolean horizontal:new boolean[]{false,true}){
   Resources res=new Resources(37,19,horizontal);BatteryProbe d=new BatteryProbe(new Context(),res,pkg);
   check(d.width==37&&d.height==19&&d.horizontal==horizontal&&d.levelColors);
   check(d.perimeter==res.paths.get("config_batterymeterPerimeterPath")&&d.perimeter.original);
   check(d.mask==res.paths.get("config_batterymeterFillMask")&&d.mask.original);
  }
  System.out.println("Production battery geometry: overlay paths, dimensions and fill direction retained for One UI and other themes PASS");
 }
}'''
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / 'BatteryProbe.java'
            file.write_text(program)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root), str(file)], check=True)
            subprocess.run(['java', '-cp', str(root), 'BatteryProbe'], check=True)


if __name__ == '__main__':
    unittest.main()

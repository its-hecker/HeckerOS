# SPDX-License-Identifier: Apache-2.0
"""Exercise the actual launch path without an Android window manager."""
from pathlib import Path
import subprocess
import tempfile
import unittest
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from check_launcher_studio import method


class GamingPopupTest(unittest.TestCase):
    def test_compatibility_apps_and_aliases_reach_native_freeform_launch(self):
        source = (ROOT/'app/src/org/hecker/suite/GamingPlatform.java').read_text()
        actual = '\n'.join(method(source, anchor) for anchor in (
            'static boolean supportsPopup(', 'static void popup('))
        probe = r'''public class PopupProbe {
static class PM { boolean feature=true; boolean hasSystemFeature(String name){return feature;} }
static class Context {
 final PM pm=new PM(); Intent launched; ActivityOptions options;
 PM getPackageManager(){return pm;} Object getContentResolver(){return this;}
 <T> T getSystemService(Class<T> type){return type.cast(new WindowManager());}
 void startActivity(Intent intent,Object bundle){
  ActivityOptions requested=(ActivityOptions)bundle;
  // Android 17 SafeActivityOptions rejects this even for a platform-signed
  // privileged app with MANAGE_ACTIVITY_TASKS: Suite is not system/SystemUI.
  if(requested.alwaysOnTop)throw new SecurityException("taskAlwaysOnTop requires system or SystemUI");
  launched=intent;options=requested;
 }
}
static class Settings {static class Global {static int enabled;
 static int getInt(Object r,String n,int fallback){return enabled;}}}
static class Intent {
 static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_MULTIPLE_TASK=2;
 boolean resolved=true;int flags;
 Object resolveActivity(PM pm){return resolved?this:null;}
 Intent addFlags(int value){flags|=value;return this;}
}
static class Rect {
 int left,top,right,bottom;
 Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 int width(){return right-left;}int height(){return bottom-top;}
}
static class WindowManager { Metrics getCurrentWindowMetrics(){return new Metrics();} }
static class Metrics { Rect getBounds(){return new Rect(0,0,1000,600);} }
public static class ActivityOptions {
 int mode,display;Rect bounds;boolean alwaysOnTop;
 static ActivityOptions makeBasic(){return new ActivityOptions();}
 public void setLaunchWindowingMode(int value){mode=value;}
 void setLaunchDisplayId(int value){display=value;}
 void setLaunchBounds(Rect rect){bounds=rect;}
 Object toBundle(){return this;}
 public void setTaskAlwaysOnTop(boolean value){alwaysOnTop=value;}
}
''' + actual + r'''
static void check(boolean value){if(!value)throw new AssertionError();}
public static void main(String[] args) throws Exception {
 Context context=new Context();Intent alias=new Intent();
 // No manifest resizeMode API is exposed: Android owns compatibility policy.
 check(supportsPopup(context,alias));popup(context,alias);
 check(context.launched==alias&&alias.flags==3&&context.options.mode==5&&!context.options.alwaysOnTop);
 check(context.options.display==0&&context.options.bounds.width()>0);
 check(context.options.bounds.right<=1000&&context.options.bounds.bottom<=600);
 alias.resolved=false;check(!supportsPopup(context,alias));
 alias.resolved=true;context.pm.feature=false;check(!supportsPopup(context,alias));
 Settings.Global.enabled=1;check(supportsPopup(context,alias));
}
}
'''
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'PopupProbe.java'
            path.write_text(probe)
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',directory,str(path)],check=True)
            subprocess.run(['java','-cp',directory,'PopupProbe'],check=True)

# SPDX-License-Identifier: Apache-2.0
"""Exercise production render/mode callbacks with deterministic window and Binder collaborators."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DashboardLifecycleTests(unittest.TestCase):
    def run_java(self, name, program):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / (name + '.java')
            source.write_text(program)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                            '-d', str(root), str(source)], check=True)
            subprocess.run(['java', '-cp', str(root), name], check=True)

    def test_page_click_after_close_or_replacement_cannot_touch_a_stale_scroll_view(self):
        source = (ROOT / 'app/src/org/hecker/suite/GamingOverlay.java').read_text()
        method = source[source.index('    private void showPage()'):source.index('    private View iconButton(')]
        program = '''public class PageProbe {
 Object c; boolean open; ScrollView content; int page, clears, updates; String fps,temperature,battery,wifi;
 Runnable duringBuild=()->{};
 static class LinearLayout {}
 static class ScrollView {int writes;void removeAllViews(){writes++;}void addView(LinearLayout v){writes++;}void scrollTo(int x,int y){writes++;}}
 static class GamingUi {static LinearLayout column(Object c){return new LinearLayout();}}
 void clearTelemetry(){clears++;}void update(String a,String b,String c,String d){updates++;}
 void buildSettings(LinearLayout v){duringBuild.run();}void buildMonitor(LinearLayout v){duringBuild.run();}void buildTools(LinearLayout v){duringBuild.run();}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
''' + method + '''
 public static void main(String[] args){
  PageProbe p=new PageProbe();p.showPage();check(p.clears==0);
  p.open=true;p.showPage();check(p.clears==0);
  ScrollView old=new ScrollView();p.content=old;p.duringBuild=()->{p.open=false;p.content=null;};p.showPage();check(old.writes==0&&p.updates==0);
  p.open=true;p.content=old;ScrollView replacement=new ScrollView();p.duringBuild=()->p.content=replacement;p.showPage();check(old.writes==0&&replacement.writes==0);
  p.duringBuild=()->{};p.showPage();check(replacement.writes==3&&p.updates==1);
  System.out.println("Production dashboard renderer: closed/replaced windows and live pages PASS");
 }
}'''
        self.run_java('PageProbe', program)

    def test_mode_binder_runs_off_ui_and_stale_session_completions_are_discarded(self):
        source = (ROOT / 'app/src/org/hecker/suite/GamingService.java').read_text()
        method = source[source.index('    @Override public void gameMode(int mode)'):source.index('    @Override public String gameLabel()')]
        method = method.replace('@Override ', '')
        program = '''public class ModeProbe {
 boolean active=true,destroyed,modePending;String gamePackage="game";int modeGeneration,currentMode=1,toasts;
 Queue worker=new Queue(),main=new Queue();Overlay overlay=new Overlay();
 static class Queue {java.util.ArrayDeque<Runnable> tasks=new java.util.ArrayDeque<>();void execute(Runnable r){tasks.add(r);}void post(Runnable r){tasks.add(r);}void run(){tasks.remove().run();}}
 static class Overlay {int refreshes;void refresh(){refreshes++;}void restyle(){throw new AssertionError("Must retain focused window");}}
 static class R {static class string {static int gaming_mode_unavailable=1;}}
 static class GamingPlatform {static int calls,nativeMode=1;static boolean success=true;static boolean gameMode(Object c,String p,int mode){calls++;if(success)nativeMode=mode;return success;}static int gameMode(Object c,String p){calls++;return nativeMode;}}
 boolean modeSupported(int mode){return !modePending&&mode>=1&&mode<=3;}
 String getString(int id){return "unavailable";}void toast(String message){toasts++;}void updateStatus(){}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
''' + method + '''
 public static void main(String[] args){
  ModeProbe p=new ModeProbe();p.gameMode(2);check(GamingPlatform.calls==0&&p.modePending&&p.worker.tasks.size()==1);
  p.gameMode(3);check(p.worker.tasks.size()==1);p.worker.run();check(GamingPlatform.calls==2&&p.currentMode==1);
  p.main.run();check(p.currentMode==2&&!p.modePending&&p.toasts==0);p.gameMode(2);check(p.worker.tasks.isEmpty());
  p.gameMode(3);p.modeGeneration++;p.modePending=false;p.worker.run();p.main.run();check(p.currentMode==2);
  GamingPlatform.success=false;p.gameMode(1);p.worker.run();p.main.run();check(p.toasts==1&&!p.modePending);
  p.active=false;p.gameMode(2);check(p.worker.tasks.isEmpty());
  System.out.println("Production mode switch: background Binder, busy gating, stale session rejection and failures PASS");
 }
}'''
        self.run_java('ModeProbe', program)


if __name__ == '__main__':
    unittest.main()

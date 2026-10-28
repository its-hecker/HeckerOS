# SPDX-License-Identifier: Apache-2.0
"""Execute the shipped grip listener, including cancellation and free shortcut movement."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from check_launcher_studio import method


class GamingMovementTest(unittest.TestCase):
    def test_drag_tap_cancel_rotation_grip_and_accessibility(self):
        source = (ROOT / 'app/src/org/hecker/suite/GamingOverlay.java').read_text()
        actual = method(source, '    private void draggable(')
        handle = method(source, '    private void buildHandle()')
        self.assertIn('restorePosition("handle",p,', handle)
        self.assertIn('draggable(v,v,p,"handle",false)', handle)
        probe = r'''public class MovementProbe {
Object c=new Object();int saved,moves,opens;boolean onLeft;
int dp(float n){return Math.round(n);}
boolean left(){return onLeft;}
void openPanel(){opens++;}
void savePosition(String surface,WindowManager.LayoutParams p){saved++;}
void move(String surface,View v,WindowManager.LayoutParams p,int x,int y){
 if(!v.attached)return; p.x=Math.max(10,Math.min(500,x));p.y=Math.max(20,Math.min(700,y));moves++;
}
static class WindowManager {static class LayoutParams {int x=100,y=200;}}
static class ViewConfiguration {
 static ViewConfiguration get(Object c){return new ViewConfiguration();}int getScaledTouchSlop(){return 8;}
}
static class MotionEvent {
 static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
 int action;float x,y;MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
 int getActionMasked(){return action;}float getRawX(){return x;}float getRawY(){return y;}
}
static class View {
 boolean attached=true;int clicks;OnTouchListener listener;AccessibilityDelegate accessibility;
 boolean isAttachedToWindow(){return attached;}
 interface OnTouchListener{boolean onTouch(View v,MotionEvent e);}
 void setOnTouchListener(OnTouchListener l){listener=l;}
 void setAccessibilityDelegate(AccessibilityDelegate d){accessibility=d;}
 boolean performClick(){clicks++;return true;}
 static class AccessibilityDelegate{
  public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){}
  public boolean performAccessibilityAction(View host,int action,android.os.Bundle args){return false;}
 }
 void send(int a,float x,float y){check(listener.onTouch(this,new MotionEvent(a,x,y)));}
}
static class AccessibilityNodeInfo {
 java.util.Set<Integer> actions=new java.util.HashSet<>();void addAction(AccessibilityAction a){actions.add(a.id);}
 static class AccessibilityAction{
  static final AccessibilityAction ACTION_SCROLL_UP=new AccessibilityAction(1),ACTION_SCROLL_DOWN=new AccessibilityAction(2),
   ACTION_SCROLL_LEFT=new AccessibilityAction(3),ACTION_SCROLL_RIGHT=new AccessibilityAction(4);
  int id;AccessibilityAction(int id){this.id=id;}int getId(){return id;}
 }
}
''' + actual + r'''
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){
 MovementProbe p=new MovementProbe();View grip=new View(),window=new View();WindowManager.LayoutParams lp=new WindowManager.LayoutParams();
 p.draggable(grip,window,lp,"panel",false);check(window.listener==null);
 grip.send(0,10,20);grip.send(2,12,22);grip.send(1,12,22);check(grip.clicks==1&&p.saved==0);
 grip.send(0,10,20);grip.send(2,50,80);grip.send(1,50,80);check(lp.x==140&&lp.y==260&&p.saved==1);
 grip.send(0,10,20);grip.send(2,9000,9000);check(lp.x==500&&lp.y==700);grip.send(3,9000,9000);
 check(lp.x==140&&lp.y==260&&p.saved==1);
 grip.send(0,10,20);grip.send(2,60,90);grip.send(5,60,90);grip.send(1,60,90);
 check(lp.x==140&&lp.y==260&&grip.clicks==1&&p.saved==1);
 check(grip.accessibility.performAccessibilityAction(grip,4,null));check(lp.x==164&&p.saved==2);
 AccessibilityNodeInfo info=new AccessibilityNodeInfo();grip.accessibility.onInitializeAccessibilityNodeInfo(grip,info);check(info.actions.size()==4);
 // The collapsed shortcut uses the same free XY policy as the panel/pill.
 View shortcut=new View();WindowManager.LayoutParams sp=new WindowManager.LayoutParams();p.draggable(shortcut,shortcut,sp,"handle",false);
 shortcut.send(0,100,100);shortcut.send(2,170,180);shortcut.send(1,170,180);
 check(sp.x==170&&sp.y==280&&p.opens==0&&shortcut.clicks==0&&p.saved==3);
 shortcut.send(0,170,180);shortcut.send(1,170,180);check(shortcut.clicks==1&&p.saved==3);
 shortcut.send(0,170,180);shortcut.send(2,240,260);shortcut.send(3,240,260);check(sp.x==170&&sp.y==280&&p.saved==3);
 p.saved=2;
 View edge=new View();WindowManager.LayoutParams ep=new WindowManager.LayoutParams();p.draggable(edge,edge,ep,"handle",true);
 edge.send(0,100,100);edge.send(2,70,100);edge.send(1,70,100);check(p.opens==1&&edge.clicks==0&&p.saved==2);
 edge.send(0,100,100);edge.send(2,91,100);edge.send(2,85,100);edge.send(1,85,100);check(p.opens==2);
 edge.send(0,100,100);edge.send(2,140,100);edge.send(1,140,100);check(p.opens==2&&edge.clicks==0);
 edge.send(0,100,100);edge.send(2,101,160);edge.send(1,101,160);check(ep.x==100&&ep.y==260&&p.saved==3);
 p.onLeft=true;edge.send(0,100,100);edge.send(2,140,100);edge.send(1,140,100);check(p.opens==3);
 info=new AccessibilityNodeInfo();edge.accessibility.onInitializeAccessibilityNodeInfo(edge,info);check(info.actions.size()==2);
 check(!edge.accessibility.performAccessibilityAction(edge,3,null));
 System.out.println("Actual game drag listener: independent grip, slop, cancellation, edge-open and accessible movement PASS");
}
}'''
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'MovementProbe.java').write_text(probe)
            bundle = root / 'android/os/Bundle.java'
            bundle.parent.mkdir(parents=True)
            bundle.write_text('package android.os;public class Bundle {}')
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', folder,
                            str(root / 'MovementProbe.java'), str(bundle)], check=True)
            subprocess.run(['java', '-cp', folder, 'MovementProbe'], check=True)

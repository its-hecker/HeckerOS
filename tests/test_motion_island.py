#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise production lifetime policy and the actual reflective Oslo glow observer."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def java_run(sources, production, main):
    with tempfile.TemporaryDirectory(prefix='hecker-motion-island-') as temporary:
        root = Path(temporary)
        files = []
        for name, code in sources.items():
            file = root / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(code)
            files.append(str(file))
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root / 'classes'),
                        *files, *map(str, production)], check=True)
        subprocess.run(['java', '-cp', str(root / 'classes'), main], check=True)


class MotionIslandTests(unittest.TestCase):
    def test_real_policy_rejects_stale_wrong_user_and_hidden_replays(self):
        java_run({'com/android/systemui/hecker/PolicyTest.java': r'''
package com.android.systemui.hecker;
public class PolicyTest {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  MotionIslandState s=new MotionIslandState();
  s.environment(true,true,true,false,0);
  check(!s.visible(10000));
  check(!s.accept(1,10,10000,10000));
  check(!s.accept(12,0,10000,10000));
  check(!s.accept(1,0,10001,10000));
  check(!s.accept(1,0,8000,10000));
  check(s.accept(1,0,10000,10000));
  check(!s.visible(10000));
  s.environment(true,true,true,true,0);
  check(s.visible(10001)&&s.creatureVisible(10001));
  long first=s.sequence;
  check(s.update(first,true,45)&&s.confirmed&&s.volume==45);
  check(!s.accept(1,0,10000,10001));
  check(s.accept(2,0,10500,10500));
  check(!s.update(first,true,99));
  s.environment(true,true,true,false,0);
  check(s.visible(11000)&&s.creatureVisible(11000)); // Pause finishes its finite goodbye.
  check(!s.visible(12900)&&!s.creatureVisible(12900));
  s.clear();
  s.environment(true,true,true,true,0);
  check(s.visible(13000)&&!s.creatureVisible(13000)); // Music persists after glow/gesture ends.
  s.environment(true,false,true,true,0);
  check(s.visible(13000)&&!s.creatureVisible(13000));
  s.environment(false,true,true,true,0);
  check(!s.visible(14000)&&!s.accept(4,0,14000,14000));
  s.environment(true,true,true,true,0);
  check(s.accept(3,0,16000,16000));
  s.environment(true,true,false,true,0);
  check(!s.visible(16001)&&s.current(16001)==0);
  s.environment(true,true,true,true,10);
  check(!s.accept(3,0,17000,17000)&&s.accept(3,10,17000,17000));
  check(MotionIslandAnimation.alpha(-1)==0&&MotionIslandAnimation.alpha(0)==0
        &&MotionIslandAnimation.alpha(2400)==0);
  check(MotionIslandAnimation.alpha(600)==1&&MotionIslandAnimation.alpha(2300)<.25f);
  check(MotionIslandAnimation.angle(320,1)<0&&MotionIslandAnimation.angle(320,-1)>0);
  check(MotionIslandAnimation.angle(320,0)==0);
  check(MotionIslandAnimation.pose(360,true)!=MotionIslandAnimation.pose(540,true));
  check(MotionIslandAnimation.pose(360,false)==0&&MotionIslandAnimation.pose(1800,true)==0);
  check(MotionIslandAnimation.swingPose(-1)==0&&MotionIslandAnimation.swingPose(0)==0
        &&MotionIslandAnimation.swingPose(1700)==0&&MotionIslandAnimation.swingPose(2400)==0);
  java.util.Set<Integer> armPoses=new java.util.HashSet<>();
  for(int age=0;age<1200;age+=50)armPoses.add(MotionIslandAnimation.swingPose(age));
  check(armPoses.equals(java.util.Set.of(0,1,2))); // Arm participates in the flick, not just play/pause.
  for(int age=0;age<=2400;age+=16){
   float right=MotionIslandAnimation.bodyRock(age,1),left=MotionIslandAnimation.bodyRock(age,-1);
   if(!Float.isFinite(right)||Math.abs(right)>6||Math.abs(right+left)>.001f)
       throw new AssertionError("invalid or asymmetric balance at "+age);
  }
  check(MotionIslandAnimation.bodyRock(200,1)!=0&&MotionIslandAnimation.bodyRock(200,0)==0);
  check(MotionIslandAnimation.bodyRock(-1,1)==0&&MotionIslandAnimation.bodyRock(2400,1)==0);
  check(MotionIslandState.volumePercent(5,11)==45);
  check(MotionIslandState.volumePercent(0,11)==0);
  check(MotionIslandState.volumePercent(11,11)==100);
  check(MotionIslandState.volumePercent(1,0)==-1);
  check(MotionIslandState.volumePercent(-1,11)==-1);
  check(MotionIslandState.volumePercent(12,11)==-1);
  System.out.println("Motion Island policy: "+checks+" checks PASS");
 }
}'''}, [ROOT/'framework/motionisland/MotionIslandState.java', ROOT/'framework/motionisland/MotionIslandAnimation.java'],
                 'com.android.systemui.hecker.PolicyTest')

    def test_glow_observer_preserves_callbacks_and_follows_shader_fade(self):
        sources = {
            'android/util/Log.java': 'package android.util;public class Log{public static int w(String a,String b,Throwable c){return 0;}}',
            'android/view/ViewTreeObserver.java': r'''package android.view;
public class ViewTreeObserver{
 public interface OnPreDrawListener{boolean onPreDraw();}
 public final java.util.List<OnPreDrawListener> listeners=new java.util.ArrayList<>();
 public void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}
 public void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
 public boolean isAlive(){return true;}
 public void draw(){for(var l:new java.util.ArrayList<>(listeners))l.onPreDraw();}
}''',
            'android/view/View.java': r'''package android.view;
public class View{
 public boolean attached=true,shown=true;public final ViewTreeObserver tree=new ViewTreeObserver();
 private static final java.util.ArrayDeque<Runnable> posted=new java.util.ArrayDeque<>();
 public ViewTreeObserver getViewTreeObserver(){return tree;}
 public boolean isAttachedToWindow(){return attached;}public boolean isShown(){return shown;}
 public boolean post(Runnable r){posted.add(r);return true;}
 public static void drain(){while(!posted.isEmpty())posted.remove().run();}
}''',
            'com/google/oslo/ui/OsloFeedbackView.java': r'''package com.google.oslo.ui;
public class OsloFeedbackView extends android.view.View{
 public interface Callback{void onAnimationStart();void onAnimationEnd();}
 public int starts,ends;
 private Callback mCallback=new Callback(){public void onAnimationStart(){starts++;}public void onAnimationEnd(){ends++;}};
 public void setCallback(Callback callback){mCallback=callback;}
 public void start(){mCallback.onAnimationStart();}public void end(){mCallback.onAnimationEnd();}
}''',
            'com/google/oslo/ui/glow/GlowFeedbackView.java': r'''package com.google.oslo.ui.glow;
public class GlowFeedbackView extends com.google.oslo.ui.OsloFeedbackView{
 public static class ShaderGlow{public boolean active,animating;public boolean isActive(){return active;}public boolean isAnimating(){return animating;}}
 public final ShaderGlow mGlow=new ShaderGlow();
 private final android.view.View mShaderView=new android.view.View();
 public void surface(boolean shown){mShaderView.shown=shown;tree.draw();}
}''',
            'com/android/systemui/hecker/VisibilityTest.java': r'''package com.android.systemui.hecker;
import com.google.oslo.ui.glow.GlowFeedbackView;import android.view.View;
public class VisibilityTest{
 static int checks;static void check(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  GlowFeedbackView f=new GlowFeedbackView();int[] changed={0};
  OsloGlowVisibility observer=OsloGlowVisibility.bind(f,()->changed[0]++);
  check(observer!=null&&!observer.visible());
  f.start();f.mGlow.active=true;View.drain();
  check(f.starts==1&&changed[0]==1&&observer.visible());
  f.surface(false);check(!observer.visible()&&changed[0]==2);
  f.surface(true);f.mGlow.active=false;f.mGlow.animating=true;
  check(observer.visible()); // Keep capsule for the real shader's fade-out.
  f.end();f.mGlow.animating=false;View.drain();
  check(!observer.visible()&&f.ends==1&&changed[0]==4);
  f.mGlow.active=true;f.shown=false;check(!observer.visible());
  f.shown=true;f.attached=false;check(!observer.visible());f.attached=true;
  check(observer.belongsTo(f)&&!observer.belongsTo(new View()));
  observer.close();int before=changed[0];f.start();f.tree.draw();View.drain();
  check(f.starts==2&&changed[0]==before&&!observer.visible()&&f.tree.listeners.isEmpty());
  observer.close();
  check(OsloGlowVisibility.bind(new View(),()->{})==null);
  System.out.println("Real glow observer: "+checks+" lifecycle checks PASS");
 }
}''',
        }
        java_run(sources, [ROOT/'framework/motionisland/OsloGlowVisibility.java'],
                 'com.android.systemui.hecker.VisibilityTest')


if __name__ == '__main__': unittest.main()

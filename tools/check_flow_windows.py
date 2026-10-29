#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile and exercise the production Shell animation adapter using narrow host API facades."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[1]
sources={
"android/app/WindowConfiguration.java":"""package android.app;public class WindowConfiguration{public static final int WINDOWING_MODE_FULLSCREEN=1,ACTIVITY_TYPE_STANDARD=1,ACTIVITY_TYPE_HOME=2;}""",
"android/content/Context.java":"""package android.content;public class Context{public <T>T getSystemService(Class<T> c){return c.cast(new android.os.PowerManager());}public Object getContentResolver(){return this;}}""",
"android/os/PowerManager.java":"""package android.os;public class PowerManager{public static boolean save;public boolean isPowerSaveMode(){return save;}}""",
"android/os/UserHandle.java":"package android.os;public class UserHandle{public static final int USER_CURRENT=-2;}",
"android/provider/Settings.java":"""package android.provider;public class Settings{public static class Secure{public static int flow=1;public static int getIntForUser(Object c,String k,int d,int u){return flow;}}}""",
"android/view/WindowManager.java":"package android.view;public interface WindowManager{int TRANSIT_OPEN=1,TRANSIT_CLOSE=2,TRANSIT_TO_FRONT=3,TRANSIT_TO_BACK=4;}",
"android/view/animation/Interpolator.java":"package android.view.animation;public interface Interpolator{float getInterpolation(float t);}",
"android/view/animation/Animation.java":"""package android.view.animation;public class Animation{public static final int RELATIVE_TO_SELF=1;public long duration;public Interpolator curve;public void setDuration(long d){duration=d;}public void setInterpolator(Interpolator i){curve=i;}public void setFillAfter(boolean b){}}""",
"android/view/animation/AnimationSet.java":"""package android.view.animation;public class AnimationSet extends Animation{public java.util.List<Animation> children=new java.util.ArrayList<>();public java.util.List<Animation> getAnimations(){return children;}public AnimationSet(boolean b){}public void addAnimation(Animation a){children.add(a);}}""",
"android/view/animation/AlphaAnimation.java":"""package android.view.animation;public class AlphaAnimation extends Animation{public float from,to;public AlphaAnimation(float f,float t){from=f;to=t;}}""",
"android/view/animation/ScaleAnimation.java":"""package android.view.animation;public class ScaleAnimation extends Animation{public float from,to;public ScaleAnimation(float f,float t,float a,float b,int c,float d,int e,float g){from=f;to=t;}}""",
"android/view/animation/TranslateAnimation.java":"""package android.view.animation;public class TranslateAnimation extends Animation{public TranslateAnimation(int a,float b,int c,float d,int e,float f,int g,float h){}}""",
"android/window/TransitionInfo.java":"""package android.window;public class TransitionInfo{public static class Task{public int mode=1,activity=1;public int getWindowingMode(){return mode;}public int getActivityType(){return activity;}}public static class Change{public Task task=new Task();public Task getTaskInfo(){return task;}}}""",
"WindowProbe.java":"""import com.android.wm.shell.transition.HeckerFlowWindowAnimation;import android.view.animation.*;import android.window.TransitionInfo;
public class WindowProbe{
static android.content.Context context=new android.content.Context();static TransitionInfo.Change change=new TransitionInfo.Change();
static Animation create(int t,boolean enter,boolean opening){return HeckerFlowWindowAnimation.create(context,t,change,enter,opening);}
static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[]args){
 for(int type=1;type<=4;type++)for(boolean enter:new boolean[]{true,false})for(boolean opening:new boolean[]{true,false}){
  AnimationSet set=(AnimationSet)create(type,enter,opening);check(set.children.size()==3);
  ScaleAnimation scale=(ScaleAnimation)set.children.get(0);check(enter?scale.to==1:scale.from==1);
  AlphaAnimation alpha=(AlphaAnimation)set.children.get(2);check(alpha.to>=0&&alpha.to<=1);
  float last=-1;for(int i=0;i<=1000;i++){float p=scale.curve.getInterpolation(i/1000f);check(p>=last&&p>=0&&p<=1);last=p;}
  check(scale.duration==(enter?460:330));check(alpha.duration==(enter?160:330));
 }
 AnimationSet clip=new AnimationSet(false);AlphaAnimation child=new AlphaAnimation(0,1);child.setDuration(80);clip.addAnimation(child);
 HeckerFlowWindowAnimation.tuneLaunch(context,1,change,clip);check(child.duration==80&&child.curve.getInterpolation(.5f)>.8f);
 change.task.activity=2;check(((AnimationSet)create(1,true,true)).children.size()==2);
 change.task.activity=3;check(create(1,true,true)==null);change.task.activity=1;
 change.task.mode=5;check(create(1,true,true)==null);change.task.mode=1;
 change.task=null;check(create(1,true,true)==null);change.task=new TransitionInfo.Task();
 check(create(99,true,true)==null);
 android.os.PowerManager.save=true;check(create(1,true,true)==null);android.os.PowerManager.save=false;
 android.provider.Settings.Secure.flow=0;check(create(1,true,true)==null);
 System.out.println("Production Shell motion: endpoints, monotonic frame curves, independent alpha timing, Home, fullscreen/type gates, Battery Saver and opt-out PASS");
}}"""
}
with tempfile.TemporaryDirectory(prefix="hecker-window-motion-") as temporary:
    work=Path(temporary)
    for name,text in sources.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
    subprocess.run(["java","-m","jdk.compiler/com.sun.tools.javac.Main","-d",str(work/"classes"),
        *map(str,work.rglob("*.java")),str(ROOT/"framework/motion/HeckerFlowWindowAnimation.java")],check=True)
    subprocess.run(["java","-cp",str(work/"classes"),"WindowProbe"],check=True)

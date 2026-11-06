# SPDX-License-Identifier: Apache-2.0
"""Check the production dock curve against an in-range oscillating native curve."""
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[1]

class DockMotionTests(unittest.TestCase):
    def test_disabled_bounce_is_monotonic_and_reads_the_current_preference(self):
        sources = {
            'android/content/Context.java': 'package android.content; public class Context { public <T>T getSystemService(Class<T> c){return null;} public Object getContentResolver(){return null;} }',
            'android/animation/ValueAnimator.java': 'package android.animation; public class ValueAnimator {public static boolean areAnimatorsEnabled(){return true;}}',
            'android/os/PowerManager.java': 'package android.os; public class PowerManager {public boolean isPowerSaveMode(){return false;}}',
            'android/provider/Settings.java': 'package android.provider; public class Settings {public static class Secure {public static int getInt(Object r,String n,int d){return d;}}}',
            'android/view/animation/Interpolator.java': 'package android.view.animation; public interface Interpolator {float getInterpolation(float t);}',
            'org/hecker/motion/FlowClockRenderer.java': 'package org.hecker.motion; public class FlowClockRenderer {public static float settle(float t){return t;}}',
            'com/android/launcher3/hecker/HeckerStudio.java': 'package com.android.launcher3.hecker; public class HeckerStudio {public static boolean bounce=false; public static boolean enabled(android.content.Context c,String key,boolean d){if(!key.equals("dock_bounce"))throw new AssertionError();return bounce;}}',
            'DockProbe.java': """import com.android.launcher3.hecker.*;
import android.content.Context;
import android.view.animation.Interpolator;
public class DockProbe {
 public static void main(String[] args){
  Context c=new Context();
  Interpolator bounce=t->Math.max(0f,Math.min(1f,t+.18f*(float)Math.sin(t*20)));
  for(int restart=0;restart<3;restart++) {
   HeckerStudio.bounce=false;
   Interpolator curve=HeckerLauncherMotion.dockInterpolator(new Context(),bounce);
   float previous=-1;
   for(int i=0;i<=1000;i++){float v=curve.getInterpolation(i/1000f);if(v<previous||v<0||v>1)throw new AssertionError("Dock reversed while disabled");previous=v;}
   if(curve.getInterpolation(0)!=0||curve.getInterpolation(1)!=1)throw new AssertionError();
   HeckerStudio.bounce=true;
   if(HeckerLauncherMotion.dockInterpolator(c,bounce)!=bounce)throw new AssertionError();
  }
 }
} """,
        }
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp)
            files=[]
            for name,text in sources.items():
                file=folder/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text);files.append(file)
            files.append(ROOT/'aosp-launcher/src/com/android/launcher3/hecker/HeckerLauncherMotion.java')
            subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',temp,*map(str,files)],check=True)
            subprocess.run(['java','-cp',temp,'DockProbe'],check=True)

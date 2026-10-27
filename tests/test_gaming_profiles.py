# SPDX-License-Identifier: Apache-2.0
"""Exercise the production profile adapter against native and custom-mode responses."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class GameProfileTests(unittest.TestCase):
    def test_actual_profile_methods_read_back_interventions_and_respect_opt_out(self):
        source = (ROOT / 'app/src/org/hecker/suite/GamingPlatform.java').read_text()
        methods = source[source.index('    private static int nativeMode('):source.index('    static boolean ownDisplayTask(')]
        files = {
            'android/content/Context.java': '''package android.content;
public class Context { public Object game, display;
 public Object getSystemService(String name){return game;}
 public <T> T getSystemService(Class<T> type){return type.cast(display);} }''',
            'android/view/Display.java': '''package android.view;
public class Display { public Mode[] getSupportedModes(){return new Mode[]{new Mode(60),new Mode(90)};}
 public static class Mode { final float fps;public Mode(float fps){this.fps=fps;}public float getRefreshRate(){return fps;} } }''',
            'android/hardware/display/DisplayManager.java': '''package android.hardware.display;
public class DisplayManager {public android.view.Display getDisplay(int id){return new android.view.Display();}}''',
            'android/app/GameModeConfiguration.java': '''package android.app;
public class GameModeConfiguration { final int fps;final float scale;
 GameModeConfiguration(int fps,float scale){this.fps=fps;this.scale=scale;}
 public int getFpsOverride(){return fps;}public float getScalingFactor(){return scale;}
 public static class Builder {int fps;float scale=1;
 public Builder setFpsOverride(int fps){this.fps=fps;return this;}
 public Builder setScalingFactor(float scale){this.scale=scale;return this;}
 public GameModeConfiguration build(){return new GameModeConfiguration(fps,scale);} } }''',
            'org/hecker/suite/GamingPrefs.java': '''package org.hecker.suite;
public class GamingPrefs {static final Prefs prefs=new Prefs();static Prefs get(android.content.Context c){return prefs;}
 public static class Prefs {java.util.Map<String,Integer> saved=new java.util.HashMap<>();
 public int getInt(String key,int fallback){return saved.getOrDefault(key,fallback);}
 public Prefs edit(){return this;} public Prefs putInt(String key,int value){saved.put(key,value);return this;}public void apply(){} } }''',
            'org/hecker/suite/ProfileProbe.java': '''package org.hecker.suite;
import android.content.Context;import android.hardware.display.DisplayManager;
final class ProfileAdapter {
''' + methods + '''
}
public class ProfileProbe {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static class Info {public boolean fpsAllowed=true,scaleAllowed=true;public android.app.GameModeConfiguration config;
 public boolean isFpsOverrideAllowed(){return fpsAllowed;}public boolean isDownscalingAllowed(){return scaleAllowed;}
 public android.app.GameModeConfiguration getGameModeConfiguration(int mode){check(mode==4);return config;}}
 public static class Manager {public int mode=1,customUpdates;public int[] available={1,4};public boolean ignoreSet;public Info info=new Info();
 public int getGameMode(String pkg){return mode;}public int[] getAvailableGameModes(String pkg){return available;}
 public Info getGameModeInfo(String pkg){return info;}
 public void updateCustomGameModeConfiguration(String pkg,android.app.GameModeConfiguration config){customUpdates++;info.config=config;}
 public void setGameMode(String pkg,int next){if(!ignoreSet)mode=next;}}
 public static void main(String[] args){
  Context c=new Context();Manager manager=new Manager();c.game=manager;c.display=new DisplayManager();String pkg="asphalt";
  check(ProfileAdapter.modeSupported(c,pkg,2));check(ProfileAdapter.gameMode(c,pkg,2));
  check(manager.mode==4&&manager.info.config.getFpsOverride()==90&&manager.info.config.getScalingFactor()==1);
  check(ProfileAdapter.gameMode(c,pkg)==2);
  check(ProfileAdapter.gameMode(c,pkg,3));check(manager.info.config.getFpsOverride()==30&&manager.info.config.getScalingFactor()==.8f);
  check(ProfileAdapter.gameMode(c,pkg)==3);
  manager.info.config=new android.app.GameModeConfiguration.Builder().setFpsOverride(60).build();
  check(ProfileAdapter.gameMode(c,pkg)==4); // external custom changes must not masquerade as a saved profile
  check(ProfileAdapter.gameMode(c,pkg,1)&&manager.mode==1);
  manager.info.scaleAllowed=false;check(ProfileAdapter.gameMode(c,pkg,3));check(manager.info.config.getScalingFactor()==1);
  manager.mode=1;manager.info.fpsAllowed=false;
  check(!ProfileAdapter.modeSupported(c,pkg,2));check(!ProfileAdapter.gameMode(c,pkg,2)&&manager.mode==1);
  manager.info.fpsAllowed=true;manager.ignoreSet=true;check(!ProfileAdapter.gameMode(c,pkg,2));
  manager.ignoreSet=false;manager.available=new int[]{1,2,3,4};int previous=manager.customUpdates;
  check(ProfileAdapter.gameMode(c,pkg,2)&&manager.mode==2&&manager.customUpdates==previous);
  manager.available=new int[]{};check(!ProfileAdapter.modeSupported(c,pkg,2));
  System.out.println("Production game profiles: native modes, real custom FPS/scale, opt-out, external edits and failed readback PASS");
 }
}
''',
        }
        with tempfile.TemporaryDirectory(prefix='hecker-profiles-') as tmp:
            root = Path(tmp)
            sources = []
            for name, content in files.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                sources.append(str(target))
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root / 'classes'), *sources], check=True)
            subprocess.run(['java', '-cp', str(root / 'classes'), 'org.hecker.suite.ProfileProbe'], check=True)

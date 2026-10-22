#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the native gaming service and its real AIDL using the locked SDK.

Hidden declarations are checked against the pinned framework by check_native_ports.
This is not a complete services, SystemUI or kernel build.
"""
import argparse, os, subprocess, tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
FACADES={
'android/app/TaskInfo.java':'''package android.app;
public class TaskInfo {public int taskId,displayId,userId;public boolean isFocused;public boolean isVisible(){return false;}}''',
'android/provider/Settings.java':'''package android.provider;
public final class Settings {
 public static class Global {public static boolean putInt(android.content.ContentResolver r,String key,int value){return true;}}
 public static class Secure {
  public static int getIntForUser(android.content.ContentResolver r,String key,int def,int user){return def;}
  public static boolean putIntForUser(android.content.ContentResolver r,String key,int value,int user){return true;}
 }
 public static class System {
  public static final String SCREEN_BRIGHTNESS_MODE="screen_brightness_mode";
  public static final int SCREEN_BRIGHTNESS_MODE_MANUAL=0;
  public static int getIntForUser(android.content.ContentResolver r,String key,int def,int user){return def;}
  public static boolean putIntForUser(android.content.ContentResolver r,String key,int value,int user){return true;}
 }
}''',

'com/android/server/SystemService.java':'''package com.android.server;
public abstract class SystemService {
 public static final int PHASE_SYSTEM_SERVICES_READY=500;
 public SystemService(android.content.Context c){} public android.content.Context getContext(){return null;}
 public abstract void onStart(); public void onBootPhase(int phase){}
 protected void publishBinderService(String name,android.os.IBinder binder){}
}''',
'android/os/ServiceManager.java':'''package android.os;
public final class ServiceManager {public static IBinder getService(String name){return null;}}''',
'android/app/StatusBarManager.java':'''package android.app;
public final class StatusBarManager {public static final int DISABLE_HOME=0x200000,DISABLE_RECENT=0x1000000,DISABLE_BACK=0x400000,DISABLE_SEARCH=0x2000000;}''',
'com/android/internal/statusbar/IStatusBarService.java':'''package com.android.internal.statusbar;
public interface IStatusBarService {
 void disableForUser(int what,android.os.IBinder token,String pkg,int userId) throws android.os.RemoteException;
 public static abstract class Stub {public static IStatusBarService asInterface(android.os.IBinder binder){return null;}}
}''',
}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk-cache',type=Path,required=True);sdk=p.parse_args().sdk_cache.resolve()
 def run(*args,**kw):subprocess.run(list(map(str,args)),check=True,**kw)
 with tempfile.TemporaryDirectory(prefix='hecker-gaming-') as tmp:
  out=Path(tmp)
  for name,source in FACADES.items():
   f=out/'facades'/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(source)
  aidl=out/'aidl/android/os';aidl.mkdir(parents=True)
  (aidl/'Bundle.aidl').write_text('package android.os; parcelable Bundle;')
  (aidl/'IHeckerGameService.aidl').write_text((ROOT/'framework/gaming/IHeckerGameService.aidl').read_text())
  env=os.environ.copy();env['LD_LIBRARY_PATH']=str(sdk/'lib64')
  run(sdk/'aidl','--lang=java','--out='+str(out/'generated'),'-I',out/'aidl',aidl/'IHeckerGameService.aidl',env=env)
  run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',sdk/'android-system.jar','-d',out/'classes',
      *sorted((out/'facades').rglob('*.java')),*sorted((out/'generated').rglob('*.java')),*sorted((ROOT/'framework/gaming').glob('*.java')))
 print('Native gaming Java and real generated Binder interface: PASS (full ROM/device checks pending)')
if __name__=='__main__':main()

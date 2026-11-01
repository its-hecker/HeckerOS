#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the real sensor broker/AIDL against locked SDK inputs and reviewed hidden declarations."""
import argparse,os,subprocess,tempfile,json,hashlib
from compile_sdk import fetch
from pathlib import Path
from check_gaming import FACADES
ROOT=Path(__file__).resolve().parents[1]
FACADES={**FACADES,
 'android/os/SystemProperties.java':'package android.os;public class SystemProperties{public static String get(String k,String d){return d;}public static void set(String k,String v){}}',
 'android/app/ActivityManager.java':'package android.app;public class ActivityManager{public static int getCurrentUser(){return 0;}}',
 'android/os/ShellCallback.java':'package android.os;public class ShellCallback{}',
 'android/os/ShellCommand.java':'''package android.os;public abstract class ShellCommand {
 public abstract int onCommand(String command);public abstract void onHelp();
 public int handleDefaultCommands(String command){return 0;}
 public java.io.PrintWriter getOutPrintWriter(){return null;}
 public int exec(Binder binder,java.io.FileDescriptor in,java.io.FileDescriptor out,java.io.FileDescriptor err,String[] args,ShellCallback callback,ResultReceiver result){return 0;}
}'''}
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sdk-cache',type=Path,required=True)
 parser.add_argument('--platform-source',type=Path)
 args=parser.parse_args();sdk=args.sdk_cache.resolve()
 if not args.platform_source:
  args.platform_source=ROOT/"generated/sensor-broker-source";args.platform_source.mkdir(parents=True,exist_ok=True)
  for name,item in json.loads((ROOT/"tests/sensor-broker-inputs.lock.json").read_text()).items():fetch(args.platform_source,name,item)
 if args.platform_source:
  for name,item in json.loads((ROOT/'tests/sensor-broker-inputs.lock.json').read_text()).items():
   assert hashlib.sha256((args.platform_source/name).read_bytes()).hexdigest()==item['sha256'],name
  binder=(args.platform_source/'Binder.java').read_text();shell=(args.platform_source/'ShellCommand.java').read_text()
  assert 'void onShellCommand(' in binder and 'ShellCallback callback' in binder and 'ResultReceiver resultReceiver' in binder
  assert 'int exec(' in shell and 'ShellCallback callback' in shell and 'ResultReceiver resultReceiver' in shell
 def run(*cmd,**kw):subprocess.run(list(map(str,cmd)),check=True,**kw)
 with tempfile.TemporaryDirectory(prefix='hecker-sensor-broker-') as temporary:
  out=Path(temporary)
  for name,value in FACADES.items():
   p=out/'facades'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value)
  aidl=out/'aidl/android/os';aidl.mkdir(parents=True)
  (aidl/'Bundle.aidl').write_text('package android.os; parcelable Bundle;')
  (aidl/'IHeckerSensorRecoveryService.aidl').write_bytes((ROOT/'framework/sensors/IHeckerSensorRecoveryService.aidl').read_bytes())
  env=dict(os.environ,LD_LIBRARY_PATH=str(sdk/'lib64'))
  run(sdk/'aidl','--lang=java','--out='+str(out/'generated'),'-I',out/'aidl',aidl/'IHeckerSensorRecoveryService.aidl',env=env)
  run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',sdk/'android-system.jar','-d',out/'classes',
   *sorted((out/'facades').rglob('*.java')),*sorted((out/'generated').rglob('*.java')),*sorted((ROOT/'framework/sensors').glob('*.java')))
 print('Root-free native sensor broker and real generated AIDL: PASS; full Soong/SELinux and phone checks pending.')
if __name__=='__main__':main()

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile native memory/launcher/volume/status-bar adapters and actual Binder interfaces.

Hidden declarations are facades reviewed against the immutable Android 17 inputs.
This is not a services/SystemUI Soong build or a phone test.
"""
import argparse,json,os,re,shutil,subprocess,tempfile,urllib.request
from pathlib import Path
from check_quick_settings import JAVA
from check_gaming import FACADES as GAME
ROOT=Path(__file__).resolve().parents[1]
FACADES={**JAVA,**GAME}
FACADES.update({
'android/os/ServiceManager.java':'''package android.os; public final class ServiceManager { public static IBinder getService(String n){return null;} public static void addService(String n,IBinder b){} }''',
'android/app/ActivityManager.java':'''package android.app; public class ActivityManager {
 public static final int PROCESS_STATE_FOREGROUND_SERVICE=4,PROCESS_STATE_HOME=14;
 public static int getCurrentUser(){return 0;} public java.util.List<RunningTaskInfo> getRunningTasks(int n){return null;}
 public static class RunningTaskInfo {public int taskId,userId,displayId;public boolean isFocused;public android.content.ComponentName topActivity;public boolean isVisible(){return true;}}
}''',
'android/app/ActivityManagerInternal.java':'''package android.app; public class ActivityManagerInternal {public static final int OOM_ADJ_REASON_RESTRICTION_CHANGE=1;}''',
'android/os/PowerManager.java':'''package android.os; public class PowerManager {public void goToSleep(long time){}public boolean isInteractive(){return true;}}''',
'android/os/UserManager.java':'''package android.os; public class UserManager {public java.util.List<UserHandle> getUserHandles(boolean exclude){return null;}}''',
'android/os/UserHandle.java':'''package android.os; public final class UserHandle implements Parcelable {
 public static final int USER_ALL=-1,USER_CURRENT=-2;public static final UserHandle ALL=null,SYSTEM=null;
 public static UserHandle of(int user){return null;} public static int getUserId(int uid){return 0;}public static int getAppId(int uid){return uid;}public static boolean isApp(int uid){return true;}
 public int getIdentifier(){return 0;}public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){}
}''',
'android/media/AudioSystem.java':'''package android.media;public class AudioSystem {public static String streamToString(int s){return "";}}''',
'com/android/systemui/plugins/Plugin.java':'''package com.android.systemui.plugins;public interface Plugin {}''',
'androidx/annotation/StringRes.java':'''package androidx.annotation;public @interface StringRes {}''',
'com/android/systemui/plugins/annotations/ProvidesInterface.java':'''package com.android.systemui.plugins.annotations;public @interface ProvidesInterface {int version();String action() default "";}''',
'com/android/systemui/plugins/annotations/DependsOn.java':'''package com.android.systemui.plugins.annotations;@java.lang.annotation.Repeatable(Dependencies.class) public @interface DependsOn {Class<?> target();}''',
'com/android/systemui/plugins/annotations/Dependencies.java':'''package com.android.systemui.plugins.annotations;public @interface Dependencies {DependsOn[] value();}''',
'com/android/systemui/statusbar/phone/SystemUIDialog.java':'''package com.android.systemui.statusbar.phone;public class SystemUIDialog extends android.app.AlertDialog {public SystemUIDialog(android.content.Context c){super(c);}public void setShowForAllUsers(boolean b){}}''',
'com/android/systemui/volume/SafetyWarningDialogDelegate.java':'''package com.android.systemui.volume;public class SafetyWarningDialogDelegate {
 public interface Cleanup{void cleanup();}public interface Factory{SafetyWarningDialogDelegate create(Cleanup c);}public com.android.systemui.statusbar.phone.SystemUIDialog createDialog(){return null;}}
''',
'com/android/systemui/volume/CsdWarningAction.java':'''package com.android.systemui.volume;public class CsdWarningAction {}''',
'com/android/systemui/volume/CsdWarningDialogDelegate.java':'''package com.android.systemui.volume;public class CsdWarningDialogDelegate {
 public interface Factory{CsdWarningDialogDelegate create(int warning,Runnable cleanup,java.util.Optional<java.util.List<CsdWarningAction>> intents);}
 public com.android.systemui.statusbar.phone.SystemUIDialog createDialog(){return null;}public void maybeShow(com.android.systemui.statusbar.phone.SystemUIDialog d){} public void destroy(){}
}''',
'com/android/systemui/hecker/HeckerQsActions.java':'''package com.android.systemui.hecker;public class HeckerQsActions{public static void settings(String action){}}''',
'com/android/server/am/psc/Constants.java':'''package com.android.server.am.psc;public class Constants {public static final int CACHED_APP_MIN_ADJ=900,PERCEPTIBLE_LOW_APP_ADJ=250,SERVICE_ADJ=500;}''',
'com/android/server/am/ActivityManagerService.java':'''package com.android.server.am;public class ActivityManagerService {
 public android.content.Context mContext;public final Object mGlobalLock=new Object(),mProcLock=new Object();public ProcessList mProcessList;public Controller mProcessStateController;
 void updateOomAdjLocked(int reason){}public static class Controller {public void setMaxAdj(ProcessRecord p,int adj){}}
}''',
'com/android/server/am/ProcessList.java':'''package com.android.server.am;public class ProcessList {java.util.ArrayList<ProcessRecord> getLruProcessesLOSP(){return null;}}''',
'com/android/server/am/ProcessRecord.java':'''package com.android.server.am;public class ProcessRecord extends com.android.server.am.psc.ProcessRecordInternal {
 public android.content.pm.ApplicationInfo info;public int uid;
 public void killLocked(String reason,String description,int reasonCode,int subReason,android.app.ApplicationExitInfo.AnrInfo info,boolean noisy,boolean async){}
}''',
'com/android/server/am/HostingRecord.java':'''package com.android.server.am;public class HostingRecord {
 public static final String HOSTING_TYPE_ACTIVITY="activity",HOSTING_TYPE_NEXT_ACTIVITY="next-activity",HOSTING_TYPE_TOP_ACTIVITY="top-activity",HOSTING_TYPE_NEXT_TOP_ACTIVITY="next-top-activity";
 public String getType(){return "";}public boolean isTopApp(){return false;}
}'''
})
FACADES.update({
'android/util/Slog.java': 'package android.util;public class Slog{public static int w(String t,String m,Throwable e){return 0;}}',
'android/app/ApplicationExitInfo.java': 'package android.app;public class ApplicationExitInfo{public static final int REASON_USER_REQUESTED=10,SUBREASON_KILL_BACKGROUND=24;public static class AnrInfo{}}',
'android/hardware/display/BrightnessInfo.java': 'package android.hardware.display;public class BrightnessInfo{public float brightnessMinimum,brightnessMaximum;}',
'android/view/Display.java': 'package android.view;public class Display{public static final int DEFAULT_DISPLAY=0;public android.hardware.display.BrightnessInfo getBrightnessInfo(){return null;}}',
'android/hardware/display/DisplayManager.java': 'package android.hardware.display;public class DisplayManager{public android.view.Display getDisplay(int id){return null;}public float getBrightness(int id){return 0;}public void setBrightness(int id,float value){}public void setTemporaryBrightness(int id,float value){}}',
'android/media/AudioManager.java': 'package android.media;public class AudioManager{public @interface CsdWarning{}public static final int STREAM_MUSIC=3,STREAM_RING=2,STREAM_NOTIFICATION=5,STREAM_ALARM=4,STREAM_SYSTEM=1,RINGER_MODE_NORMAL=2,RINGER_MODE_VIBRATE=1,RINGER_MODE_SILENT=0;}'
})
# Keep the broader SystemUI Settings facade; game-only Settings omits all UI APIs.
FACADES['android/provider/Settings.java']=JAVA['android/provider/Settings.java'].replace(' public static final String ACTION_CAST_SETTINGS="",',
' public static final String ACTION_DATE_SETTINGS="",ACTION_WIFI_SETTINGS="",ACTION_DATA_USAGE_SETTINGS="",ACTION_SETTINGS="",ACTION_SOUND_SETTINGS="", ACTION_CAST_SETTINGS="",').replace(' public static final class Secure {',' public static final class Secure { public static final String DEFAULT_INPUT_METHOD="default_input_method";')
FACADES.pop('com/android/systemui/hecker/HeckerStatusBarPopup.java',None)

def process_record_internal_facade(source):
    # Preserve the actual superclass package and method access. A package-private
    # PSC getter is not inherited by ProcessRecord in com.android.server.am.
    methods=[]
    for name in ['getMaxAdj','getCurAdj','getCurProcState','getProcState','getPid','isKilledByAm']:
        matches=list(re.finditer(
            r'^\s*(?:(public|protected|private)\s+)?(?:abstract\s+)?'
            r'(?:@[\w.]+\s+)*(int|boolean)\s+'+name+r'\s*\(\s*\)\s*[;{]',
            source,re.MULTILINE))
        assert len(matches)==1, 'Missing or ambiguous PSC getter declaration: '+name
        access,result=matches[0].groups()
        if name!='getCurProcState':assert access=='public', 'PSC getter is not public: '+name
        methods.append((access+' ' if access else '')+result+' '+name+'(){return '
                       +('false' if result=='boolean' else '0')+';}')
    return 'package com.android.server.am.psc;public class ProcessRecordInternal {\n'+'\n'.join(methods)+'\n}'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sdk-cache',type=Path,required=True)
    parser.add_argument('--source-cache',type=Path,default=ROOT/'out/native-source-cache');args=parser.parse_args();sdk=args.sdk_cache.resolve()
    origin=json.loads((ROOT/'integration/source-revisions.json').read_text())['pixelos_inputs']['frameworks/base']
    def native(path):
        file=args.source_cache/origin['revision']/'frameworks/base'/path
        if not file.exists():
            with urllib.request.urlopen('https://raw.githubusercontent.com/'+origin['repository']+'/'+origin['revision']+'/'+path,timeout=60) as response:data=response.read()
            file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(data)
        return file
    # Verify the declarations that changed in Android 17, rather than silently accepting old facades.
    record=native('services/core/java/com/android/server/am/psc/ProcessRecordInternal.java').read_text()
    FACADES['com/android/server/am/psc/ProcessRecordInternal.java']=process_record_internal_facade(record)
    process=native('services/core/java/com/android/server/am/ProcessRecord.java').read_text()
    assert 'class ProcessRecord extends ProcessRecordInternal' in process
    assert 'String description,' in process and 'boolean asyncKPG)' in process
    plist=native('services/core/java/com/android/server/am/ProcessList.java').read_text()
    assert 'mService.mProcessStateController.setMaxAdj' in plist and 'com.android.server.am.psc.Constants' in plist
    assert '@JvmStatic fun settings(action: String)' in (ROOT/'framework/qs/HeckerQsActions.kt').read_text()
    safety=native('packages/SystemUI/src/com/android/systemui/volume/SafetyWarningDialogDelegate.java').read_text()
    csd=native('packages/SystemUI/src/com/android/systemui/volume/CsdWarningDialogDelegate.java').read_text()
    assert 'create(Cleanup cleanup)' in safety and 'void maybeShow(SystemUIDialog dialog)' in csd
    def run(*cmd,**kw):subprocess.run(list(map(str,cmd)),check=True,**kw)
    with tempfile.TemporaryDirectory(prefix='hecker-interactions-') as temp:
        out=Path(temp);aidl=out/'aidl/android/os';aidl.mkdir(parents=True)
        for name in ['Bundle','ParcelFileDescriptor']:(aidl/(name+'.aidl')).write_text('package android.os; parcelable '+name+';')
        env=os.environ.copy();env['LD_LIBRARY_PATH']=str(sdk/'lib64')
        for folder,name in [('memory','IHeckerMemoryService'),('launcher','IHeckerLauncherService')]:
            source=aidl/(name+'.aidl');source.write_text((ROOT/'framework'/folder/(name+'.aidl')).read_text())
            run(sdk/'aidl','--lang=java','--out='+str(out/'generated'),'-I',out/'aidl',source,env=env)
        for name,source in FACADES.items():
            p=out/'facades'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
        for folder,name in [('values','strings.xml'),('values-zh-rCN','strings-zh.xml')]:
            p=out/'res'/folder/'hecker.xml';p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'framework/qs'/name,p)
        manifest=out/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.android.systemui.res"><application/></manifest>')
        run(sdk/'aapt2','compile','--dir',out/'res','-o',out/'res.zip')
        run(sdk/'aapt2','link','-I',sdk/'android.jar','--manifest',manifest,'--java',out/'generated','-o',out/'res.apk',out/'res.zip')
        interfaces=[native('packages/SystemUI/plugin/src/com/android/systemui/plugins/'+name+'.java') for name in ['VolumeDialog','VolumeDialogController']]
        owned=[p for folder in ['memory','launcher','volume','statusbar'] for p in (ROOT/'framework'/folder).glob('*.java')]
        run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',sdk/'android-system.jar','-d',out/'classes',
            *sorted((out/'facades').rglob('*.java')),*sorted((out/'generated').rglob('*.java')),*interfaces,*owned)
    print('Native interaction adapters, real volume plugin interfaces and generated Binder APIs: PASS')
    print('Full services/SystemUI Soong compilation, Pixel view compatibility and Coral testing remain required.')
if __name__=='__main__':main()

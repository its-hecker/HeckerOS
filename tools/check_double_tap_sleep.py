#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Execute native gesture/settings routes with host service doubles, not a ROM build."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from check_launcher import COMPILER
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]
BASE = 'frameworks/base/packages/SystemUI/'
PHONE = BASE + 'src/com/android/systemui/statusbar/phone/PhoneStatusBarView.java'
KEYGUARD = BASE + 'src/com/android/systemui/keyguard/domain/interactor/KeyguardTouchHandlingInteractor.kt'
TOUCH = BASE + 'src/com/android/systemui/common/ui/view/TouchHandlingView.kt'
QQS = BASE + 'src/com/android/systemui/shade/QQSGestureListener.kt'
SYSTEM_REPOSITORY = BASE + 'customization/src/com/android/systemui/shared/settings/data/repository/SystemSettingsRepository.kt'
USER_REPOSITORY = BASE + 'src/com/android/systemui/util/settings/repository/UserAwareSystemSettingsRepository.kt'
SETTINGS_MODULE = BASE + 'src/com/android/systemui/settings/UserSettingsRepositoryModule.kt'
INPUTS = (SYSTEM_REPOSITORY, USER_REPOSITORY, SETTINGS_MODULE)


def block(source, marker):
    begin = source.index(marker)
    opening = source.index('{', begin)
    depth = 1
    end = opening + 1
    while depth:
        if source[end] == '{': depth += 1
        if source[end] == '}': depth -= 1
        end += 1
    return source[begin:end]


def check_double_tap_sleep(folder, cache):
    xml = ET.parse(ROOT / 'settings/res/xml/hecker_display_settings.xml').getroot()
    a = '{http://schemas.android.com/apk/res/android}'
    switches = [n for n in xml.iter() if n.get(a + 'key') == 'double_tap_sleep_gesture']
    assert len(switches) == 1 and switches[0].tag.endswith('SystemSettingSwitchPreference')
    assert switches[0].get(a + 'defaultValue') == 'false'
    source = (folder / PHONE).read_text()
    dispatch = '    @Override\n    ' + block(source, 'public boolean dispatchTouchEvent(MotionEvent event)')
    assert source.count('public boolean dispatchTouchEvent(MotionEvent event)') == 1
    assert 'HeckerStatusBarSleepGesture.reset(this);' in block(source, 'protected void onDetachedFromWindow()')
    panel = (folder / (BASE + 'src/com/android/systemui/shade/NotificationPanelViewController.java')).read_text()
    assert not re.search(r'private (?:boolean|GestureDetector|final ContentObserver) mDoubleTap', panel)
    keyguard = (folder / KEYGUARD).read_text()
    assert 'Settings.Secure.DOUBLE_TAP_TO_SLEEP' not in keyguard
    assert 'Flags.doubleTapToSleep' not in keyguard and 'config_supportDoubleTapSleep' not in keyguard
    assert 'private val systemSettingsRepository: SystemSettingsRepository' in keyguard
    assert 'UserAwareSystemSettingsRepository(' in (folder / SETTINGS_MODULE).read_text()
    assert 'UserAwareSettingsRepository(' in (folder / USER_REPOSITORY).read_text()
    enable = keyguard[keyguard.index('    val isDoubleTapHandlingEnabled:'):keyguard.index('    /* Cache value of')]
    read_setting = block(keyguard, 'private fun isDoubleTapSettingEnabled()')
    click = block(keyguard, 'fun onDoubleClick()')
    touch = (folder / TOUCH).read_text()
    callback = block(touch, 'onDoubleTapDetected =')
    assert 'doubleTapToSleep()' not in callback
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-double-tap-') as temporary:
        out = Path(temporary)
        java = {
            'android/app/ActivityManager.java': '''package android.app;
public class ActivityManager { public static int user; public static int getCurrentUser(){return user;} }''',
            'android/app/KeyguardManager.java': '''package android.app;
public class KeyguardManager { public boolean locked; public boolean isKeyguardLocked(){return locked;} }''',
            'android/os/PowerManager.java': '''package android.os;
public class PowerManager { public int sleeps; public boolean interactive=true;
 public boolean isInteractive(){return interactive;} public void goToSleep(long time){sleeps++;} }''',
            'android/content/ContentResolver.java': 'package android.content; public class ContentResolver {}',
            'android/content/res/Resources.java': '''package android.content.res;
public class Resources { public int getDimensionPixelSize(int id){return 60;} }''',
            'android/content/Context.java': '''package android.content;
public class Context {
 public final android.os.PowerManager power=new android.os.PowerManager();
 public final android.app.KeyguardManager keyguard=new android.app.KeyguardManager();
 public final android.view.accessibility.AccessibilityManager accessibility=new android.view.accessibility.AccessibilityManager();
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public <T>T getSystemService(Class<T> type){return type.cast(type==android.os.PowerManager.class?power:
   type==android.app.KeyguardManager.class?keyguard:accessibility);}
}''',
            'android/provider/Settings.java': '''package android.provider;
public class Settings { public static class System {
 public static final java.util.Map<Integer,Integer> values=new java.util.HashMap<>();
 public static int getIntForUser(android.content.ContentResolver r,String name,int fallback,int user){
  if(!name.equals("double_tap_sleep_gesture"))throw new AssertionError(name);
  return values.getOrDefault(user,fallback);
 }
} }''',
            'android/view/View.java': '''package android.view;
public class View { private final android.content.Context context;
 public View(android.content.Context context){this.context=context;}
 public android.content.Context getContext(){return context;}
}''',
            'android/view/ViewConfiguration.java': '''package android.view;
public class ViewConfiguration {
 public static ViewConfiguration get(android.content.Context context){return new ViewConfiguration();}
 public static int getDoubleTapMinTime(){return 40;} public static int getDoubleTapTimeout(){return 300;}
 public int getScaledTouchSlop(){return 8;} public int getScaledDoubleTapSlop(){return 48;}
}''',
            'android/view/InputDevice.java': 'package android.view; public class InputDevice { public static final int SOURCE_TOUCHSCREEN=4098; }',
            'android/view/MotionEvent.java': '''package android.view;
public class MotionEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_OUTSIDE=4,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6;
 public static final int TOOL_TYPE_FINGER=1;
 public int action,pointers=1,tool=1,source=4098; public long time; public float x=20,y=20;
 public MotionEvent(int action,long time){this.action=action;this.time=time;}
 public int getActionMasked(){return action;} public int getPointerCount(){return pointers;}
 public int getToolType(int index){return tool;} public boolean isFromSource(int kind){return source==kind;}
 public long getEventTime(){return time;} public float getRawX(){return x;} public float getRawY(){return y;}
 public float getY(){return y;}
 public static MotionEvent obtain(MotionEvent event){return new MotionEvent(event.action,event.time);}
 public void setAction(int action){this.action=action;} public void recycle(){}
}''',
            'android/view/GestureDetector.java': '''package android.view;
public class GestureDetector { public static class SimpleOnGestureListener {
 public boolean onDoubleTapEvent(MotionEvent event){return false;} } }''',
            'android/view/accessibility/AccessibilityManager.java': '''package android.view.accessibility;
public class AccessibilityManager { public boolean exploration; public boolean isTouchExplorationEnabled(){return exploration;} }''',
            'android/util/Log.java': 'package android.util; public class Log { public static void d(String tag,String message){} }',
            'com/android/internal/R.java': 'package com.android.internal; public class R { public static class dimen {public static int quick_qs_offset_height=1;} }',
            'com/android/systemui/statusbar/phone/PhoneStatusBarView.java': '''package com.android.systemui.statusbar.phone;
import android.view.MotionEvent; import java.util.function.BooleanSupplier;
public class PhoneStatusBarView extends TouchParent {
 public BooleanSupplier mIsStatusBarInteractiveSupplier;
 public PhoneStatusBarView(android.content.Context context){super(context);}
''' + dispatch + '''
}
class TouchParent extends android.view.View {
 public int nativeCalls,lastAction=-1;
 public TouchParent(android.content.Context c){super(c);}
 public boolean dispatchTouchEvent(MotionEvent event){nativeCalls++;lastAction=event.action;return false;}
}''',
            'com/android/systemui/statusbar/phone/SleepDispatchProbe.java': '''package com.android.systemui.statusbar.phone;
import android.view.MotionEvent; import android.app.ActivityManager; import android.provider.Settings;
import com.android.systemui.hecker.HeckerStatusBarSleepGesture;
public class SleepDispatchProbe {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static MotionEvent event(int action,long time){return new MotionEvent(action,time);}
 static void first(PhoneStatusBarView v){v.dispatchTouchEvent(event(0,0));v.dispatchTouchEvent(event(1,45));}
 static boolean second(PhoneStatusBarView v){v.dispatchTouchEvent(event(0,140));return v.dispatchTouchEvent(event(1,185));}
 public static void main(String[] args){
  android.content.Context c=new android.content.Context(); PhoneStatusBarView v=new PhoneStatusBarView(c);
  first(v);check(!second(v)&&c.power.sleeps==0&&v.lastAction==1); // Default off.
  Settings.System.values.put(0,1);first(v);check(second(v)&&c.power.sleeps==1&&v.lastAction==3);
  check(v.nativeCalls==8); // Completion sends exactly one native CANCEL, not an extra UP.
  first(v);Settings.System.values.put(0,0);check(!second(v)&&c.power.sleeps==1);
  Settings.System.values.put(0,1);first(v);v.mIsStatusBarInteractiveSupplier=()->false;
  check(second(v)&&c.power.sleeps==1);v.mIsStatusBarInteractiveSupplier=()->true;
  check(!second(v)&&c.power.sleeps==1); // Interactivity change cancels the old pair.
  HeckerStatusBarSleepGesture.reset(v);first(v);ActivityManager.user=1;
  check(!second(v)&&c.power.sleeps==1);Settings.System.values.put(1,1);
  first(v);ActivityManager.user=0;check(!second(v)&&c.power.sleeps==1);
  for(int guard=0;guard<3;guard++){
   HeckerStatusBarSleepGesture.reset(v);c.power.interactive=guard!=0;c.keyguard.locked=guard==1;c.accessibility.exploration=guard==2;
   first(v);check(!second(v)&&c.power.sleeps==1);
  }
  c.power.interactive=true;c.keyguard.locked=false;c.accessibility.exploration=false;
  for(int kind=0;kind<4;kind++){
   HeckerStatusBarSleepGesture.reset(v);first(v);MotionEvent e=event(0,140);
   if(kind==0)e.pointers=2; if(kind==1)e.tool=2;if(kind==2)e.source=8194;if(kind==3)e.action=3;
   v.dispatchTouchEvent(e);check(!v.dispatchTouchEvent(event(1,185))&&c.power.sleeps==1);
  }
  HeckerStatusBarSleepGesture.reset(v);first(v);v.dispatchTouchEvent(event(0,140));
  MotionEvent drag=event(2,160);drag.y=90;v.dispatchTouchEvent(drag);
  check(!v.dispatchTouchEvent(event(1,185))&&c.power.sleeps==1);
  first(v);v.dispatchTouchEvent(event(0,140));check(!v.dispatchTouchEvent(event(1,600))&&c.power.sleeps==1);
  HeckerStatusBarSleepGesture.reset(v);first(v);HeckerStatusBarSleepGesture.reset(v);check(!second(v)&&c.power.sleeps==1);
  HeckerStatusBarSleepGesture.reset(v);first(v);check(second(v)&&c.power.sleeps==2);
  java.lang.System.out.println("Native bar dispatch, toggle/user changes, AOD, keyguard, accessibility, pointer and cancel routes: PASS");
 }
}''',
        }
        for name, content in java.items():
            p = out / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(content)
        javac = ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17']
        classes = out / 'classes'
        subprocess.run(javac + ['-d', str(classes)] + [str(out / name) for name in java]
                       + list(map(str, sorted((ROOT / 'framework/gestures').glob('*.java'))))
                       + [str(ROOT / 'tests/DoubleTapSleepGestureTest.java')], check=True)
        for test in ['DoubleTapSleepGestureTest', 'com.android.systemui.statusbar.phone.SleepDispatchProbe']:
            subprocess.run(['java', '-cp', str(classes), test], check=True)
        kotlin = {
            'Inject.kt': 'package javax.inject\nannotation class Inject',
            'Singleton.kt': 'package com.android.systemui.dagger\nannotation class SysUISingleton',
            'FalsingManager.kt': 'package com.android.systemui.plugins\nclass FalsingManager {var isFalseDoubleTap=false}',
            'StatusBarStateController.kt': '''package com.android.systemui.plugins.statusbar
class StatusBarStateController {var isDozing=false;var state=0;fun getState()=state}'''.replace('var state=0;fun getState()=state', 'var stateValue=0;fun getState()=stateValue'),
            'StatusBarState.kt': 'package com.android.systemui.statusbar\nobject StatusBarState {const val KEYGUARD=1}',
            'CentralSurfaces.kt': 'package com.android.systemui.statusbar.phone\nclass CentralSurfaces {var bouncer=false;fun isBouncerShowing()=bouncer}',
            'NativeKeyguard.kt': '''package com.android.systemui.keyguard.domain.interactor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.os.PowerManager
import android.util.Log
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.android.systemui.hecker.HeckerDoubleTapSleep
enum class KeyguardState { LOCKSCREEN }
class Transition {val value=MutableStateFlow(1f); fun transitionValue(state:KeyguardState)=value}
class Repository {val isQuickSettingsVisible=MutableStateFlow(false)}
class SecureLock {val isSecureLockDeviceEnabled=MutableStateFlow(false);fun get()=this}
class Clock {fun uptimeMillis()=10L}
class Pulsing {var calls=0;fun onDoubleTapEvent(){calls++}}
class KeyguardTouchHandlingInteractor(
 val scope:CoroutineScope,val transitionInteractor:Transition,val repository:Repository,
 val systemSettingsRepository:SystemSettingsRepository,val secureLockDeviceInteractor:SecureLock,
 val powerManager:PowerManager,val systemClock:Clock,val pulsingGestureListener:Pulsing
) { companion object {const val TAG="test"}
''' + enable + '\n' + read_setting + '\n' + click + '\n}',
            'NativeTouchView.kt': '''package test
class TouchHandlingView {
 var calls=0
 interface Listener {fun onDoubleTapDetected(view:TouchHandlingView)}
 var listener:Listener?=object:Listener {override fun onDoubleTapDetected(view:TouchHandlingView){calls++}}
 private fun handler(onDoubleTapDetected:()->Unit)=onDoubleTapDetected
 val callback=handler(''' + callback + ''')
}''',
            'SleepFlowProbe.kt': '''package test
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.view.MotionEvent
import com.android.systemui.keyguard.domain.interactor.*
import com.android.systemui.hecker.HeckerDoubleTapSleep
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.android.systemui.shade.QQSGestureListener
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.phone.CentralSurfaces
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
@OptIn(ExperimentalCoroutinesApi::class)
class UserSettings:SystemSettingsRepository {
 val user=MutableStateFlow(0);val values=mapOf(0 to MutableStateFlow(0),1 to MutableStateFlow(1))
 override fun intSetting(name:String,defaultValue:Int):Flow<Int>{check(name==HeckerDoubleTapSleep.SETTING);return user.flatMapLatest{values.getValue(it)}}
 override fun boolSetting(name:String,defaultValue:Boolean)=intSetting(name,0).map{it!=0}
 override suspend fun setInt(name:String,value:Int){values.getValue(user.value).value=value}
 override suspend fun getInt(name:String,defaultValue:Int)=values.getValue(user.value).value
 override suspend fun getString(name:String):String?=null
}
fun main()=runBlocking {
 val owner=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
 val settings=UserSettings();val transition=Transition();val repository=Repository();val secure=SecureLock();val power=PowerManager();val pulsing=Pulsing()
 val interactor=KeyguardTouchHandlingInteractor(owner,transition,repository,settings,secure,power,Clock(),pulsing)
 val collection=owner.launch{interactor.isDoubleTapHandlingEnabled.collect{}}
 suspend fun settle(){repeat(8){yield()};delay(5)}
 suspend fun expect(enabled:Boolean){settle();check(interactor.isDoubleTapHandlingEnabled.value==enabled)}
 expect(false);interactor.onDoubleClick();check(power.sleeps==0&&pulsing.calls==1)
 settings.values.getValue(0).value=1;expect(true);interactor.onDoubleClick();check(power.sleeps==1)
 for(value in listOf(0f,.5f,.999f)){transition.value.value=value;expect(false);interactor.onDoubleClick();check(power.sleeps==1)}
 transition.value.value=1f;repository.isQuickSettingsVisible.value=true;expect(false)
 repository.isQuickSettingsVisible.value=false;secure.isSecureLockDeviceEnabled.value=true;expect(false)
 secure.isSecureLockDeviceEnabled.value=false;expect(true)
 settings.values.getValue(0).value=0;expect(false);settings.user.value=1;expect(true)
 settings.user.value=0;expect(false);settings.user.value=1;expect(true);settings.values.getValue(1).value=0;expect(false)
 val view=TouchHandlingView();view.callback();check(view.calls==1)
 collection.cancel();owner.cancel()
 // Execute the actual legacy listener: header/lockscreen/bouncer, false touches and current user.
 val context=Context();val falsing=FalsingManager();val state=StatusBarStateController();val central=CentralSurfaces()
 val qqs=QQSGestureListener(context,falsing,context.power,state,central)
 android.app.ActivityManager.user=0;Settings.System.values[0]=1
 fun tap(action:Int=1,y:Float=20f):Boolean {val e=MotionEvent(action,100);e.y=y;return qqs.onDoubleTapEvent(e)}
 check(!tap(0));check(tap()&&context.power.sleeps==1)
 state.isDozing=true;check(!tap());state.isDozing=false
 falsing.isFalseDoubleTap=true;check(!tap());falsing.isFalseDoubleTap=false
 check(!tap(y=200f));state.stateValue=1;check(tap(y=200f))
 central.bouncer=true;check(!tap(y=200f));central.bouncer=false
 Settings.System.values[0]=0;check(!tap());android.app.ActivityManager.user=1;Settings.System.values[1]=1;check(tap())
 Settings.System.values[1]=0;check(!tap())
 println("Native lockscreen setting flow/callback and legacy QQS: live toggle, active user, AOD/transition, QS, secure-lock, bouncer and falsing checks: PASS")
}''',
        }
        for name, content in kotlin.items(): (out / name).write_text(content)
        classpath = os.pathsep.join(map(str, [classes, cache / 'kotlin-stdlib.jar', cache / 'kotlinx-coroutines-core-jvm.jar']))
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, jars)),
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
                        '-no-stdlib', '-no-reflect', '-classpath', classpath,
                        '-d', str(classes), *[str(out / name) for name in kotlin],
                        str(folder / QQS), str(folder / SYSTEM_REPOSITORY)], check=True)
        subprocess.run(['java', '-cp', classpath, 'test.SleepFlowProbeKt'], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True, help='Prepared native Android source fixture')
    parser.add_argument('--kotlin-cache', type=Path, default=ROOT / 'generated/compose-check')
    args = parser.parse_args()
    check_double_tap_sleep(args.source, args.kotlin_cache)


if __name__ == '__main__': main()

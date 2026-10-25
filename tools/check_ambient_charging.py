#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Execute the actual prepared power/ambient policy methods against plug and user scenarios."""
from pathlib import Path
import subprocess
import tempfile
import re


def method(source, marker):
    start = source.index(marker)
    first = source.index('{', start)
    depth = 1
    end = first + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def check_ambient_charging(folder):
    base = folder / 'frameworks/base'
    power = method((base / 'services/core/java/com/android/server/power/PowerManagerService.java').read_text(),
                   '    private boolean shouldWakeUpWhenPluggedOrUnpluggedLocked(')
    ambient = method((base / 'core/java/android/hardware/display/AmbientDisplayConfiguration.java').read_text(),
                     '    public boolean alwaysOnEnabled(int user)')
    available = method((base / 'core/java/android/hardware/display/AmbientDisplayConfiguration.java').read_text(),
                       '    public boolean enabled(int user)')
    machine = (base / 'packages/SystemUI/src/com/android/systemui/doze/DozeMachine.java').read_text()
    aod = re.search(r'else if \((mAmbientDisplayConfig\.alwaysOnEnabled.*?)\) \{\s+nextState = State.DOZE_AOD;',
                    machine, re.S).group(1)
    indication = method((base / 'packages/SystemUI/src/com/android/systemui/statusbar/KeyguardIndicationController.java').read_text(),
                        '    protected final void updateDeviceEntryIndication(boolean animate)')
    indication = indication[indication.index('            CharSequence newIndication;'):
                            indication.index('            if (!TextUtils.equals')].replace('return;', 'return null;')
    sources = {
        'android/content/ContentResolver.java': '''package android.content;
public class ContentResolver { public final java.util.Map<String,Integer> values = new java.util.HashMap<>(); }''',
        'android/content/Intent.java': '''package android.content;
public class Intent { public static final String ACTION_BATTERY_CHANGED="battery";
 public final int plugged; public Intent(int plugged) { this.plugged=plugged; }
 public int getIntExtra(String key,int fallback) { return plugged; } }''',
        'android/content/IntentFilter.java': '''package android.content;
public class IntentFilter { public IntentFilter(String key) {} }''',
        'android/content/Context.java': '''package android.content;
public class Context {
 public boolean plugged=true, hardware=true, inverted=false;
 private final ContentResolver resolver=new ContentResolver();
 public ContentResolver getContentResolver() { return resolver; }
 public Intent registerReceiver(Object ignored, IntentFilter filter) { return new Intent(plugged?1:0); }
}''',
        'android/os/BatteryManager.java': '''package android.os;
public class BatteryManager { public static final int BATTERY_PLUGGED_WIRELESS=4;
 public static final String EXTRA_PLUGGED="plugged"; }''',
        'android/provider/Settings.java': '''package android.provider;
public class Settings { public static class Secure {
 public static final String DOZE_ALWAYS_ON="doze_always_on";
 public static int getIntForUser(android.content.ContentResolver resolver,String key,int fallback,int user) {
  return resolver.values.getOrDefault(user+":"+key,fallback);
 } } }''',
        'android/hardware/display/AmbientDisplayConfiguration.java': '''package android.hardware.display;
import android.content.Context;
import android.provider.Settings;
public class AmbientDisplayConfiguration {
 private final Context mContext; private final boolean mAlwaysOnByDefault=false;
 public AmbientDisplayConfiguration(Context context) { mContext=context; }
 private boolean boolSetting(String key,int user,int fallback) {
  return Settings.Secure.getIntForUser(mContext.getContentResolver(),key,fallback,user)!=0;
 }
 private boolean alwaysOnAvailable() { return mContext.hardware; }
 private boolean accessibilityInversionEnabled(int user) { return mContext.inverted; }
 public boolean alwaysOnAvailableForUser(int user) { return alwaysOnAvailable()&&!accessibilityInversionEnabled(user); }
''' + ambient + '\n' + available + '''
 private boolean pulseOnNotificationEnabled(int user) { return false; }
 private boolean pulseOnLongPressEnabled(int user) { return false; }
 private boolean wakeLockScreenGestureEnabled(int user) { return false; }
 private boolean wakeDisplayGestureEnabled(int user) { return false; }
 private boolean pickupGestureEnabled(int user) { return false; }
 private boolean tapGestureEnabled(int user) { return false; }
 private boolean doubleTapGestureEnabled(int user) { return false; }
 private boolean quickPickupSensorEnabled(int user) { return false; }
 private boolean screenOffUdfpsEnabled(int user) { return false; }
}''',
        'com/android/systemui/hecker/HeckerAmbientCharging.java': '''package com.android.systemui.hecker;
public class HeckerAmbientCharging {
 public static boolean plugged=true,route=true; public static int style=1;
 public static boolean isChargingAmbient() { return plugged&&route&&style>0; }
 public static boolean ownsChargingIndication() { return isChargingAmbient(); }
}''',
        'android/text/TextUtils.java': '''package android.text;
public class TextUtils { public static boolean isEmpty(CharSequence value) { return value==null||value.length()==0; } }''',
        'NativeChargingTest.java': '''import android.content.Context;
import android.os.BatteryManager;
import android.provider.Settings;
import android.hardware.display.AmbientDisplayConfiguration;
import android.text.TextUtils;
import java.text.NumberFormat;
public class NativeChargingTest {
 private final Context mContext=new Context();
 private int mUserId=0,mPlugType=1,wakefulness=0;
 private boolean mIsPowered=true,mWakeUpWhenPluggedOrUnpluggedSetting=true;
 private boolean mKeepDreamingWhenUnplugging=false,mTheaterModeEnabled=false;
 private boolean mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig=false,mAlwaysOnEnabled=false;
 private static final int WAKEFULNESS_ASLEEP=0,WAKEFULNESS_AWAKE=1,WAKEFULNESS_DREAMING=2,WAKEFULNESS_DOZING=3;
 private int getGlobalWakefulnessLocked() { return wakefulness; }
 private void setting(String key,int value) { mContext.getContentResolver().values.put(mUserId+":"+key,value); }
 private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
''' + power + '''
 private static class MachineCase {
  final Context context=new Context();
  final AmbientDisplayConfiguration mAmbientDisplayConfig=new AmbientDisplayConfiguration(context);
  final Tracker mUserTracker=new Tracker();
  boolean aod() { return ''' + aod + '''; }
 }
 private static class Tracker { int user; int getUserId() { return user; } }
 private static class IndicationCase {
  CharSequence mBiometricMessage,mTransientIndication,mAlignmentIndication;
  boolean mBatteryPresent=true,mEnableBatteryDefender=false,mPowerPluggedIn=true;
  int mBatteryLevel=80; static final int GONE=8;
  final Area mIndicationArea=new Area(); final WakeLock mWakeLock=new WakeLock();
  CharSequence computePowerIndication() { return "native charging warning"; }
  CharSequence choose() { mIndicationArea.visibility=0; boolean useMisalignmentColor=false;
''' + indication + '''
   return newIndication;
  }
 }
 private static class Area { int visibility; void setVisibility(int value) { visibility=value; } }
 private static class WakeLock { boolean held=true; void setAcquired(boolean value) { held=value; } }
 public static void main(String[] args) {
  NativeChargingTest p=new NativeChargingTest();
  check(!p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Screen-off plug must stay ambient");
  p.wakefulness=WAKEFULNESS_DOZING;
  check(!p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Already on AOD must not wake");
  p.wakefulness=WAKEFULNESS_AWAKE;
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Interactive behavior preserved");
  p.wakefulness=WAKEFULNESS_ASLEEP; p.setting("hecker_charging_on_ambient",0);
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Ambient routing can be disabled");
  p.setting("hecker_charging_on_ambient",1); p.setting("hecker_aod_charging_style",0); p.setting("hecker_charging_animation",0);
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"All charging features off keeps native behavior");
  p.setting("hecker_charging_animation",1);
  check(!p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Animation-only ambient flow");
  p.mContext.hardware=false;
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Unsupported AOD hardware preserves native wake");
  p.mContext.hardware=true; p.mContext.inverted=true;
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Display inversion gating preserved");
  p.mContext.inverted=false; p.mUserId=10; p.setting("hecker_charging_on_ambient",0);
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(false,0,false),"Foreground user settings, not system user");
  p.mIsPowered=false;
  check(p.shouldWakeUpWhenPluggedOrUnpluggedLocked(true,1,false),"Wired unplug native policy preserved");
  check(!p.shouldWakeUpWhenPluggedOrUnpluggedLocked(true,4,false),"Wireless unplug policy preserved");
  MachineCase machine=new MachineCase(); Context c=machine.context;
  AmbientDisplayConfiguration a=machine.mAmbientDisplayConfig;
  check(!a.alwaysOnEnabled(0),"Core always-on setting is not rewritten by charging");
  check(machine.aod(),"Native doze resolves to charging AOD with ordinary always-on off");
  check(a.enabled(0),"Feature makes native doze available even with all gestures off");
  com.android.systemui.hecker.HeckerAmbientCharging.plugged=false;
  check(!machine.aod(),"Unplug ends charging-only AOD");
  c.getContentResolver().values.put("0:doze_always_on",1);
  check(machine.aod(),"Existing always-on remains enabled after unplug");
  c.getContentResolver().values.clear(); com.android.systemui.hecker.HeckerAmbientCharging.plugged=true;
  c.getContentResolver().values.put("10:hecker_charging_on_ambient",0);
  machine.mUserTracker.user=10; com.android.systemui.hecker.HeckerAmbientCharging.route=false;
  check(!machine.aod()&&!a.enabled(10),"Ambient configuration follows the selected user");
  machine.mUserTracker.user=0; com.android.systemui.hecker.HeckerAmbientCharging.route=true;
  com.android.systemui.hecker.HeckerAmbientCharging.style=0;
  check(!machine.aod(),"Animation-only does not create persistent AOD");
  c.getContentResolver().values.put("0:hecker_aod_charging_style",0);
  c.getContentResolver().values.put("0:hecker_charging_animation",0);
  check(!a.enabled(0),"All features off restores the ordinary doze policy");
  com.android.systemui.hecker.HeckerAmbientCharging.style=1;
  c.getContentResolver().values.clear(); c.hardware=false;
  check(!machine.aod()&&!a.enabled(0),"Unsupported hardware is never forced into AOD");
  c.hardware=true; c.inverted=true;
  check(!machine.aod()&&!a.enabled(0),"Ambient override respects inversion");
  IndicationCase i=new IndicationCase();
  check(i.choose()==null&&i.mIndicationArea.visibility==8&&!i.mWakeLock.held,
      "Custom information hides duplicate stock text without retaining its animation wake lock");
  i.mBiometricMessage="Biometric help";
  check(i.choose().equals("Biometric help"),"Biometric guidance keeps precedence");
  i.mBiometricMessage=null; i.mTransientIndication="Transient warning";
  check(i.choose().equals("Transient warning"),"Transient warnings keep precedence");
  i.mTransientIndication=null; i.mAlignmentIndication="Align charger";
  check(i.choose().equals("Align charger"),"Wireless alignment guidance keeps precedence");
  i.mAlignmentIndication=null; i.mEnableBatteryDefender=true;
  check(i.choose().equals("native charging warning"),"Battery defender warning remains visible");
  i.mEnableBatteryDefender=false;
  com.android.systemui.hecker.HeckerAmbientCharging.route=false;
  check(i.choose().equals("native charging warning"),"Disabling custom charging restores native text");
  System.out.println("Prepared native charging wake/AOD policies, foreground users, unplug and hardware gating: PASS");
  System.out.println("Native AOD indication precedence, duplicate suppression and animation wake-lock release: PASS");
 }
}''',
    }
    with tempfile.TemporaryDirectory(prefix='hecker-ambient-native-') as temp:
        output = Path(temp)
        for relative, text in sources.items():
            target = output / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
        subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',str(output/'classes'),
                        *map(str, sorted(output.rglob('*.java')))], check=True)
        subprocess.run(['java','-cp',str(output/'classes'),'NativeChargingTest'], check=True)

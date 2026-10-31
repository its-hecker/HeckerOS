#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the prepared native About home and its controller-owned destinations."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = 'packages/apps/Settings/src/com/android/settings/deviceinfo/aboutphone/MyDeviceInfoFragment.java'


def check_about_navigation(folder):
    source = (folder / NATIVE).read_text()
    begin = source.index('    private com.android.settings.hecker.HeckerAboutPhoneUi mHeckerAboutUi;')
    end = source.index('    @Override\n    public void showDeviceNameWarningDialog', begin)
    methods = source[begin:end]
    ui = (ROOT / 'settings/src/com/android/settings/hecker/HeckerAboutPhoneUi.java').read_text()
    begin = ui.index('    public static boolean isDetails(')
    end = ui.index('\n    public HeckerAboutPhoneUi(', begin)
    details = ui[begin:end]
    with tempfile.TemporaryDirectory(prefix='hecker-about-nav-') as temporary:
        out = Path(temporary)
        files = {
            'android/os/Bundle.java': '''package android.os; public class Bundle {
 public final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public void putBoolean(String key,boolean value){values.put(key,value);}
 public boolean getBoolean(String key,boolean fallback){return (boolean)values.getOrDefault(key,fallback);}
 public void putString(String key,String value){values.put(key,value);}
 public String getString(String key){return (String)values.get(key);}
}''',
            'android/content/Intent.java': '''package android.content; public class Intent {
 public android.os.Bundle arguments;public String key;
 public String getStringExtra(String name){return key;}
 public android.os.Bundle getBundleExtra(String name){return arguments;}
}''',
            'android/content/Context.java': 'package android.content; public class Context {}',
            'android/view/View.java': 'package android.view; public class View {}',
            'android/app/Activity.java': '''package android.app; public class Activity {
 public final android.content.Intent intent=new android.content.Intent();public android.content.Intent getIntent(){return intent;}
}''',
            'androidx/preference/Preference.java': '''package androidx.preference;
public class Preference {public String key;public boolean visible=true,enabled=true;public int clicks;
 public Preference(String key){this.key=key;}public void setVisible(boolean visible){this.visible=visible;}
 public boolean isEnabled(){return enabled;}public void performClick(){clicks++;}
}''',
            'androidx/preference/PreferenceScreen.java': '''package androidx.preference;
public class PreferenceScreen {public final java.util.List<Preference> children=new java.util.ArrayList<>();
 public int getPreferenceCount(){return children.size();}public Preference getPreference(int i){return children.get(i);}
}''',
            'com/android/settingslib/widget/LayoutPreference.java': '''package com.android.settingslib.widget;
public class LayoutPreference extends androidx.preference.Preference {public LayoutPreference(String key){super(key);}}''',
            'com/android/settings/R.java': '''package com.android.settings;
public class R {public static class string {public static final int hecker_about_software=1,hecker_about_device=2;}}''',
            'com/android/settings/hecker/HeckerAboutPhonePreference.java': '''package com.android.settings.hecker;
public class HeckerAboutPhonePreference extends androidx.preference.Preference {
 public static final String KEY="hecker_about_phone";public static final int NAME=0,ANDROID=1,PROCESSOR=2,SOFTWARE=3,DEVICE=4,LEGAL=5;
 public boolean active,editable;public java.util.function.IntConsumer navigate;
 public HeckerAboutPhonePreference(){super(KEY);}public void setActive(boolean value){active=value;}
 public void setNavigator(java.util.function.IntConsumer callback,boolean editable){navigate=callback;this.editable=editable;}
}''',
            'com/android/settings/hecker/HeckerAboutPhoneUi.java': '''package com.android.settings.hecker;
import android.os.Bundle;import android.content.Intent;
public class HeckerAboutPhoneUi {
 public static final String DETAILS="hecker_about_device_details";
 private static final String SEARCH_KEY=":settings:fragment_args_key";
 public static int installs;public HeckerAboutPhoneUi(android.app.Activity activity,android.view.View list){installs++;}
 public void close(){}
''' + details + '\n}',
            'com/android/settings/core/SubSettingLauncher.java': '''package com.android.settings.core;
public class SubSettingLauncher {
 public static SubSettingLauncher launched;public String destination;public android.os.Bundle arguments;public int title,metrics;
 public SubSettingLauncher(android.content.Context c){}
 public SubSettingLauncher setDestination(String value){destination=value;return this;}
 public SubSettingLauncher setArguments(android.os.Bundle value){arguments=value;return this;}
 public SubSettingLauncher setTitleRes(int value){title=value;return this;}
 public SubSettingLauncher setSourceMetricsCategory(int value){metrics=value;return this;}
 public void launch(){launched=this;}
}''',
            'MyDeviceInfoFragment.java': '''package com.android.settings.deviceinfo.aboutphone;
import androidx.preference.*;import com.android.settings.R;import com.android.settingslib.widget.LayoutPreference;
import com.android.settings.hecker.*;import com.android.settings.core.SubSettingLauncher;
public class MyDeviceInfoFragment {
 static final String KEY_MY_DEVICE_INFO_HEADER="my_device_info_header";
 final android.os.Bundle args=new android.os.Bundle();final android.app.Activity activity=new android.app.Activity();
 final PreferenceScreen screen=new PreferenceScreen();final java.util.Map<String,Preference> preferences=new java.util.HashMap<>();
 final HeckerAboutPhonePreference card=new HeckerAboutPhonePreference();
 MyDeviceInfoFragment(){
  screen.children.add(card);preferences.put(card.key,card);
  for(String key:new String[]{"basic_info_category","legal_category","device_detail_category","device_identifiers_category"})screen.children.add(new Preference(key));
  preferences.put(KEY_MY_DEVICE_INFO_HEADER,new LayoutPreference(KEY_MY_DEVICE_INFO_HEADER));
  for(String key:new String[]{"device_name","firmware_version","device_model","legal_container","build_number"})preferences.put(key,new Preference(key));
 }
 @SuppressWarnings("unchecked") <T extends Preference>T findPreference(String key){return (T)preferences.get(key);}
 android.os.Bundle getArguments(){return args;}android.app.Activity getActivity(){return activity;}
 android.content.Context getContext(){return new android.content.Context();}int getMetricsCategory(){return 40;}
 PreferenceScreen getPreferenceScreen(){return screen;}android.view.View getListView(){return new android.view.View();}
''' + methods + '''
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] argv){
  MyDeviceInfoFragment home=new MyDeviceInfoFragment();home.initHeader();
  check(home.card.visible&&home.card.active&&home.card.editable);
  for(Preference p:home.screen.children)check(p.visible==(p==home.card));
  for(int action:new int[]{0,1,2,5})home.card.navigate.accept(action);
  for(String key:new String[]{"device_name","firmware_version","device_model","legal_container"})check(home.preferences.get(key).clicks==1);
  home.preferences.get("device_name").enabled=false;home.initHeader();check(!home.card.editable);
  home.card.navigate.accept(0);check(home.preferences.get("device_name").clicks==1);
  home.card.navigate.accept(3);check(SubSettingLauncher.launched.destination.endsWith("FirmwareVersionSettings")&&SubSettingLauncher.launched.title==1);
  home.card.navigate.accept(4);check(SubSettingLauncher.launched.destination.equals(MyDeviceInfoFragment.class.getName()));
  check(SubSettingLauncher.launched.arguments.getBoolean(HeckerAboutPhoneUi.DETAILS,false));
  home.setHeckerAboutActive(false);check(!home.card.active);
  MyDeviceInfoFragment detail=new MyDeviceInfoFragment();detail.args.putBoolean(HeckerAboutPhoneUi.DETAILS,true);detail.initHeader();
  check(!detail.card.visible&&!detail.card.active);for(Preference p:detail.screen.children)if(p!=detail.card)check(p.visible);
  for(int route=0;route<3;route++){
   MyDeviceInfoFragment search=new MyDeviceInfoFragment();String key=":settings:fragment_args_key";
   if(route==0)search.args.putString(key,"build_number");
   if(route==1)search.activity.intent.key="imei_info";
   if(route==2){search.activity.intent.arguments=new android.os.Bundle();search.activity.intent.arguments.putString(key,"device_name");}
   search.initHeader();check(!search.card.visible);for(Preference p:search.screen.children)if(p!=search.card)check(p.visible);
  }
  System.out.println("Prepared About home/detail/search routes, native preference clicks, policy-disabled rename and active lifecycle: PASS");
 }
}''',
        }
        # All controller and credential behavior remains in the real fragment, outside the UI adapter.
        assert 'mBuildNumberPreferenceController.onActivityResult' in source
        assert 'controller.updateDeviceName(confirm)' in source
        assert 'return MyDeviceInfoScreen.KEY;' in source
        for name, content in files.items():
            path = out / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content)
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17',
                        '-d', str(out / 'classes'), *[str(out / name) for name in files]], check=True)
        subprocess.run(['java', '-cp', str(out / 'classes'),
                        'com.android.settings.deviceinfo.aboutphone.MyDeviceInfoFragment'], check=True)

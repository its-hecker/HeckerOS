#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check Studio resources and compile the actual native shortcut/factory adapters against SDK types."""
import re
import subprocess
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'


def method(source, anchor):
    start = source.index(anchor)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        if source[end] == '{': depth += 1
        if source[end] == '}': depth -= 1
        end += 1
    return source[start:end]


def check(launcher, sdk, helper_classes):
    resources = ET.parse(ROOT / 'aosp-launcher/res/values/studio.xml').getroot()
    names = {node.get('name') for node in resources}
    for source in (ROOT / 'aosp-launcher/src').rglob('*.java'):
        code = source.read_text()
        keys = re.findall(r'\bt\("([a-z_]+)"\)', code)
        keys += re.findall(r'HeckerStudio\.text\([^,]+,\s*"([a-z_]+)"\)', code)
        keys += re.findall(r'\b(?:button|toggle|slider|glassSlider|category|choice)\("([a-z_]+)"', code)
        for key in keys:
            if key.endswith('_'): continue  # Dynamic suffixes have their own generated resource set.
            assert 'hs_' + key in names, (source, key)
    manifest = ET.parse(launcher / 'generated/hecker-launcher/AndroidManifest.xml').getroot()
    app = manifest.find('application')
    for simple in ('HeckerStudioActivity', 'HeckerSearchActivity'):
        cls = 'com.android.launcher3.hecker.' + simple
        assert len([n for n in app.findall('activity') if n.get(A + 'name') == cls]) == 1
        assert (helper_classes / (cls.replace('.', '/') + '.class')).is_file()
    clock = app.find("receiver[@" + A + "name='com.android.launcher3.hecker.HeckerClockWidget']")
    assert clock is not None and clock.get(A + 'exported') == 'false'
    info = ET.parse(ROOT / 'aosp-launcher/res/xml/hs_clock_widget_info.xml').getroot()
    assert info.get(A + 'configure') == 'com.android.launcher3.hecker.HeckerStudioActivity'
    assert info.get(A + 'updatePeriodMillis') == '0'
    assert set(info.get(A + 'resizeMode').split('|')) == {'horizontal', 'vertical'}

    shortcuts = (launcher / 'src/com/android/launcher3/popup/SystemShortcut.java').read_text()
    factories = shortcuts[shortcuts.index('    public static final Factory<com.android.launcher3.Launcher> HECKER_APP_LOCK'):shortcuts.index('    public interface Factory<T')]
    factory_contract = method(shortcuts, 'public interface Factory<T').replace('@Nullable', '').replace('@NonNull', '')
    native_launcher = (launcher / 'src/com/android/launcher3/Launcher.java').read_text()
    methods = '\n'.join(method(native_launcher, anchor) for anchor in (
        'private Stream<SystemShortcut.Factory> getNativeSupportedShortcuts',
        'public Stream<SystemShortcut.Factory> getSupportedShortcuts'))
    apps = (launcher / 'src/com/android/launcher3/allapps/AllAppsStore.java').read_text()
    get_apps = method(apps, 'public AppInfo[] getApps()')
    stubs = {
        'com/android/launcher3/views/ActivityContext.java': '''package com.android.launcher3.views;
public interface ActivityContext { android.content.Context asContext(); }''',
        'com/android/launcher3/model/data/ItemInfo.java': '''package com.android.launcher3.model.data;
public class ItemInfo { public int container; public android.os.UserHandle user;
public android.content.ComponentName getTargetComponent(){return null;} }''',
        'com/android/launcher3/model/data/ItemInfoWithIcon.java': '''package com.android.launcher3.model.data;
public class ItemInfoWithIcon extends ItemInfo { public int runtimeStatusFlags; }''',
        'com/android/launcher3/model/data/AppInfo.java': '''package com.android.launcher3.model.data;
public class AppInfo extends ItemInfoWithIcon { public CharSequence title; public android.content.ComponentName componentName;
public AppInfo clone(){return new AppInfo();} }''',
        'com/android/launcher3/popup/SystemShortcut.java': '''package com.android.launcher3.popup;
import android.view.View;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.model.data.ItemInfo;
public abstract class SystemShortcut<T extends ActivityContext> implements View.OnClickListener {
protected T mTarget; protected ItemInfo mItemInfo;
public SystemShortcut(int icon, int label, T target, ItemInfo item, View view, boolean collapsible) {mTarget=target;mItemInfo=item;}
protected void dismissTaskMenuView(){}
''' + factory_contract + '\n' + factories + '\n}',
        'com/android/launcher3/Launcher.java': '''package com.android.launcher3;
import java.util.stream.Stream;
import com.android.launcher3.model.data.*;
import com.android.launcher3.popup.SystemShortcut;
public class Launcher extends android.app.Activity implements com.android.launcher3.views.ActivityContext {
static final int CONTAINER_DESKTOP=-100,CONTAINER_HOTSEAT=-101,CONTAINER_ALL_APPS=-102,CONTAINER_ALL_APPS_PREDICTION=-103,FLAG_NOT_PINNABLE=32;
static final SystemShortcut.Factory APP_INFO=null,WIDGETS=null,INSTALL=null,REMOVE=null,ADD_TO_HOME_SCREEN=null;
public android.content.Context asContext(){return this;}
''' + methods + '\n}',
        'StudioStore.java': '''import java.util.Arrays;
import com.android.launcher3.model.data.AppInfo;
class StudioStore {
AppInfo[] mApps;
com.android.launcher3.views.ActivityContext mActivityContext;
''' + get_apps + '\n}',
    }
    with tempfile.TemporaryDirectory(prefix='hecker-studio-contract-') as directory:
        folder = Path(directory)
        for name, code in stubs.items():
            path = folder / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(code)
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp',
                        str(sdk / 'android.jar') + ':' + str(helper_classes), '-d', str(folder / 'classes'),
                        *map(str, folder.rglob('*.java'))], check=True)
    print('Studio resources/widget metadata, exact native shortcut factories/stream types and app-label filtering compile: PASS')

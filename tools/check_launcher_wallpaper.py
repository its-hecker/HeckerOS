# SPDX-License-Identifier: Apache-2.0
"""Compile the prepared native wallpaper route against real Android SDK APIs."""
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
SOURCE = 'packages/apps/Launcher3/src/com/android/launcher3/popup/WorkspaceLongPressOptions.kt'


def check_wallpaper_picker(folder, sdk, cache):
    native = (folder / SOURCE).read_text()
    start = native.index('    private fun startWallpaperPicker(')
    end = native.index('\n    private fun createNewFolder(', start)
    # Compile the actual prepared method, including the admin gate and launch extras.
    # Only Launcher-owned contracts are facades; Intent, Settings and other Android
    # types come from the locked SDK, so an invented API constant cannot pass.
    method = native[start:end]
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-wallpaper-route-') as temporary:
        output = Path(temporary)
        classes = output / 'classes'
        facade = output / 'WallpaperFacades.java'
        facade.write_text('''package com.android.launcher3.popup;
interface ActivityContext {}
class Launcher extends android.content.ContextWrapper implements ActivityContext {
 public Launcher(){super(null);}
 public StringCache stringCache;
 public Workspace workspace;
 public void startActivitySafely(android.view.View v,android.content.Intent i,WorkspaceItemInfo info){}
}
class StringCache { public String disabledByAdminMessage; }
class Workspace { public float wallpaperOffsetForCenterPage; }
class WorkspaceItemInfo { public android.content.Intent intent; public long container; }
class Utilities { public static boolean isWallpaperAllowed(Launcher l){return true;} }
class Favorites { public static final long CONTAINER_SETTINGS=-200; }
class R { public static class string {
 public static final int msg_disabled_by_admin=1,wallpaper_picker_package=2;
} }
''')
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                        '-cp', str(sdk / 'android.jar'), '-d', str(classes), str(facade)], check=True)
        probe = output / 'WallpaperRoute.kt'
        probe.write_text('''package com.android.launcher3.popup
import android.content.Intent
import android.text.TextUtils
import android.view.View
import android.widget.Toast
private object NativeWallpaperRoute {
 private const val EXTRA_WALLPAPER_OFFSET="com.android.launcher3.WALLPAPER_OFFSET"
 private const val EXTRA_WALLPAPER_LAUNCH_SOURCE="com.android.wallpaper.LAUNCH_SOURCE"
 private const val EXTRA_WALLPAPER_FLAVOR="com.android.wallpaper.FLAVOR"
''' + method + '\n}\n')
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, jars)),
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
                        '-no-stdlib', '-no-reflect', '-classpath',
                        os.pathsep.join(map(str, [sdk / 'android.jar', cache / 'kotlin-stdlib.jar', classes])),
                        '-d', str(output / 'kotlin'), str(probe)], check=True)
    action = re.search(r'intent\.action\s*=\s*"([^"]+)"', method)
    assert action is not None, 'Review the explicit Settings wallpaper fallback'
    assert 'intent.setPackage("com.android.settings")' in method
    manifest = ET.parse(folder / 'packages/apps/Settings/AndroidManifest.xml').getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    handlers = [activity for activity in manifest.findall('application/activity')
                if activity.get(android + 'exported') == 'true'
                for intent_filter in activity.findall('intent-filter')
                if any(item.get(android + 'name') == action.group(1)
                       for item in intent_filter.findall('action'))
                and any(item.get(android + 'name') == 'android.intent.category.DEFAULT'
                        for item in intent_filter.findall('category'))]
    assert manifest.get('package') == 'com.android.settings' and handlers, \
        'Wallpaper fallback has no exported default handler in pinned Settings'
    print('Prepared native wallpaper-picker Kotlin compiles against the locked Android SDK; '
          'the fallback resolves to an exported pinned Settings action: PASS')

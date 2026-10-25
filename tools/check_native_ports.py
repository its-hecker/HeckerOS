#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check adapters against immutable upstream source files; this is not a ROM build."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from check_launcher import COMPILER
from compile_sdk import fetch
from customization_port import customization_plan
from aosp_launcher_port import aosp_launcher_plan
from prepare import replace_guarded
from privapp_permissions import check_privapp_permissions
from check_pin_bouncer import PIN_INPUTS, check_pin_bouncer
from check_game_mode_info import check_game_mode_info
from check_interaction_integration import INPUTS as INTERACTION_INPUTS, check_service_bindings, check_statusbar_dispatch

ROOT = Path(__file__).resolve().parents[1]


def write(changes):
    for path, value in changes.items():
        if value is None:
            path.unlink()
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value if isinstance(value, bytes) else value.encode())


def snapshot(folder):
    return {str(p.relative_to(folder)): p.read_bytes() for p in folder.rglob('*') if p.is_file()}


def check_theme_broadcast(folder):
    engine = (ROOT / 'framework/themes/ThemeEngine.java').read_text()
    action = re.search(r'ACTION_THEME_CHANGED\s*=\s*"([^"]+)"', engine).group(1)
    manifest = ET.parse(folder / 'frameworks/base/core/res/AndroidManifest.xml').getroot()
    names = [node.get('{http://schemas.android.com/apk/res/android}name')
             for node in manifest.findall('protected-broadcast')]
    assert names.count(action) == 1, 'Theme engine update must have exactly one protected system broadcast'
    print('Theme engine update is declared once as a protected system broadcast')


def check_bootanimation_owner(folder):
    # Check the real module definitions, independently of PRODUCT_PACKAGES: Soong
    # exports install actions even for the unselected PixelOS prebuilt.
    owners = []
    for path in (ROOT / 'Android.bp', folder / 'vendor/custom/bootanimation/Android.bp'):
        for block in re.findall(r'(?ms)^prebuilt_media\s*\{(.*?)^\}', path.read_text()):
            if not re.search(r'(?m)^\s*filename:\s*"bootanimation\.zip",', block):
                continue
            assert re.search(r'(?m)^\s*product_specific:\s*true,', block), 'Boot animation partition changed'
            if not re.search(r'(?m)^\s*enabled:\s*false,', block):
                owners.append(re.search(r'(?m)^\s*name:\s*"([^"]+)"', block).group(1))
    assert owners == ['bootanimation_heckeros'], 'Conflicting boot animation install owners: ' + str(owners)
    print('product/media/bootanimation.zip has exactly one enabled prebuilt owner: HeckerOS')


def check_bootanimation_selection(folder):
    """Execute the actual native path selector against normal/dark/APEX/reboot cases."""
    source = (folder / 'frameworks/base/cmds/bootanimation/BootAnimation.cpp').read_text()
    constants = '\n'.join(line for line in source.splitlines()
                          if re.match(r'static (?:const char|constexpr const char\*)', line)
                          and re.search(r'(?:BOOTANIMATION|SHUTDOWNANIMATION|REBOOT_ANIMATION)_', line))
    begin = source.index('bool BootAnimation::findBootAnimationFileInternal(')
    end = source.index('\nGLuint compileShader(', begin)
    probe = r'''#include <string>
#include <vector>
#include <map>
#include <set>
#include <cassert>
#define R_OK 4
#define ATRACE_CALL() ((void)0)
#define SLOGI(...) ((void)0)
static std::set<std::string> readable;
static std::map<std::string,std::string> properties;
static int access(const char* path,int) { return readable.count(path) ? 0 : -1; }
namespace android { namespace base {
int GetIntProperty(const char* key,int fallback) {
 return properties.count(key) ? std::stoi(properties[key]) : fallback;
}
std::string GetProperty(const char* key,const char* fallback) {
 return properties.count(key) ? properties[key] : fallback;
}
bool GetBoolProperty(const char* key,bool fallback) {
 return properties.count(key) ? properties[key]=="true" : fallback;
}
}}
struct BootAnimation {
 std::string mZipFileName;
 bool mShuttingDown=false;
 bool findBootAnimationFileInternal(const std::vector<std::string>& files);
 void findBootAnimationFile();
};
'''
    probe += constants + '\n' + source[begin:end] + r'''
int main(int argc,char** argv) {
 assert(argc==2);
 int scenario=std::stoi(argv[1]);
 BootAnimation animation;
 std::string expected;
 readable={PRODUCT_BOOTANIMATION_DIR+std::string(PRODUCT_BOOTANIMATION_FILE),APEX_BOOTANIMATION_FILE};
 expected="/product/media/bootanimation.zip";
 if(scenario==1) properties["ro.boot.theme"]="1";
 if(scenario==2) {
  properties["ro.boot.theme"]="1"; readable.insert("/product/media/bootanimation-dark.zip");
  expected="/product/media/bootanimation-dark.zip";
 }
 if(scenario==3) {
  properties["ro.product.bootanim.file"]="custom.zip"; readable.insert("/product/media/custom.zip");
  expected="/product/media/custom.zip";
 }
 if(scenario==4) { readable={APEX_BOOTANIMATION_FILE}; expected=APEX_BOOTANIMATION_FILE; }
 if(scenario==5) { readable={OEM_BOOTANIMATION_FILE,SYSTEM_BOOTANIMATION_FILE}; expected=OEM_BOOTANIMATION_FILE; }
 if(scenario==6) { readable.clear(); expected=""; }
 if(scenario==7) {
  readable.insert(PRODUCT_USERSPACE_REBOOT_ANIMATION_FILE);
  properties["sys.init.userspace_reboot.in_progress"]="true"; expected=PRODUCT_USERSPACE_REBOOT_ANIMATION_FILE;
 }
 if(scenario==8) {
  readable.insert(PRODUCT_SHUTDOWNANIMATION_FILE); animation.mShuttingDown=true;
  expected=PRODUCT_SHUTDOWNANIMATION_FILE;
 }
 animation.findBootAnimationFile(); assert(animation.mZipFileName==expected);
}
'''
    with tempfile.TemporaryDirectory(prefix='hecker-boot-selection-') as temporary:
        output = Path(temporary)
        cpp = output / 'BootSelection.cpp'
        cpp.write_text(probe)
        subprocess.run(['c++', '-std=c++17', '-Wall', '-Werror', str(cpp), '-o', str(output / 'probe')], check=True)
        for scenario in range(9):
            subprocess.run([str(output / 'probe'), str(scenario)], check=True)
    print('Native boot selector: normal/dark product branding, APEX/OEM fallback, shutdown and userspace reboot: PASS')


def check_clock_plugins(folder, sdk):
    """Compile the actual trust provider and PackageConfig, retaining existing plugin policy."""
    base = folder / 'frameworks/base/packages/SystemUI'
    module = (base / 'src/com/android/systemui/plugins/PluginsModule.kt').read_text()
    registry = (base / 'customization/src/com/android/systemui/shared/clocks/ClockRegistry.kt').read_text()
    packages = sorted(set(re.findall(r'"(com\.android\.systemui\.clocks\.[^"]+)" to', registry)))
    assert len(packages) == 9, 'Review changed native clock providers'
    method = re.search(r'(?ms)^        fun providesPackageConfig\(.*?^        \}', module).group(0)
    brightness = (base / 'src/com/android/systemui/brightness/ui/viewmodel/BrightnessSliderViewModel.kt').read_text()
    image_method = re.search(r'(?ms)^    suspend fun loadImage\(.*?^    \}', brightness).group(0)
    lock = json.loads((ROOT / 'tests/compose-inputs.lock.json').read_text())
    cache = sdk.parent / 'compose-check'
    jars = [fetch(cache, name, lock[name]) for name in sorted(COMPILER)]
    with tempfile.TemporaryDirectory(prefix='hecker-clock-policy-') as temporary:
        output = Path(temporary)
        sources = {
            'PackageConfig.kt': (base / 'shared/src/com/android/systemui/shared/plugins/PackageConfig.kt').read_text(),
            'Context.kt': '''package android.content
class Context(val resources: android.content.res.Resources) {
 var lastResource=0
 fun getDrawable(id:Int):android.graphics.drawable.Drawable? {
  lastResource=id
  return if(id<0) null else android.graphics.drawable.Drawable()
 }
}
data class ComponentName(val packageName:String,val className:String) {
 companion object {
  @JvmStatic fun unflattenFromString(value:String):ComponentName? {
   val split=value.indexOf('/'); if(split<0) return null
   return ComponentName(value.substring(0,split),value.substring(split+1))
  }
 }
}''',
            'Resources.kt': '''package android.content.res
class Resources { fun getStringArray(id:Int)=arrayOf("com.android.systemui","com.vendor.existing/ClockProvider") }''',
            'Drawable.kt': '''package android.graphics.drawable
class Drawable''',
            'Icon.kt': '''package com.android.systemui.common.shared.model
class Icon { data class Loaded(val resourceId:Int) }
fun android.graphics.drawable.Drawable.asIcon(description:String?,id:Int)=Icon.Loaded(id)''',
            'BrightnessImageProbe.kt': '''import android.content.Context
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import kotlinx.coroutines.withTimeoutOrNull
annotation class DrawableRes
object BrightnessImageProbe {
''' + image_method + '''
 @JvmStatic fun main(args:Array<String>) = kotlinx.coroutines.runBlocking {
  val context=Context(android.content.res.Resources())
  check(loadImage(7,context)?.resourceId==7 && context.lastResource==7)
  check(loadImage(-1,context)==null && context.lastResource==-1)
 }
}''',
            'ClockPolicyProbe.kt': '''import android.content.Context
import android.content.ComponentName
import com.android.systemui.shared.plugins.PackageConfig
object R { object array { const val config_pluginAllowlist=1 } }
object ClockPolicyProbe {
''' + method + '''
 @JvmStatic fun main(args:Array<String>) {
  val policy=providesPackageConfig(Context(android.content.res.Resources()))
  check(policy.isPrivileged(ComponentName("com.android.systemui","Existing")))
  check(policy.isPrivileged(ComponentName("com.vendor.existing","ClockProvider")))
''' + ''.join('  check(policy.isPrivileged(ComponentName("'+name+'","ClockProvider")))\n' for name in packages) + '''
  check(!policy.isPrivileged(ComponentName("com.thirdparty.clock","ClockProvider")))
  check(!policy.isPrivileged(ComponentName("com.android.systemui.clocks.weather.fake","ClockProvider")))
 }
}''',
        }
        for name, content in sources.items():
            (output / name).write_text(content)
        subprocess.run(['java', '-cp', os.pathsep.join(map(str, jars)),
                        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-jvm-target', '17',
                        '-no-stdlib', '-no-reflect', '-classpath', os.pathsep.join([
                            str(cache / 'kotlin-stdlib.jar'), str(cache / 'kotlinx-coroutines-core-jvm.jar')]),
                        '-d', str(output / 'classes'), *map(str, sorted(output.glob('*.kt')))], check=True)
        subprocess.run(['java', '-cp', os.pathsep.join([str(output / 'classes'),
                        str(cache / 'kotlin-stdlib.jar')]), 'ClockPolicyProbe'], check=True)
        subprocess.run(['java', '-cp', os.pathsep.join([str(output / 'classes'),
                        str(cache / 'kotlin-stdlib.jar'), str(cache / 'kotlinx-coroutines-core-jvm.jar')]),
                        'BrightnessImageProbe'], check=True)
    print('Actual SystemUI plugin provider and PackageConfig: bundled clocks trusted; existing policy retained; unrelated packages rejected: PASS')
    print('Actual brightness icon loader compiles and loads drawable resources without ImageDecoder: PASS')


def check_recents_transaction(folder):
    source = (folder / 'frameworks/base/services/core/java/com/android/server/om/OverlayManagerService.java').read_text()
    enabled = source.split('case TYPE_SET_ENABLED:', 1)[1].split('case TYPE_SET_DISABLED:', 1)[0]
    assert 'mImpl.setEnabled(request.overlay, true' in enabled
    assert 'mImpl.setHighestPriority(request.overlay, realUserId)' in enabled
    assert enabled.index('mImpl.setEnabled(') < enabled.index('mImpl.setHighestPriority(')
    print('Pinned OverlayManager transaction enables the Recents overlay and raises its priority together')


def check_launcher_weather(folder):
    launcher = folder / 'packages/apps/Launcher3'
    controller = (launcher / 'src/com/android/launcher3/quickspace/QuickspaceController.java').read_text()
    assert 'new HeckerWeather(context, this::notifyListeners)' in controller, 'Google weather was removed'
    assert 'OmniJawsClient' not in controller, 'OmniJaws dependency remains'
    assert 'OmniJawsClient.get(' not in controller, 'OmniJaws client startup remains'
    assert 'addOmniJawsIfEnabled(' not in controller, 'OmniJaws startup remains'
    preferences = ET.parse(launcher / 'res/xml/launcher_home_screen_preferences.xml').getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    keys = {node.get(android + 'key') for node in preferences.iter()}
    assert 'pref_quickspace_weather' in keys, 'Google weather toggle was removed'
    assert not keys.intersection({'pref_quickspace_weather_provider',
                                  'pref_quickspace_weather_city',
                                  'pref_quickspace_weather_text'}), 'OmniJaws preferences remain'
    print('Launcher keeps Google weather; OmniJaws startup, fallback and controls are absent')


def check_launcher_weather_module(folder):
    generated = folder / 'packages/apps/Launcher3/generated/hecker-google-weather'
    expected = generated / 'Android.bp'
    # Soong discovers both the Suite sources and the prepared launcher. Checking
    # only the generated module misses an active definition in its source folder.
    definitions = [path for root in (ROOT / 'launcher/compat', generated)
                   for path in root.rglob('Android.bp')
                   if re.search(r'\bname\s*:\s*"HeckerLauncherGoogleWeather"', path.read_text())]
    assert definitions == [expected], 'Duplicate or missing Google weather module: ' + str(definitions)
    assert list((generated / 'src').rglob('*.kt')), 'Google weather module has no Kotlin sources'
    print('HeckerLauncherGoogleWeather has exactly one active Soong definition')


def check_launcher_resource_scope(folder):
    launcher = folder / 'packages/apps/Launcher3'
    bp = (launcher / 'Android.bp').read_text()
    libraries = re.findall(r'(?ms)^android_library\s*\{(.*?)^\}', bp)
    library = next(b for b in libraries if re.search(
        r'name:\s*"Launcher3QuickStepLib"', b))
    resources = re.search(r'resource_dirs:\s*\[(.*?)\]', library, re.S).group(1)
    directories = re.findall(r'"([^"]+)"', resources)
    view = (launcher / 'src/com/android/launcher3/hecker/HeckerLottieView.java').read_text()
    for name in re.findall(r'R\.raw\.(\w+)', view):
        assert any(list((launcher / directory / 'raw').glob(name + '.*'))
                   for directory in directories), 'Raw resource missing from Java library: ' + name
    print('Lottie raw resources belong to Launcher3QuickStepLib, where the view compiles')


def launcher_navbar_uri_probe(folder):
    """Compile the actual four navigation-size observers, including imported Settings aliases."""
    taskbar = folder / 'packages/apps/Launcher3/quickstep/src/com/android/launcher3/taskbar'
    classes = []
    for name, prefix in [('TaskbarActivityContext', 'URI_NAVBAR_'),
                         ('TaskbarManagerImpl', 'GESTURE_NAVBAR_')]:
        source = (taskbar / (name + '.java')).read_text()
        names = '|'.join(prefix + axis + '_MODE' + ('_URI' if prefix == 'GESTURE_NAVBAR_' else '')
                         for axis in ('LENGTH', 'HEIGHT'))
        declarations = re.findall(
            r'(?m)^\s*(?:private|public)\s+static\s+final\s+Uri\s+(?:' + names + r')\s*=[^;]+;', source)
        assert len(declarations) == 2, 'Missing native navigation observers in ' + name
        classes.append('static class ' + name + ' {\n' + '\n'.join(declarations) + '\n}')
    return ('import android.net.Uri;\nimport android.provider.Settings;\n'
            'import android.provider.Settings.System;\n'
            'public class LauncherNavbarUris {\n' + '\n'.join(classes) + '\n}\n')


def check_launcher_navbar_uris(folder, sdk):
    with tempfile.TemporaryDirectory(prefix='hecker-navbar-uris-') as temporary:
        output = Path(temporary)
        source = output / 'LauncherNavbarUris.java'
        source.write_text(launcher_navbar_uri_probe(folder))
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp',
                        str(sdk / 'android-system.jar'), '-d', str(output / 'classes'),
                        str(source)], check=True)
    print('Both taskbar navigation-size URI declarations compile against the Android SDK')


def check_secure_capture(folder):
    """Execute the actual prepared WMS method, including secure-surface refreshes."""
    wms = (folder / 'frameworks/base/services/core/java/com/android/server/wm/WindowManagerService.java').read_text()
    begin = wms.index('        void updateDisableSecureWindows() {')
    end = wms.index('        void updateMagnifyIme()', begin)
    method = wms[begin:end]
    assert 'mCurrentUserId = newUserId;\n            mSettingsObserver.updateDisableSecureWindows();' in wms
    probe = r'''import java.util.Map;
import java.util.HashMap;
class CaptureUserProbe {
 static class Settings {
  static class SettingNotFoundException extends Exception {}
  static class Secure {
   static String DISABLE_SECURE_WINDOWS="disable_secure_windows";
   static Map<Integer,Integer> values = new HashMap<>();
   static int getIntForUser(Object resolver,String key,int user) throws SettingNotFoundException {
    if (!values.containsKey(user)) throw new SettingNotFoundException();
    return values.get(user);
   }
  }
 }
 static class Context { Object getContentResolver(){return this;} }
 static class Root { int refreshes; void refreshSecureSurfaceState(){refreshes++;} }
 final Context mContext=new Context(); final Object mGlobalLock=new Object(); final Root mRoot=new Root();
 boolean mDisableSecureWindows=false; int mCurrentUserId;
''' + method + r'''
 static void check(boolean value) { if(!value)throw new AssertionError(); }
 public static void main(String[] args) {
  CaptureUserProbe w=new CaptureUserProbe();
  w.updateDisableSecureWindows(); check(!w.mDisableSecureWindows && w.mRoot.refreshes==0);
  Settings.Secure.values.put(0,1); w.updateDisableSecureWindows(); check(w.mDisableSecureWindows && w.mRoot.refreshes==1);
  w.updateDisableSecureWindows(); check(w.mRoot.refreshes==1);
  w.mCurrentUserId=10; w.updateDisableSecureWindows(); check(!w.mDisableSecureWindows && w.mRoot.refreshes==2);
  Settings.Secure.values.put(10,1); w.updateDisableSecureWindows(); check(w.mDisableSecureWindows && w.mRoot.refreshes==3);
  w.mCurrentUserId=0; Settings.Secure.values.put(0,0); w.updateDisableSecureWindows();
  check(!w.mDisableSecureWindows && Settings.Secure.values.get(10)==1 && w.mRoot.refreshes==4);
 }
}
'''
    with tempfile.TemporaryDirectory(prefix='hecker-capture-user-') as directory:
        source = Path(directory) / 'CaptureUserProbe.java'
        source.write_text(probe)
        subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',directory,str(source)], check=True)
        subprocess.run(['java','-cp',directory,'CaptureUserProbe'], check=True)
    print('Actual WindowManager secure-capture setting: default off, per-user changes, switch isolation and live surface refresh PASS')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / 'out/native-source-cache')
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--launcher-source', type=Path,
                        help='Read the pinned commit directly from an existing launcher Git checkout')
    parser.add_argument('--sdk-cache', type=Path,
                        help='Also compile native declarations and exercise PIN entry with locked compilers')
    parser.add_argument('--kotlin-cache', type=Path, default=ROOT / 'generated/compose-check',
                        help='Locked Kotlin dependencies for the PIN bouncer regression check')
    args = parser.parse_args()
    revisions = json.loads((ROOT / 'integration/source-revisions.json').read_text())
    sources = dict(revisions['pixelos_inputs'])
    sources['device/google/coral'] = revisions['hardware_sources']['device']
    git_sources = {}
    def git_source(origin):
        revision = origin['revision']
        if revision in git_sources:
            return git_sources[revision]
        folder = args.launcher_source or args.cache / 'git-sources' / revision
        folder = folder.resolve()
        if args.launcher_source is None:
            if not (folder / '.git').exists():
                folder.mkdir(parents=True, exist_ok=True)
                subprocess.run(['git', 'init', '-q', str(folder)], check=True)
            present = subprocess.run(['git', '-C', str(folder), 'cat-file', '-e', revision + '^{commit}'],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
            if not present:
                if args.offline:
                    raise SystemExit('Missing immutable launcher Git source: ' + revision)
                subprocess.run(['git', '-C', str(folder), 'fetch', '-q', '--depth=1',
                                origin['repository'], revision], check=True)
        subprocess.run(['git', '-C', str(folder), 'cat-file', '-e', revision + '^{commit}'], check=True)
        git_sources[revision] = folder
        return folder
    paths = set(json.loads((ROOT / 'framework/customization-patches.json').read_text()))
    paths.update(PIN_INPUTS)
    paths.update(INTERACTION_INPUTS)
    paths.add('frameworks/base/core/java/android/view/WindowManager.java')
    paths.update('packages/apps/Launcher3/' + name for name in ('Android.bp', 'aconfig/Android.bp', 'AndroidManifest.xml', 'src/com/android/launcher3/LauncherFiles.java'))
    paths.update('packages/apps/Launcher3/' + name for name in json.loads((ROOT/'aosp-launcher/patches.json').read_text()))
    paths.add('frameworks/base/core/java/android/content/res/Resources.java')
    paths.add('frameworks/base/core/res/AndroidManifest.xml')
    paths.add('frameworks/base/packages/SystemUI/shared/src/com/android/systemui/shared/plugins/PackageConfig.kt')
    paths.add('frameworks/base/packages/SystemUI/customization/src/com/android/systemui/shared/clocks/ClockRegistry.kt')
    paths.add('frameworks/base/services/core/java/com/android/server/om/OverlayManagerService.java')
    def cached_source(relative):
        project = max((p for p in sources if relative.startswith(p + '/')), key=len)
        # A pin update must never reuse content fetched for an older revision.
        return args.cache / sources[project]['revision'] / relative

    for relative in sorted(paths):
        cached = cached_source(relative)
        if cached.is_file():
            continue
        if args.offline:
            raise SystemExit('Missing immutable source: ' + relative)
        project = max((p for p in sources if relative.startswith(p + '/')), key=len)
        origin = sources[project]
        repository = origin['repository']
        googlesource = repository.startswith('https://android.googlesource.com/')
        if repository.startswith('https://gitea.com/'):
            # Gitea's raw HTTP endpoint may be unavailable while its Git source is public.
            # Read immutable Git blobs, never an uncommitted checkout or moving branch.
            folder = git_source(origin)
            content = subprocess.check_output(['git', '-C', str(folder), 'show',
                origin['revision'] + ':' + relative[len(project) + 1:]])
            cached.parent.mkdir(parents=True, exist_ok=True)
            cached.write_bytes(content)
            continue
        if googlesource:
            url = '{}/+/{}/{}?format=TEXT'.format(
                repository, origin['revision'], relative[len(project) + 1:])
        else:
            url = 'https://raw.githubusercontent.com/{}/{}/{}'.format(
                repository, origin['revision'], relative[len(project) + 1:])
        with urllib.request.urlopen(url, timeout=45) as response:
            content = response.read(8 * 1024 * 1024 + 1)
        if len(content) > 8 * 1024 * 1024:
            raise SystemExit('Unexpected source size: ' + relative)
        if googlesource:
            content = base64.b64decode(content, validate=True)
        cached.parent.mkdir(parents=True, exist_ok=True)
        cached.write_bytes(content)
    with tempfile.TemporaryDirectory(prefix='hecker-native-check-') as temporary:
        folder = Path(temporary)
        for relative in paths:
            target = folder / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(cached_source(relative), target)
        before = snapshot(folder)
        required = check_privapp_permissions(folder / 'frameworks/base/core/res/AndroidManifest.xml', ROOT)
        print(f'Suite allowlist covers {len(required)} privileged permissions in the pinned Android framework')
        plans = (customization_plan, aosp_launcher_plan)
        count = 0
        for plan in plans:
            changes = plan(folder, replace_guarded)
            count += len(changes)
            write(changes)
            assert not plan(folder, replace_guarded), 'Adapter is not idempotent'
        check_theme_broadcast(folder)
        check_bootanimation_owner(folder)
        check_bootanimation_selection(folder)
        check_secure_capture(folder)
        check_game_mode_info(folder)
        check_service_bindings(folder)
        window_types = (folder / 'frameworks/base/core/java/android/view/WindowManager.java').read_text()
        assert re.search(r'TYPE_STATUS_BAR_ADDITIONAL\s*=\s*FIRST_SYSTEM_WINDOW\s*\+\s*41\s*;', window_types)
        assert 'public void setTrustedOverlay()' in window_types
        for relative, module in [('Android.bp', 'launcher-aosp-tapl'),
                                 ('aconfig/Android.bp', 'com_android_launcher3_flags_lib')]:
            source = (folder / 'packages/apps/Launcher3' / relative).read_text()
            assert len(re.findall(r'\bname:\s*"' + module + '"', source)) == 1, module
        launcher=folder/'packages/apps/Launcher3'
        manifest=(launcher/'generated/hecker-launcher/AndroidManifest.xml').read_text()
        assert 'android.permission.DEVICE_POWER' in manifest and 'TouchInteractionService' not in manifest
        assert 'android:debuggable="true"' not in manifest
        assert 'SHARED_PREFERENCES_KEY = "com.android.launcher3.prefs"' in (launcher/'src/com/android/launcher3/LauncherFiles.java').read_text()
        assert 'HeckerWorkspaceGestures.touch(this, ev,' in (launcher/'src/com/android/launcher3/Workspace.java').read_text()
        assert 'hecker_double_tap_sleep' in (launcher/'res/xml/launcher_preferences.xml').read_text()
        assert (ROOT/'aosp-launcher/res/drawable/ic_launcher_home.xml').read_bytes()==(launcher/'generated/hecker-launcher/res/drawable/ic_launcher_home.xml').read_bytes()
        if args.sdk_cache:
            with tempfile.TemporaryDirectory(prefix='hecker-aosp-launcher-') as launcher_check:
                output=Path(launcher_check)
                fixture=output/'src/com/android/launcher3/LauncherFiles.java'
                fixture.parent.mkdir(parents=True,exist_ok=True)
                fixture.write_text('package com.android.launcher3;public class LauncherFiles{public static final String SHARED_PREFERENCES_KEY="com.android.launcher3.prefs";}')
                subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',str(args.sdk_cache/'android.jar'),'-d',str(output/'classes'),str(fixture),str(ROOT/'aosp-launcher/src/com/android/launcher3/hecker/HeckerWorkspaceGestures.java')],check=True)
        print('AOSP Hecker Launcher HOME module, branding, preference/gesture wiring, permission, retained flag/TAPL libraries and unchanged Pixel Recents: PASS')
        if args.sdk_cache:
            check_clock_plugins(folder, args.sdk_cache.resolve())
            check_pin_bouncer(folder, args.kotlin_cache.resolve())
            check_statusbar_dispatch(folder, args.kotlin_cache.resolve())
        for plan in reversed(plans):
            write(plan(folder, replace_guarded, True))
        assert before == snapshot(folder), 'Revert changed upstream content'
    print(f'{len(paths)} immutable upstream files checked; {count} source adapters/copies apply idempotently and revert exactly.')
    print('Full Android/Soong compilation and coral testing are still required.')


if __name__ == '__main__':
    main()

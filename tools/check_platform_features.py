#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the owned Settings adapters and check Axion PIF against pinned source.

This does not build Settings/Android, run biometric enrollment, or obtain a Play verdict.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def source(project, cache):
    folder = cache / project['revision']
    for name, digest in project['files'].items():
        path = folder / name
        if not path.exists():
            url = 'https://raw.githubusercontent.com/{}/{}/{}'.format(
                project['repository'], project['revision'], name)
            with urllib.request.urlopen(url, timeout=45) as response:
                data = response.read(1024 * 1024 + 1)
            if len(data) > 1024 * 1024:
                raise ValueError('Unexpected feature source size: ' + name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError('Pinned feature source differs: ' + str(path))
    return folder


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--settings-source', type=Path)
    parser.add_argument('--framework-source', type=Path)
    parser.add_argument('--source-cache', type=Path, default=ROOT/'out/platform-feature-sources')
    parser.add_argument('--sdk-cache', type=Path)
    parser.add_argument('--metadata-only', action='store_true',
                        help='Read-only smoke check of public Google beta metadata; no phone changes')
    args = parser.parse_args()
    lock = json.loads((ROOT/'integration/platform-features.json').read_text())
    settings = args.settings_source or source(lock['settings'], args.source_cache)
    if args.metadata_only:
        with tempfile.TemporaryDirectory(prefix='hecker-beta-metadata-') as temporary:
            out = Path(temporary)
            run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',out,
                settings/'src/com/android/settings/hecker/PifProfile.java',
                settings/'src/com/android/settings/hecker/PixelBetaProfileFetcher.java',
                settings/'tools/hecker/tests/PifProfileTest.java')
            run('java','-cp',out,'com.android.settings.hecker.PifProfileTest','--live')
        return
    framework = args.framework_source or source(lock['framework'], args.source_cache)
    if args.sdk_cache is None: parser.error('--sdk-cache is required for the compilation checks')
    sdk = args.sdk_cache.resolve()
    jar = settings/'libs/pixel-face-ui/stock-pose-grid.jar'
    origin = json.loads((settings/'libs/pixel-face-ui/ORIGIN.json').read_text())
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == origin['sha256']
    with zipfile.ZipFile(jar) as archive:
        assert archive.namelist() and all(p.startswith(
            'com/google/android/settings/biometrics/face/anim/curve/')
            and p.endswith('.class') for p in archive.namelist())
    assert '-keep class com.google.android.settings.biometrics.face.anim.curve.**' in (
        settings/'proguard.flags').read_text()
    fragment = (settings/'src/com/android/settings/biometrics/face/FaceEnrollPreviewFragment.java').read_text()
    assert 'mProgressView.onEnrollmentHelp(helpMsgId, helpString);' in fragment
    assert 'mProgressView.setEnrollmentActive(false);' in fragment
    assert 'mSurfaceView.getHolder().setFixedSize(' in fragment
    enrolling = (settings/'src/com/android/settings/biometrics/face/FaceEnrollEnrolling.java').read_text()
    assert 'if (remaining == 0 && !mCompletionStarted)' in enrolling
    assert 'launchFinish(mToken);' in enrolling
    manifest = (settings/'AndroidManifest.xml').read_text()
    assert 'android:name="com.android.settings.hecker.HeckerIntegrityActivity"\n            android:exported="false"' in manifest
    with tempfile.TemporaryDirectory(prefix='hecker-platform-features-') as temporary:
        out = Path(temporary)
        resources = out/'res/values'
        resources.mkdir(parents=True)
        # Existing Settings declarations used by the unchanged generic ring.
        (resources/'base.xml').write_text('<resources><bool name="config_face_enroll_hal_owns_camera">true</bool>'
            + ''.join('<color name="face_enroll_'+n+'">#ff4285f4</color>'
                      for n in ['progress', 'warning', 'error', 'success'])+'</resources>')
        (out/'AndroidManifest.xml').write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.android.settings">'
            '<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="37"/><application/></manifest>')
        imported = out/'owned-res'
        for name in lock['settings']['files']:
            if name.startswith('res/'):
                path = imported/name.removeprefix('res/')
                path.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(settings/name,path)
        run(sdk/'aapt2', 'compile', '--dir', imported, '-o', out/'resources.zip')
        run(sdk/'aapt2', 'compile', '--dir', out/'res', '-o', out/'base.zip')
        # Native full-page layout also uses SetupDesign; check the imported renderer resources here.
        with zipfile.ZipFile(out/'resources.zip') as original, zipfile.ZipFile(out/'pixel.zip','w') as filtered:
            for name in original.namelist():
                if not name.startswith('layout_'): filtered.writestr(name, original.read(name))
        run(sdk/'aapt2','link','-I',sdk/'android.jar','--manifest',out/'AndroidManifest.xml',
            '--java',out/'generated','-o',out/'resources.apk',out/'pixel.zip',out/'base.zip')
        excluded = {'FaceEnrollPreviewFragment.java', 'FaceEnrollEnrolling.java'}
        java = [p for p in (settings/'src').rglob('*.java') if p.name not in excluded]
        # The fetched source set is narrow; --settings-source can point to a full checkout.
        java = [p for p in java if str(p.relative_to(settings/'src')) in lock['settings_java']]
        run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',str(sdk/'android-system.jar')+os.pathsep+str(jar),
            '-d',out/'settings-classes',out/'generated/com/android/settings/R.java',*java,
            *sorted((settings/'tools/hecker/tests').glob('*.java')))
        for test in ['com.android.settings.biometrics.face.FacePoseEnrollmentStateTest',
                     'com.android.settings.hecker.PifProfileTest']:
            run('java','-cp',out/'settings-classes',test)
        stubs = out/'stubs/android'
        (stubs/'app').mkdir(parents=True)
        (stubs/'app/ActivityThread.java').write_text('package android.app; public class ActivityThread {'
            'public static String currentPackageName(){return null;}}')
        (stubs/'util').mkdir()
        (stubs/'util/Log.java').write_text('package android.util; public class Log {'
            +' '.join('public static int '+n+'(String a,String b){return 0;} '
                       'public static int '+n+'(String a,String b,Throwable t){return 0;}'
                       for n in ['i','d','w','e'])+'}')
        run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',sdk/'android-system.jar',
            '-d',out/'framework-classes',*sorted(stubs.rglob('*.java')),
            framework/'core/java/android/security/pif/PlayIntegritySpoofService.java',
            framework/'tests/hecker/PlayIntegrityScopeTest.java')
        run('java','-cp',str(out/'framework-classes')+os.pathsep+str(sdk/'android-system.jar'),
            'android.security.pif.PlayIntegrityScopeTest')
        cpp = (framework/'core/jni/android_os_SystemProperties.cpp').read_text()
        start = cpp.index('namespace pif_prop_virt {')
        end = cpp.index('}  // namespace pif_prop_virt',start)+len('}  // namespace pif_prop_virt')
        native = '''#include <algorithm>
#include <cerrno>
#include <cinttypes>
#include <climits>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <linux/futex.h>
#include <string>
#include <string_view>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <unistd.h>
#include <unordered_map>
#include <vector>
#define PROP_VALUE_MAX 92
#define ALOGE(...) ((void)0)
#define ALOGD(...) ((void)0)
struct prop_info;
extern "C" const prop_info* __system_property_find(const char*);
extern "C" void __system_property_read_callback(const prop_info*, void (*)(void*,const char*,const char*,uint32_t), void*);
extern "C" int __system_property_foreach(void (*)(const prop_info*,void*),void*);
extern "C" int __system_property_get(const char*,char*);
'''+cpp[start:end]+'\n'+(framework/'tests/hecker/NativeVirtualizationTest.cpp').read_text()
        (out/'native.cpp').write_text(native)
        run('g++','-std=c++20','-O1','-g','-fsanitize=address,undefined','-o',out/'native-test',out/'native.cpp')
        run(out/'native-test')
        assert 'FindClass("android/security/pif/PlayIntegritySpoofService")' in cpp
        assert '"([Ljava/lang/String;[Ljava/lang/String;Z)Z"' in cpp
    print('Owned Settings UI Java/resource checks and Axion PIF host regressions: PASS')
    print('Full Settings/platform build, Pixel 4 enrollment and Play Integrity verdict testing remain required.')


if __name__ == '__main__': main()

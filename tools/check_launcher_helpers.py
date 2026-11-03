#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile owned Launcher Java/resources against the locked Android SDK without upstream downloads.

Only Launcher3's database/receiver contracts use host stubs. Native source-hook
verification remains in check_native_ports.py; this is not a complete ROM build.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, required=True)
    args = parser.parse_args(); sdk = args.sdk_cache.resolve()
    if not (sdk / 'android.jar').is_file() or not (sdk / 'aapt2').is_file():
        raise SystemExit('Run tools/compile_sdk.py with the same SDK cache first')
    with tempfile.TemporaryDirectory(prefix='hecker-launcher-helpers-') as temporary:
        output = Path(temporary)
        fixture = output / 'src/com/android/launcher3/LauncherFiles.java'
        fixture.parent.mkdir(parents=True)
        fixture.write_text('package com.android.launcher3;public class LauncherFiles{'
                           'public static final String SHARED_PREFERENCES_KEY="com.android.launcher3.prefs";}')
        receiver = fixture.with_name('SessionCommitReceiver.java')
        receiver.write_text('package com.android.launcher3;public class SessionCommitReceiver{'
                            'public static final String ADD_ICON_PREFERENCE_KEY="pref_add_icon_to_home";'
                            'public static boolean isEnabled(android.content.Context c,android.os.UserHandle u){return true;}}')
        sources = [fixture, receiver, *sorted((ROOT / 'tests/launcher-api-stubs').rglob('*.java')),
                   *sorted((ROOT / 'aosp-launcher/src').rglob('*.java'))]
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', str(sdk / 'android.jar'),
                        '-d', str(output / 'classes'), *map(str, sources)], check=True)
        subprocess.run([str(sdk / 'aapt2'), 'compile', '--dir', str(ROOT / 'aosp-launcher/res'),
                        '-o', str(output / 'resources.zip')], check=True)
        manifest = output / 'AndroidManifest.xml'
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="com.android.launcher3"><application/></manifest>')
        subprocess.run([str(sdk / 'aapt2'), 'link', '-I', str(sdk / 'android.jar'), '--manifest', str(manifest),
                        '-o', str(output / 'launcher-res.apk'), str(output / 'resources.zip')], check=True)
    print('All owned Launcher Java classes and resource linking against the locked Android SDK: PASS')
    print('Native Launcher3 source-hook checks, full platform build and phone testing remain required.')


if __name__ == '__main__': main()

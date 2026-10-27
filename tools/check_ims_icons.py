#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Link the IMS icons and compile their real callbacks against the locked system SDK.

SystemUI/Dagger dependencies and platform-only Settings/ContentResolver methods
are stubbed. Android telephony classes, registration attributes and IMS callbacks
come from the checked SDK, not host mocks.
This is a source/API check, not a SystemUI APK or device telephony test.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(*arguments):
    subprocess.run(list(map(str, arguments)), check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-cache", type=Path, required=True)
    args = parser.parse_args()
    sdk = args.sdk_cache.resolve()
    lock = json.loads((ROOT / "tests/sdk-inputs.lock.json").read_text())
    for name in ("android.jar", "android-system.jar"):
        if hashlib.sha256((sdk / name).read_bytes()).hexdigest() != lock[name]["sha256"]:
            raise ValueError("IMS check SDK mismatch: " + name)
    with tempfile.TemporaryDirectory(prefix="hecker-ims-icons-") as temporary:
        output = Path(temporary)
        resources = output / "res"
        for name in ("ic_hecker_volte.xml", "ic_hecker_vowifi.xml"):
            (resources / "drawable").mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / "framework/ims" / name, resources / "drawable" / name)
        (resources / "values").mkdir()
        shutil.copyfile(ROOT / "framework/ims/strings.xml", resources / "values/strings.xml")
        manifest = output / "AndroidManifest.xml"
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="com.android.systemui"><application/></manifest>')
        run(sdk / "aapt2", "compile", "--dir", resources, "-o", output / "resources.zip")
        run(sdk / "aapt2", "link", "-I", sdk / "android.jar", "--manifest", manifest,
            "--custom-package", "com.android.systemui.res", "--java", output / "generated",
            "-o", output / "resources.apk", output / "resources.zip")
        stubs = {
            "android/content/ContentResolver.java":
                'package android.content; public abstract class ContentResolver {'
                'public final void registerContentObserver(android.net.Uri uri, boolean descendants,'
                'android.database.ContentObserver observer, int user) {} }',
            "android/provider/Settings.java":
                'package android.provider; public final class Settings {'
                'public static final class Secure {'
                'public static android.net.Uri getUriFor(String name) { return null; }'
                'public static int getIntForUser(android.content.ContentResolver r, String key,'
                'int fallback, int user) { return fallback; }}'
                'public static final class Global {'
                'public static final String AIRPLANE_MODE_ON = "airplane_mode_on";'
                'public static int getInt(android.content.ContentResolver r, String key,'
                'int fallback) { return fallback; }}}',
            "com/android/systemui/CoreStartable.java":
                'package com.android.systemui; public interface CoreStartable { void start(); '
                'default void dump(java.io.PrintWriter writer, String[] args) {} }',
            "com/android/systemui/dagger/SysUISingleton.java":
                'package com.android.systemui.dagger; public @interface SysUISingleton {}',
            "com/android/systemui/dagger/qualifiers/Application.java":
                'package com.android.systemui.dagger.qualifiers; public @interface Application {}',
            "javax/inject/Inject.java": 'package javax.inject; public @interface Inject {}',
            "com/android/systemui/statusbar/phone/ui/StatusBarIconController.java":
                'package com.android.systemui.statusbar.phone.ui; '
                'public interface StatusBarIconController {'
                'void setIcon(String slot, int resourceId, CharSequence contentDescription);'
                'void setIconVisibility(String slot, boolean visible); }',
        }
        for name, content in stubs.items():
            target = output / "stubs" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
        run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp", sdk / "android-system.jar",
            "-d", output / "classes", *sorted((output / "stubs").rglob("*.java")),
            output / "generated/com/android/systemui/res/R.java",
            ROOT / "framework/ims/ImsIconState.java", ROOT / "framework/ims/HeckerImsIcons.java")
    print("IMS status-bar vector resources and actual Android IMS callback API compilation: PASS")
    print("SystemUI/platform Settings dependency stubs used; platform build and phone tests still required")


if __name__ == "__main__":
    main()

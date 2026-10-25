#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Link Android resources and type-check the complete app with locked SDK inputs.

This checks source, not a platform-signed install or a complete ROM build.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def fetch(cache, name, item):
    target = cache / name
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError("Cached SDK input differs: " + name)
        return target
    request = urllib.request.Request(item["url"], headers={"User-Agent": "HeckerSuite-source-check/1"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read()
            break
        except (urllib.error.URLError, TimeoutError) as error:
            # Only transient transport/server failures are retryable. Invalid
            # URLs, authorization errors and checksum mismatches remain fatal.
            if (attempt == 3 or isinstance(error, urllib.error.HTTPError)
                    and error.code not in {408, 429, 500, 502, 503, 504}):
                raise
            print(f"Temporary download failure for {name}; retry {attempt + 1}/3",
                  file=sys.stderr)
            time.sleep(2 ** attempt)
    if item.get("base64"):
        data = base64.b64decode(data, validate=True)
    if hashlib.sha256(data).hexdigest() != item["sha256"]:
        raise ValueError("Downloaded SDK input differs: " + name)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return target


def run(*arguments, env=None):
    subprocess.run([str(argument) for argument in arguments], check=True, env=env)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=Path(os.environ.get(
        "HECKER_SUITE_SDK_CACHE", "/tmp/hecker-suite-sdk-check")))
    args = parser.parse_args()
    cache = args.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / "tests/sdk-inputs.lock.json").read_text())
    for name, item in lock.items(): fetch(cache, name, item)
    for name in ("lottie", "appcompat", "core"):
        with zipfile.ZipFile(cache / (name + ".aar")) as archive:
            (cache / (name + ".jar")).write_bytes(archive.read("classes.jar"))
    with zipfile.ZipFile(cache / "aapt2-linux.jar") as archive:
        aapt = cache / "aapt2"
        aapt.write_bytes(archive.read("aapt2"))
        aapt.chmod(0o755)
    with tempfile.TemporaryDirectory() as temporary:
        output = Path(temporary)
        from prepare import AIDL_NEW, MANIFEST_NEW
        probe = output / "aidl/suite/probe/IAccessProbe.aidl"
        probe.parent.mkdir(parents=True)
        probe.write_text("package suite.probe;\ninterface IAccessProbe {\n"
                         + AIDL_NEW.splitlines()[0] + "\nvoid requestSetup();\n}\n")
        (cache / "aidl").chmod(0o755)
        environment = os.environ.copy()
        environment["LD_LIBRARY_PATH"] = str(cache / "lib64")
        run(cache / "aidl", "--lang=java", "--out=" + str(output / "aidl-generated"),
            "-I", output / "aidl", probe, env=environment)
        for name in ("Bundle", "ParcelFileDescriptor"):
            parcelable = output / "aidl/android/os" / (name + ".aidl")
            parcelable.parent.mkdir(parents=True, exist_ok=True)
            parcelable.write_text("package android.os; parcelable " + name + ";\n")
        for folder, name in (("memory", "IHeckerMemoryService"),):
            source = output / "aidl/android/os" / (name + ".aidl")
            source.write_text((ROOT / "framework" / folder / (name + ".aidl")).read_text())
            run(cache / "aidl", "--lang=java", "--out=" + str(output / "feature-aidl"),
                "-I", output / "aidl", source, env=environment)
        generated = (output / "aidl-generated/suite/probe/IAccessProbe.java").read_text()
        assert "android.Manifest.permission.LOCK_APPS" in generated
        assert "android.Manifest.permission.HECKER_SUITE_APP_LOCK" in generated
        assert "mEnforcer.enforcePermissionAnyOf" in generated
        run(aapt, "compile", "--dir", ROOT / "app/res", "-o", output / "resources.zip")
        run(aapt, "link", "-I", cache / "android.jar", "--manifest", ROOT / "app/AndroidManifest.xml",
            "-A", ROOT / "app/assets", "--auto-add-overlay", "--java", output / "generated", "-o", output / "resources.apk",
            output / "resources.zip")
        # Exercise aapt's real permission-Java generation. Inserting a permission between
        # another permission and its documentation would silently lose @FlaggedApi metadata.
        permission_manifest = output / "permission-manifest.xml"
        permission_manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="android">\n'
            + MANIFEST_NEW.replace('android:featureFlag="android.security.app_lock_apis"', '')
            + '\n<permission android:name="android.permission.LOCK_APPS" '
            + 'android:protectionLevel="internal|role|recents"/><application/></manifest>')
        run(aapt, "link", "-I", cache / "android.jar", "--manifest", permission_manifest,
            "--java", output / "permission-generated", "-o", output / "permission-res.apk")
        permission_java = (output / "permission-generated/android/Manifest.java").read_text()
        assert '@android.annotation.FlaggedApi(android.security.Flags.FLAG_APP_LOCK_APIS)' in permission_java
        assert 'HECKER_SUITE_APP_LOCK="android.permission.HECKER_SUITE_APP_LOCK"' in permission_java
        assert "@hide Hecker's Suite" in permission_java
        (output / "classes").mkdir()
        classpath = os.pathsep.join(str(cache / name) for name in
                                  ("android-system.jar", "lottie.jar", "appcompat.jar", "core.jar"))
        run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp", classpath,
            "-d", output / "classes", output / "generated/org/hecker/suite/R.java",
            *sorted((ROOT / "tests/platform-api-stubs").rglob("*.java")),
            *sorted((ROOT / "app/src").rglob("*.java")),
            ROOT / "framework/sandbox/HeckerSandboxRules.java",
            ROOT / "framework/memory/HeckerMemoryRules.java",
            *sorted((output / "feature-aidl").rglob("*.java")))
        # Compile exactly the Settings UI module without backend classes on its
        # classpath, so a feature Activity reference cannot silently join its UID.
        (output / "settings-ui-classes").mkdir()
        run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp", classpath,
            "-d", output / "settings-ui-classes", output / "generated/org/hecker/suite/R.java",
            *[ROOT / "app/src/org/hecker/suite" / (name + ".java") for name in
              ("SuiteActivity", "SuiteAbout", "SuiteAnimation", "SuiteGreetings", "Ui")])
    print("Complete app Java compilation with locked system SDK and checked platform declarations,")
    print("Android resource linking and native AIDL permission generation: PASS")
    print("Native Settings dashboard compiles independently of the service package: PASS")


if __name__ == "__main__": main()

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile the app-local weather adapter with locked Kotlin/Compose inputs.

This checks the portable adapter, its shared Java client and new resources.
It does not compile the full OmniJaws app or replace an Android/Soong build.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import tempfile
import zipfile
from compile_sdk import fetch, run
from omnijaws_port import omnijaws_copies

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-cache", type=Path, required=True)
    parser.add_argument("--cache", type=Path, default=ROOT / "generated/weather-check")
    args = parser.parse_args()
    sdk, cache = args.sdk_cache.resolve(), args.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / "tests/weather-inputs.lock.json").read_text())
    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(lambda item: fetch(cache, *item), lock.items()))
    compiler, libraries = [], []
    for name, item in lock.items():
        source = cache / name
        if name.endswith(".aar"):
            with zipfile.ZipFile(source) as archive:
                source = cache / (Path(name).stem + ".jar")
                source.write_bytes(archive.read("classes.jar"))
        if item["role"] in ("compiler", "shared"):
            compiler.append(source)
        if item["role"] in ("library", "shared"):
            libraries.append(source)
    with tempfile.TemporaryDirectory(prefix="hecker-weather-") as temporary:
        out = Path(temporary)
        for relative, content in omnijaws_copies().items():
            target = out / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
        manifest = out / "AndroidManifest.xml"
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="org.omnirom.omnijaws"><uses-sdk android:minSdkVersion="31"/>'
                            '<application/></manifest>')
        run(sdk / "aapt2", "compile", "--dir", out / "app/src/main/res", "-o", out / "res.zip")
        run(sdk / "aapt2", "link", "-I", sdk / "android.jar", "--manifest", manifest,
            "--java", out / "generated", "-o", out / "res.apk", out / "res.zip")
        run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp", sdk / "android-system.jar",
            "-d", out / "classes", out / "generated/org/omnirom/omnijaws/R.java",
            *sorted((out / "app/src/main/java").rglob("*.java")))
        run("java", "-cp", os.pathsep.join(map(str, compiler)),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
            "-classpath", os.pathsep.join(map(str, [sdk / "android.jar", out / "classes", *libraries])),
            "-Xplugin=" + str(cache / "kotlin-compose-compiler-plugin-embeddable.jar"),
            "-d", out / "kotlin-classes", *sorted((out / "app/src/main/java").rglob("*.kt")))
    print("Weather Material adapter Kotlin/Compose compilation, shared client Java and resource linking: PASS")
    print("Full OmniJaws/Android compilation and coral weather tests remain required.")


if __name__ == "__main__":
    main()

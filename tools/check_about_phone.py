#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Link/type-check native About Phone and exercise real release/OTA Make rules.

This does not replace a complete Settings/Soong build or phone validation.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]


def run(*args, **kwargs):
    return subprocess.run([str(arg) for arg in args], check=True, **kwargs)


def check_branding(vendor):
    """HeckerOS's early Official setting must not subscribe to PixelOS OTA."""
    with tempfile.TemporaryDirectory(prefix="hecker-branding-") as directory:
        rom = Path(directory)
        (rom / "vendor/hecker/floral").mkdir(parents=True)
        (rom / "vendor/hecker/floral/suite").symlink_to(ROOT, target_is_directory=True)
        (rom / "vendor/custom/config").mkdir(parents=True)
        for name in ("version.mk", "ota.mk"):
            shutil.copyfile(vendor / "config" / name, rom / "vendor/custom/config" / name)
        (rom / "vendor/custom/config/common_full_phone.mk").write_text(
            "include vendor/custom/config/version.mk\ninclude vendor/custom/config/ota.mk\n")
        device = rom / "device/google/coral/coral/device.mk"
        device.parent.mkdir(parents=True)
        device.write_text("# Hardware selection is outside this release-identity check.\n")
        cases = [("hecker_coral", True), ("custom_coral", True), ("custom_coral", False)]
        for product, official in cases:
            text = (f"TARGET_PRODUCT := {product}\nCUSTOM_BUILD := coral\n"
                    "TARGET_BUILD_VARIANT := userdebug\n"
                    f"IS_OFFICIAL := {str(official).lower()}\n")
            if product == "hecker_coral":
                # Evaluate the actual product's ordering; retain only branding inheritance.
                text += ("inherit-product = $(if $(filter vendor/custom/config/common_full_phone.mk "
                         "vendor/hecker/floral/suite/rom/common.mk,$(1)),$(eval include $(1)))\n"
                         "include vendor/hecker/floral/suite/rom/products/hecker_coral.mk\n")
            else:
                text += "include vendor/custom/config/common_full_phone.mk\n"
            text += ("$(info PROPS=$(PRODUCT_PRODUCT_PROPERTIES))\n"
                     "$(info SYSTEM_PROPS=$(PRODUCT_SYSTEM_PROPERTIES) $(PRODUCT_SYSTEM_DEFAULT_PROPERTIES))\n"
                     "$(info PACKAGES=$(PRODUCT_PACKAGES))\nall:\n\t@:\n")
            result = run("make", "--no-print-directory", "-f", "-", "all", cwd=rom,
                         input=text, text=True, capture_output=True)
            lines = result.stdout.splitlines()
            props = next(line[6:].split() for line in lines if line.startswith("PROPS="))
            system_props = next(line[len("SYSTEM_PROPS="):].split() for line in lines
                                if line.startswith("SYSTEM_PROPS="))
            packages = next(line[9:].split() for line in lines if line.startswith("PACKAGES="))
            version = next(value for value in props if value.startswith("ro.custom.version="))
            if product == "hecker_coral":
                assert not system_props, ("Device properties violate generic_system artifact "
                                          "requirements: " + " ".join(system_props))
                assert version.startswith("ro.custom.version=HeckerOS_"), result.stdout
                assert props.count("ro.heckeros.build_type=OFFICIAL") == 1, result.stdout
                assert props.count("ro.heckeros.maintainer=Hecker") == 1, result.stdout
                assert "net.pixelos.build_type=ci" not in props, result.stdout
                assert "Updater" not in packages, result.stdout
                contexts = {}
                for line in (ROOT / "rom/sepolicy/private/property_contexts").read_text().splitlines():
                    fields = line.split()
                    if fields and not fields[0].startswith("#"):
                        contexts[fields[0]] = fields[1:]
                for value in props:
                    key = value.split("=", 1)[0]
                    if key.startswith("ro.heckeros."):
                        kind = "bool" if key == "ro.heckeros.dev_usb_adb" else "string"
                        assert contexts.get(key) == ["u:object_r:build_prop:s0", "exact", kind], key
            else:
                assert version.startswith("ro.custom.version=PixelOS_"), result.stdout
                assert ("net.pixelos.build_type=ci" in props) == official, result.stdout
                assert ("Updater" in packages) == official, result.stdout
        print("Actual product/version/OTA Make rules: HeckerOS Official and PixelOS channels PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-cache", type=Path, required=True)
    parser.add_argument("--vendor-source", type=Path)
    args = parser.parse_args()
    cache = args.sdk_cache.resolve()
    lock = json.loads((ROOT / "tests/sdk-inputs.lock.json").read_text())
    jars = []
    for name in ("preference", "recyclerview", "core"):
        archive = fetch(cache, name + ".aar", lock[name + ".aar"])
        with zipfile.ZipFile(archive) as source:
            jar = cache / (name + ".jar")
            jar.write_bytes(source.read("classes.jar"))
            jars.append(jar)
    with tempfile.TemporaryDirectory(prefix="hecker-about-phone-") as directory:
        output = Path(directory)
        resources = {"values/hecker_about.xml", "values/hecker_strings.xml",
                     "values/hecker_colors.xml", "values-night/hecker_colors.xml",
                     "drawable/hecker_about_badge.xml", "drawable/hecker_card_background.xml",
                     "drawable/hecker_caracal_mark.xml",
                     "layout/hecker_about_phone.xml"}
        resources.update(str(p.relative_to(ROOT / "settings/res"))
                         for p in (ROOT / "settings/res").rglob("hecker_about_*.xml"))
        for name in sorted(resources):
            target = output / "res" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / "settings/res" / name, target)
        manifest = output / "AndroidManifest.xml"
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="com.android.settings"><uses-sdk android:minSdkVersion="29"/>'
                            '<application/></manifest>')
        run(cache / "aapt2", "compile", "--dir", output / "res", "-o", output / "resources.zip")
        run(cache / "aapt2", "link", "-I", cache / "android.jar", "--manifest", manifest,
            "--java", output / "generated", "-o", output / "resources.apk", output / "resources.zip")
        (output / "classes").mkdir()
        # Settings already links Material's AppBar/CollapsingToolbar classes. Keep the
        # host SDK probe narrow; a complete Settings build links their implementation.
        stubs = {
            "com/google/android/material/appbar/AppBarLayout.java": '''package com.google.android.material.appbar;
public class AppBarLayout extends android.widget.FrameLayout {
 public AppBarLayout(android.content.Context c){super(c);} public void setExpanded(boolean expanded,boolean animate){}
 public static class LayoutParams extends android.widget.FrameLayout.LayoutParams {
  public LayoutParams(int w,int h){super(w,h);} public int getScrollFlags(){return 0;}public void setScrollFlags(int flags){}
 }
}''',
            "com/google/android/material/appbar/CollapsingToolbarLayout.java": '''package com.google.android.material.appbar;
public class CollapsingToolbarLayout extends android.widget.FrameLayout {
 public CollapsingToolbarLayout(android.content.Context c){super(c);}
 public boolean isTitleEnabled(){return true;} public void setTitleEnabled(boolean enabled){}
 public android.graphics.drawable.Drawable getContentScrim(){return null;}public android.graphics.drawable.Drawable getStatusBarScrim(){return null;}
 public void setContentScrimColor(int color){}public void setStatusBarScrimColor(int color){}
 public void setContentScrim(android.graphics.drawable.Drawable drawable){}public void setStatusBarScrim(android.graphics.drawable.Drawable drawable){}
}''',
        }
        for name, content in stubs.items():
            p = output / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(content)
        run("java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-cp",
            os.pathsep.join(map(str, [cache / "android-system.jar", *jars])),
            "-d", output / "classes", output / "generated/com/android/settings/R.java",
            *[output / name for name in stubs],
            ROOT / "framework/qs/MediaGlowSpec.java",
            ROOT / "settings/src/com/android/settings/hecker/HeckerPhoneArtView.java",
            ROOT / "settings/src/com/android/settings/hecker/HeckerAboutGlowLayout.java",
            ROOT / "settings/src/com/android/settings/hecker/HeckerAboutPhoneUi.java",
            ROOT / "settings/src/com/android/settings/hecker/HeckerAboutPhoneValues.java",
            ROOT / "settings/src/com/android/settings/hecker/HeckerAboutPhonePreference.java")
        print("Native About Phone resources and Java against Android/AndroidX APIs: PASS")
        vendor = args.vendor_source
        if vendor is None:
            vendor = output / "vendor-custom"
            origin = json.loads((ROOT / "integration/source-revisions.json").read_text())[
                "pixelos_inputs"]["vendor/custom"]
            for name in ("config/version.mk", "config/ota.mk"):
                url = "https://raw.githubusercontent.com/{}/{}/{}".format(
                    origin["repository"], origin["revision"], name)
                with urllib.request.urlopen(url, timeout=45) as response:
                    data = response.read(128 * 1024)
                target = vendor / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
        check_branding(vendor.resolve())


if __name__ == "__main__":
    main()

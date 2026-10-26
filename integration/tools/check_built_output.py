#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check installed camera/apps, Motion Sense and the selected development ADB mode."""

from pathlib import Path
import hashlib
import json
import os
import re
import sys
import zipfile

from camera_install import SERVICE
from floral_target import DEVICE, VENDOR
from google_camera import verify_installed


def check_suite(rom, output):
    suite = output / "system_ext/priv-app/HeckerSuite/HeckerSuite.apk"
    if not suite.is_file() or suite.stat().st_size == 0:
        raise ValueError("Built system_ext image is missing HeckerSuite.apk")
    relative = "configs/privapp-permissions-hecker-suite.xml"
    installed = output / "system_ext/etc/permissions/privapp-permissions-hecker-suite.xml"
    original = rom / "vendor/hecker/floral/suite" / relative
    if not installed.is_file() or installed.read_bytes() != original.read_bytes():
        raise ValueError("Built system_ext image is missing the exact Suite permission allowlist")
    relative = "configs/hecker-suite-freeform.xml"
    installed = output / "system_ext/etc/permissions/hecker-suite-freeform.xml"
    original = rom / "vendor/hecker/floral/suite" / relative
    if not installed.is_file() or installed.read_bytes() != original.read_bytes():
        raise ValueError("Built system_ext image is missing the Suite freeform-window feature")
    controller = output / "system_ext/priv-app/HeckerNetLabController/HeckerNetLabController.apk"
    if not controller.is_file() or controller.stat().st_size == 0:
        raise ValueError("Built system_ext image is missing HeckerNetLabController.apk")
    for name, folder in (("privapp-permissions-hecker-netlab.xml", "permissions"),
                         ("default-permissions-hecker-netlab.xml", "default-permissions")):
        original = rom / "vendor/hecker/floral/suite/configs" / name
        installed = output / "system_ext/etc" / folder / name
        if not original.is_file() or not installed.is_file() or installed.read_bytes() != original.read_bytes():
            raise ValueError("Built system_ext image is missing the exact NetLab permission file: " + name)
    helper = output / "system_ext/bin/hecker-netlab-lan"
    if not helper.is_file() or helper.stat().st_size == 0 or not os.access(helper, os.X_OK):
        raise ValueError("Built system_ext image is missing executable hecker-netlab-lan")
    launcher = output / "system_ext/priv-app/HeckerLauncher/HeckerLauncher.apk"
    if not launcher.is_file() or launcher.stat().st_size == 0:
        raise ValueError("Built system_ext image is missing HeckerLauncher.apk")
    name = "privapp-permissions-hecker-launcher.xml"
    original = rom / "vendor/hecker/floral/suite/configs" / name
    installed = output / "system_ext/etc/permissions" / name
    if not original.is_file() or not installed.is_file() or installed.read_bytes() != original.read_bytes():
        raise ValueError("Built system_ext image is missing the exact Launcher permission allowlist")


def check_motion_sense(output):
    paths = [output / "product/etc/build.prop", output / "product/build.prop"]
    properties = next((path for path in paths if path.is_file()), None)
    if properties is None:
        raise ValueError("Built product image is missing Motion Sense's product properties")
    values = re.findall(r"(?m)^\s*pixel\.oslo\.allowed_override\s*=\s*(\S+)\s*$",
                        properties.read_text())
    if values != ["1"]:
        raise ValueError("Built product image needs exactly one pixel.oslo.allowed_override=1")


def check_bootanimation(rom, output):
    metadata = json.loads((rom / "vendor/hecker/floral/suite/bootanimation/payload.json").read_text())
    installed = output / "product/media/bootanimation.zip"
    if not installed.is_file() or installed.stat().st_size != metadata["size"] \
            or hashlib.sha256(installed.read_bytes()).hexdigest() != metadata["sha256"]:
        raise ValueError("Built product image is missing the exact HeckerOS boot animation")


def check_development_adb(output, expected_mode=None):
    paths = [output / "product/etc/build.prop", output / "product/build.prop"]
    properties = next((path for path in paths if path.is_file()), None)
    if properties is None:
        raise ValueError("Built product image is missing development ADB properties")
    values = {}
    for line in properties.read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            name, value = (part.strip() for part in line.split("=", 1))
            values.setdefault(name, []).append(value)
    modes = values.get("ro.heckeros.dev_usb_adb", [])
    if modes not in (["0"], ["1"]):
        raise ValueError("Built product image needs exactly one ro.heckeros.dev_usb_adb=0 or 1")
    enabled = modes == ["1"]
    if expected_mode is not None:
        if expected_mode.strip() not in ("true", "false"):
            raise ValueError("HECKER_DEV_USB_ADB must be true or false")
        if enabled != (expected_mode.strip() == "true"):
            raise ValueError("Built ADB mode differs from HECKER_DEV_USB_ADB; rebuild product properties")
    secure = "0" if enabled else "1"
    if values.get("ro.adb.secure", []) != [secure]:
        raise ValueError("Built product image needs exactly one ro.adb.secure=" + secure +
                         " for the selected development ADB mode")
    if enabled:
        usb = values.get("persist.sys.usb.config", [])
        if len(usb) != 1 or "adb" not in usb[0].split(","):
            raise ValueError("Development ADB mode needs a USB default containing adb")


def check_librepods(rom, output):
    source = rom / "vendor/hecker/floral/suite/prebuilts/librepods"
    metadata = json.loads((source / "source.json").read_text())
    apk = output / "system_ext/priv-app/HeckerLibrePods/HeckerLibrePods.apk"
    if not apk.is_file():
        raise ValueError("Built image is missing the Hecker LibrePods APK")
    built = source / "prebuilt/HeckerLibrePods.apk"
    record = json.loads((source / "prebuilt/build.json").read_text())
    if record.get("source_commit") != metadata["source_commit"] \
            or record.get("package") != metadata["package"] \
            or not built.is_file() or built.stat().st_size != record.get("size") \
            or hashlib.sha256(built.read_bytes()).hexdigest() != record.get("sha256"):
        raise ValueError("Hecker LibrePods APK source-build record is stale or corrupt")

    def payload(path):
        try:
            with zipfile.ZipFile(path) as archive:
                names = [entry.filename for entry in archive.infolist()
                         if not entry.is_dir() and not entry.filename.startswith("META-INF/")]
                required = {"AndroidManifest.xml", "classes.dex", "lib/arm64-v8a/libbluetooth_socket.so"}
                if len(names) != len(set(names)) or not required <= set(names):
                    raise ValueError("Incomplete or duplicate LibrePods APK entries")
                return {name: hashlib.sha256(archive.read(name)).digest() for name in names}
        except zipfile.BadZipFile as error:
            raise ValueError("Invalid LibrePods APK archive") from error

    # Soong changes the signature, ZIP alignment and compression. Compare every
    # executable/resource entry to the verified source build, not container bytes.
    if payload(apk) != payload(built):
        raise ValueError("Installed LibrePods APK payload differs from the verified source build")
    for folder, name in (("permissions", "privapp-permissions-librepods.xml"),
                         ("default-permissions", "default-permissions-librepods.xml")):
        installed = output / "system_ext/etc" / folder / name
        if not installed.is_file() or installed.read_bytes() != (source / name).read_bytes():
            raise ValueError("Built image is missing the exact LibrePods permissions: " + name)


def check_removed_weather(output):
    for partition in ("system", "system_ext", "product", "vendor"):
        for app_type in ("app", "priv-app"):
            directory = output / partition / app_type / "OmniJaws"
            if directory.exists():
                raise ValueError("Removed OmniJaws installation remains: " + str(directory))


def check(rom, output):
    check_removed_weather(output)
    filename = SERVICE + ".rc"
    original = rom / VENDOR / "proprietary/vendor/etc/init" / filename
    installed = output / "vendor/etc/init" / filename
    if not installed.is_file() or installed.read_bytes() != original.read_bytes():
        raise ValueError(f"Built vendor image is missing {DEVICE.title()}'s exact camera provider init script")
    if not (output / "vendor/bin/hw" / SERVICE).is_file():
        raise ValueError(f"Built vendor image is missing the {DEVICE.title()} camera provider binary")
    if not (output / "vendor/etc/vintf/manifest" / (SERVICE + ".xml")).is_file():
        raise ValueError("Built vendor image is missing the camera VINTF declaration")
    verify_installed(rom, output)
    check_motion_sense(output)
    check_development_adb(output, expected_mode=os.environ.get("HECKER_DEV_USB_ADB"))
    check_suite(rom, output)
    check_bootanimation(rom, output)
    check_librepods(rom, output)
    print("Built output: camera provider binary/init/VINTF, original ten-APK Google Camera set "
          "and Motion Sense country override, Hecker Suite APK/permissions/freeform; "
          "source-built LibrePods APK payload/permissions; exact HeckerOS boot animation; "
          "development ADB mode/authentication; OmniJaws absent: PASS")


if __name__ == "__main__":
    root = Path.cwd()
    try:
        check(root, root / (sys.argv[1] if len(sys.argv) > 1 else "out/target/product/" + DEVICE))
    except (OSError, ValueError) as error:
        print("PixelOS built output:", error, file=sys.stderr)
        sys.exit(1)

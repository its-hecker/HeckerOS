# SPDX-License-Identifier: Apache-2.0
"""Reject the missing runtime init script found in the phone bug report."""

from pathlib import Path
import hashlib
import json
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from floral_target import VENDOR
from check_built_output import check, check_bootanimation, check_development_adb, check_motion_sense, check_suite, check_librepods, SERVICE


class BuiltOutputTests(unittest.TestCase):
    def test_rejects_retired_weather_apk_in_each_install_partition(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for partition in ("system", "system_ext", "product", "vendor"):
                for app_type in ("app", "priv-app"):
                    with self.subTest(partition=partition, app_type=app_type):
                        app = root / partition / app_type / "OmniJaws"
                        app.mkdir(parents=True)
                        apk = app / "OmniJaws.apk"
                        apk.write_bytes(b"stale apk")
                        with self.assertRaisesRegex(ValueError, "Removed OmniJaws"):
                            check(root, root)
                        apk.unlink()
                        app.rmdir()

    def test_selected_camera_needs_its_exact_init_binary_and_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            source = root / (VENDOR + "/proprietary/vendor/etc/init") / (SERVICE + ".rc")
            source.parent.mkdir(parents=True)
            source.write_text("service camera /vendor/bin/hw/" + SERVICE + "\n")
            with self.assertRaisesRegex(ValueError, "init script"):
                check(root, output)
            init = output / "vendor/etc/init" / source.name
            init.parent.mkdir(parents=True)
            init.write_bytes(source.read_bytes())
            with self.assertRaisesRegex(ValueError, "provider binary"):
                check(root, output)
            binary = output / "vendor/bin/hw" / SERVICE
            binary.parent.mkdir(parents=True)
            binary.touch()
            with self.assertRaisesRegex(ValueError, "VINTF"):
                check(root, output)
            manifest = output / "vendor/etc/vintf/manifest" / (SERVICE + ".xml")
            manifest.parent.mkdir(parents=True)
            manifest.touch()
            properties = output / "product/etc/build.prop"
            properties.parent.mkdir(parents=True)
            properties.write_text("pixel.oslo.allowed_override=1\n")
            with patch("check_built_output.verify_installed") as apks, patch("check_built_output.check_suite"), \
                    patch("check_built_output.check_bootanimation"), \
                    patch("check_built_output.check_librepods"), \
                    patch("check_built_output.check_development_adb") as adb:
                check(root, output)
                apks.assert_called_once_with(root, output)
                adb.assert_called_once()
                self.assertEqual((output,), adb.call_args.args)
            init.write_text("service camera /wrong/binary\n")
            with self.assertRaisesRegex(ValueError, "exact camera provider init"):
                check(root, output)

    def test_boot_animation_must_reach_the_product_image_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            metadata = root / "vendor/hecker/floral/suite/bootanimation/payload.json"
            metadata.parent.mkdir(parents=True)
            payload = b"locked animation bytes"
            metadata.write_text(json.dumps({"size": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}))
            installed = output / "product/media/bootanimation.zip"
            with self.assertRaisesRegex(ValueError, "exact HeckerOS boot animation"):
                check_bootanimation(root, output)
            installed.parent.mkdir(parents=True)
            for data in (b"wrong size", b"x" * len(payload)):
                installed.write_bytes(data)
                with self.assertRaisesRegex(ValueError, "exact HeckerOS boot animation"):
                    check_bootanimation(root, output)
            installed.write_bytes(payload)
            check_bootanimation(root, output)

    def test_motion_sense_requires_the_country_override_in_built_product_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with self.assertRaisesRegex(ValueError, "missing Motion Sense"):
                check_motion_sense(output)
            properties = output / "product/etc/build.prop"
            properties.parent.mkdir(parents=True)
            for value in ("", "pixel.oslo.allowed_override=0\n",
                          "pixel.oslo.allowed_override=1\npixel.oslo.allowed_override=1\n"):
                with self.subTest(properties=value):
                    properties.write_text(value)
                    with self.assertRaisesRegex(ValueError, "exactly one"):
                        check_motion_sense(output)
            properties.write_text("# pixel.oslo.allowed_override=0\npixel.oslo.allowed_override=1\n")
            check_motion_sense(output)
            legacy = output / "product/build.prop"
            properties.rename(legacy)
            check_motion_sense(output)

    def test_development_adb_requires_an_unambiguous_built_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with self.assertRaisesRegex(ValueError, "missing development ADB"):
                check_development_adb(output)
            properties = output / "product/etc/build.prop"
            properties.parent.mkdir(parents=True)
            for value in ("", "ro.heckeros.dev_usb_adb=true\n",
                          "ro.heckeros.dev_usb_adb=1\nro.heckeros.dev_usb_adb=1\n"):
                with self.subTest(properties=value):
                    properties.write_text(value)
                    with self.assertRaisesRegex(ValueError, "exactly one ro.heckeros"):
                        check_development_adb(output)

    def test_development_adb_checks_authentication_and_usb_defaults(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            properties = output / "product/etc/build.prop"
            properties.parent.mkdir(parents=True)
            for mode, secure in (("1", "0"), ("0", "1")):
                base = "ro.heckeros.dev_usb_adb=" + mode + "\n"
                usb = "persist.sys.usb.config=adb\n" if mode == "1" else ""
                for value in ("", "ro.adb.secure=" + ("1" if secure == "0" else "0") + "\n",
                              "ro.adb.secure=" + secure + "\nro.adb.secure=" + secure + "\n"):
                    with self.subTest(mode=mode, properties=value):
                        properties.write_text(base + value + usb)
                        with self.assertRaisesRegex(ValueError, "exactly one ro.adb.secure=" + secure):
                            check_development_adb(output)
                properties.write_text(base + "ro.adb.secure=" + secure + "\n" + usb)
                check_development_adb(output)
            base = "ro.heckeros.dev_usb_adb=1\nro.adb.secure=0\n"
            for usb in ("", "persist.sys.usb.config=mtp\n",
                        "persist.sys.usb.config=adb\npersist.sys.usb.config=adb\n"):
                properties.write_text(base + usb)
                with self.assertRaisesRegex(ValueError, "USB default containing adb"):
                    check_development_adb(output)
            properties.write_text(base + "persist.sys.usb.config=mtp,adb\n")
            check_development_adb(output)
            properties.rename(output / "product/build.prop")
            check_development_adb(output)

    def test_development_adb_detects_a_final_switch_that_did_not_reach_the_image(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            properties = output / "product/etc/build.prop"
            properties.parent.mkdir(parents=True)
            for mode, secure, expected in (("1", "0", "true"), ("0", "1", "false")):
                properties.write_text("ro.heckeros.dev_usb_adb=" + mode + "\nro.adb.secure=" + secure +
                                      "\npersist.sys.usb.config=adb\n")
                check_development_adb(output, expected)
                with self.assertRaisesRegex(ValueError, "differs from HECKER_DEV_USB_ADB"):
                    check_development_adb(output, "false" if expected == "true" else "true")
                with self.assertRaisesRegex(ValueError, "must be true or false"):
                    check_development_adb(output, "1")

    def test_suite_requires_installed_apk_and_matching_permission_xml(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            with self.assertRaisesRegex(ValueError, "HeckerSuite.apk"):
                check_suite(root, output)
            apk = output / "system_ext/priv-app/HeckerSuite/HeckerSuite.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"suite test artifact")
            with self.assertRaisesRegex(ValueError, "Suite permission"):
                check_suite(root, output)
            original = root / "vendor/hecker/floral/suite/configs/privapp-permissions-hecker-suite.xml"
            original.parent.mkdir(parents=True)
            original.write_text("<permissions />")
            installed = output / "system_ext/etc/permissions/privapp-permissions-hecker-suite.xml"
            installed.parent.mkdir(parents=True)
            installed.write_text("<wrong />")
            with self.assertRaisesRegex(ValueError, "Suite permission"):
                check_suite(root, output)
            installed.write_bytes(original.read_bytes())
            with self.assertRaisesRegex(ValueError, "freeform-window"):
                check_suite(root, output)
            feature = root / "vendor/hecker/floral/suite/configs/hecker-suite-freeform.xml"
            feature.write_text('<permissions><feature name="android.software.freeform_window_management" /></permissions>')
            installed_feature = output / "system_ext/etc/permissions/hecker-suite-freeform.xml"
            installed_feature.write_bytes(feature.read_bytes())
            with self.assertRaisesRegex(ValueError, "HeckerNetLabController.apk"):
                check_suite(root, output)
            controller = output / "system_ext/priv-app/HeckerNetLabController/HeckerNetLabController.apk"
            controller.parent.mkdir(parents=True)
            controller.write_bytes(b"netlab controller fixture")
            for name, folder in (("privapp-permissions-hecker-netlab.xml", "permissions"),
                                 ("default-permissions-hecker-netlab.xml", "default-permissions")):
                with self.assertRaisesRegex(ValueError, "NetLab permission"):
                    check_suite(root, output)
                original_netlab = original.parent / name
                original_netlab.write_text("<permissions />")
                installed_netlab = output / "system_ext/etc" / folder / name
                installed_netlab.parent.mkdir(parents=True, exist_ok=True)
                installed_netlab.write_text("<wrong />")
                with self.assertRaisesRegex(ValueError, "NetLab permission"):
                    check_suite(root, output)
                installed_netlab.write_bytes(original_netlab.read_bytes())
            with self.assertRaisesRegex(ValueError, "hecker-netlab-lan"):
                check_suite(root, output)
            helper = output / "system_ext/bin/hecker-netlab-lan"
            helper.parent.mkdir(parents=True)
            helper.write_bytes(b"native helper fixture")
            with self.assertRaisesRegex(ValueError, "hecker-netlab-lan"):
                check_suite(root, output)
            helper.chmod(0o755)
            with self.assertRaisesRegex(ValueError, "HeckerLauncher.apk"):
                check_suite(root, output)
            launcher = output / "system_ext/priv-app/HeckerLauncher/HeckerLauncher.apk"
            launcher.parent.mkdir(parents=True)
            launcher.write_bytes(b"launcher fixture")
            with self.assertRaisesRegex(ValueError, "Launcher permission"):
                check_suite(root, output)
            launcher_source = original.parent / "privapp-permissions-hecker-launcher.xml"
            launcher_source.write_text("<permissions />")
            misplaced = output / "product/etc/permissions/privapp-permissions-hecker-launcher.xml"
            misplaced.parent.mkdir(parents=True)
            misplaced.write_bytes(launcher_source.read_bytes())
            with self.assertRaisesRegex(ValueError, "Launcher permission"):
                check_suite(root, output)
            launcher_allowlist = output / "system_ext/etc/permissions/privapp-permissions-hecker-launcher.xml"
            launcher_allowlist.write_text("<wrong />")
            with self.assertRaisesRegex(ValueError, "Launcher permission"):
                check_suite(root, output)
            launcher_allowlist.write_bytes(launcher_source.read_bytes())
            check_suite(root, output)

    def test_librepods_accepts_rom_signing_but_requires_source_payload_and_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            source = root / "vendor/hecker/floral/suite/prebuilts/librepods"
            source.mkdir(parents=True)
            (source / "prebuilt").mkdir()
            built = source / "prebuilt/HeckerLibrePods.apk"
            entries = {"AndroidManifest.xml": b"org.hecker.librepods manifest",
                       "classes.dex": b"headphone routing and codec",
                       "lib/arm64-v8a/libbluetooth_socket.so": b"AirPods JNI",
                       "resources.arsc": b"animated companion resources"}
            with zipfile.ZipFile(built, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for name, content in entries.items(): archive.writestr(name, content)
            data = built.read_bytes()
            identity = {"source_commit": "a" * 40, "package": "org.hecker.librepods"}
            (source / "source.json").write_text(json.dumps(identity))
            record = dict(identity, size=len(data), sha256=hashlib.sha256(data).hexdigest())
            (source / "prebuilt/build.json").write_text(json.dumps(record))
            with self.assertRaisesRegex(ValueError, "LibrePods APK"):
                check_librepods(root, output)
            apk = output / "system_ext/priv-app/HeckerLibrePods/HeckerLibrePods.apk"
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b"wrong APK bytes")
            with self.assertRaisesRegex(ValueError, "LibrePods APK"):
                check_librepods(root, output)
            with zipfile.ZipFile(apk, "w", compression=zipfile.ZIP_STORED) as archive:
                for name, content in entries.items(): archive.writestr(name, content)
                archive.writestr("META-INF/PLATFORM.SF", b"ROM signature")
            self.assertNotEqual(data, apk.read_bytes())
            for folder, name in (("permissions", "privapp-permissions-librepods.xml"),
                                 ("default-permissions", "default-permissions-librepods.xml")):
                (source / name).write_text('<permissions />')
                with self.assertRaisesRegex(ValueError, "LibrePods permissions"):
                    check_librepods(root, output)
                installed = output / "system_ext/etc" / folder / name
                installed.parent.mkdir(parents=True, exist_ok=True)
                installed.write_bytes((source / name).read_bytes())
            check_librepods(root, output)
            with zipfile.ZipFile(apk, "w") as archive:
                for name, content in entries.items():
                    archive.writestr(name, b"stale code" if name == "classes.dex" else content)
            with self.assertRaisesRegex(ValueError, "LibrePods APK"):
                check_librepods(root, output)
            apk.write_bytes(data)
            for key, value in (("source_commit", "b" * 40), ("package", "me.kavishdevar.librepods"),
                               ("sha256", "0" * 64)):
                with self.subTest(key=key):
                    (source / "prebuilt/build.json").write_text(json.dumps(dict(record, **{key: value})))
                    with self.assertRaisesRegex(ValueError, "source-build record"):
                        check_librepods(root, output)
            (source / "prebuilt/build.json").write_text(json.dumps(record))
            with zipfile.ZipFile(apk, "w") as archive:
                for name, content in entries.items():
                    if name != "lib/arm64-v8a/libbluetooth_socket.so": archive.writestr(name, content)
            with self.assertRaisesRegex(ValueError, "LibrePods APK"):
                check_librepods(root, output)


if __name__ == "__main__":
    unittest.main()

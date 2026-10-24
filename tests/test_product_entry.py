# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ProductEntryTests(unittest.TestCase):
    def test_rom_removes_inherited_omnijaws_and_excludes_stale_source(self):
        source = "\n".join([
            "TARGET_BUILD_VARIANT := userdebug",
            "HECKER_DEV_USB_ADB := true",
            "PRODUCT_PACKAGES := OmniJaws ExistingApp HeckerLauncher Launcher3 Launcher3QuickStep HeckerLauncherRecentsOverlay",
            "inherit-product = $(eval include $(1))",
            "include " + str(ROOT / "rom/common.mk"),
            "$(info PACKAGES=$(PRODUCT_PACKAGES))",
            "$(info EXCLUSIONS=$(PRODUCT_SOURCE_ROOT_DIRS))",
            "$(info ADB=$(filter ro.adb.secure=% persist.sys.usb.config=%,$(PRODUCT_PRODUCT_PROPERTIES)))",
            "all:", "\t@:", ""])
        with tempfile.TemporaryDirectory() as temp:
            rom = Path(temp)
            suite = rom / "vendor/hecker/floral/suite"
            suite.parent.mkdir(parents=True)
            suite.symlink_to(ROOT, target_is_directory=True)
            result = subprocess.run(["make", "--no-print-directory", "-f", "-", "all"],
                                    cwd=rom, input=source, text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        packages = next(line.removeprefix("PACKAGES=").split() for line in result.stdout.splitlines()
                        if line.startswith("PACKAGES="))
        self.assertTrue({"ExistingApp", "HeckerSuite", "HeckerSystemUIOverlay"} <= set(packages))
        self.assertFalse({"HeckerLauncher", "Launcher3", "Launcher3QuickStep", "HeckerLauncherRecentsOverlay"} & set(packages))
        self.assertIn("EXCLUSIONS=-packages/services/OmniJaws", result.stdout)
        self.assertNotIn("-packages/apps/Launcher3", result.stdout)
        self.assertIn("ADB=ro.adb.secure=0 persist.sys.usb.config=adb", result.stdout)

    def test_release_map_is_available_before_android_defines_my_dir(self):
        with tempfile.TemporaryDirectory() as temp:
            rom = Path(temp)
            suite = rom / "vendor/hecker/floral/suite"
            suite.mkdir(parents=True)
            (suite / "suite.mk").write_bytes((ROOT / "suite.mk").read_bytes())
            source = "\n".join([
                "include vendor/hecker/floral/suite/suite.mk",
                "$(info MAPS=$(PRODUCT_RELEASE_CONFIG_MAPS))",
                "$(info PACKAGES=$(PRODUCT_PACKAGES))",
                "all:", "\t@:", ""])
            result = subprocess.run(["make", "--no-print-directory", "-f", "-", "all"],
                                    cwd=rom, input=source, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("MAPS=vendor/hecker/floral/suite/release/release_config_map.textproto", result.stdout)
            self.assertIn("PACKAGES=HeckerSuite", result.stdout)


if __name__ == "__main__": unittest.main()

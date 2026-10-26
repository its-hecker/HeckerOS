# SPDX-License-Identifier: Apache-2.0
"""Regress Flame builds accidentally preparing Coral firmware or overlays."""

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


INTEGRATION = Path(__file__).resolve().parents[1]
SUITE = Path(os.environ.get("HECKER_SUITE_ROOT", INTEGRATION.parent))
TOOLS = INTEGRATION / "tools"


class FloralTargetTests(unittest.TestCase):
    def probe(self, device, code):
        env = dict(os.environ, HECKER_DEVICE=device, PYTHONPATH=str(TOOLS))
        return subprocess.run([sys.executable, "-c", code], env=env,
                              capture_output=True, text=True)

    def test_target_routes_vendor_product_and_last_overlay_together(self):
        for device in ("coral", "flame"):
            with self.subTest(device=device):
                result = self.probe(device, """
import json
from floral_target import DEVICE, VENDOR, PRODUCT
from prepare import OVERLAY_ROOTS
print(json.dumps([DEVICE, VENDOR, PRODUCT, OVERLAY_ROOTS[-1]]))
""")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(result.stdout), [
                    device, "vendor/google/" + device,
                    "vendor/hecker/floral/suite/rom/products/hecker_" + device + ".mk",
                    "device/google/coral/" + device + "/overlay"])

    def test_coral_manifest_is_rejected_for_a_flame_build(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "floral.xml").write_text(
                '<manifest><project path="vendor/google/coral" /></manifest>')
            result = self.probe("flame", "from pathlib import Path; from validate import manifest; "
                                + "manifest(Path(" + repr(temporary) + "))")
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("does not match the manifest vendor", result.stderr)

    def test_invalid_device_fails_without_preparing_any_sources(self):
        result = self.probe("unknown", "import prepare")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("must be coral or flame", result.stderr)

    @unittest.skipUnless((SUITE / "rom/products/hecker_flame.mk").is_file(),
                         "Product inheritance is checked in the shared HeckerOS repository")
    def test_both_products_resolve_the_same_features_and_distinct_hardware(self):
        features = {}
        for device, model, width, height in (("coral", "Pixel 4 XL", "1440", "3040"),
                                             ("flame", "Pixel 4", "1080", "2280")):
            with self.subTest(device=device), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                # Isolate Android's surrounding build graph while evaluating the
                # real product file and its direct includes with GNU Make.
                hardware = root / "device/google/coral" / device / "device.mk"
                hardware.parent.mkdir(parents=True)
                hardware.touch()
                capture = root / "vendor/hecker/floral/suite/rom/boot_diagnostics/boot_capture.mk"
                capture.parent.mkdir(parents=True)
                capture.write_bytes((SUITE / "rom/boot_diagnostics/boot_capture.mk").read_bytes())
                product = SUITE / "rom/products" / ("hecker_" + device + ".mk")
                make = ("define inherit-product\n$(info INHERIT=$(1))\nendef\n"
                        + "include " + str(product) + "\n"
                        + "$(info ID=$(PRODUCT_NAME)|$(PRODUCT_DEVICE)|$(PRODUCT_MODEL)|$(CUSTOM_BUILD))\n"
                        + "$(info SCREEN=$(TARGET_SCREEN_WIDTH)x$(TARGET_SCREEN_HEIGHT))\n"
                        + "$(info FACE=$(TARGET_FACE_UNLOCK_SUPPORTED))\n"
                        + "$(info PACKAGES=$(PRODUCT_PACKAGES))\nall:;@:\n")
                result = subprocess.run(["make", "--no-print-directory", "-f", "-"],
                                        input=make, text=True, capture_output=True, cwd=root)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(f"ID=hecker_{device}|{device}|{model}|{device}", result.stdout)
                self.assertIn(f"SCREEN={width}x{height}", result.stdout)
                self.assertIn("FACE=false", result.stdout)
                self.assertIn("HeckerLibrePods", result.stdout)
                inherited = [line.removeprefix("INHERIT=").strip() for line in result.stdout.splitlines()
                             if line.startswith("INHERIT=")]
                self.assertIn(f"vendor/google/{device}/{device}-vendor.mk", inherited)
                features[device] = [p for p in inherited if p.startswith("vendor/hecker/")]
        self.assertEqual(features["coral"], features["flame"])
        self.assertEqual(len(features["flame"]), 4)
        self.assertTrue(features["flame"][-1].endswith("suite/rom/common.mk"))


if __name__ == "__main__":
    unittest.main()

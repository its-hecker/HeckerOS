#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Ensure About Phone drawable references are installed into native Settings."""
import json
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


class AboutPhoneResourceCopyTest(unittest.TestCase):
    def test_all_layout_drawables_are_copied(self):
        layout = (ROOT / "settings/res/layout/hecker_about_phone.xml").read_text()
        names = set(re.findall(r"@drawable/(hecker_[a-z0-9_]+)", layout))
        self.assertTrue(names, "Expected native About Phone drawable references")
        copies = json.loads((ROOT / "framework/customization-copies.json").read_text())
        for name in sorted(names):
            with self.subTest(drawable=name):
                assets = [(target, source) for target, source in copies.items()
                          if target.startswith("packages/apps/Settings/res/drawable")
                          and Path(target).stem == name
                          and Path(target).suffix in {".xml", ".png", ".webp"}]
                self.assertTrue(assets, f"Missing native Settings copy for {name}")
                for target, source in assets:
                    expected = "settings/res/" + target.removeprefix(
                        "packages/apps/Settings/res/")
                    self.assertEqual(source, expected)
                    self.assertTrue((ROOT / source).is_file(),
                                    f"Missing source for {name}: {source}")


if __name__ == "__main__":
    unittest.main()

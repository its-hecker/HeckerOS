#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Ensure every HeckerOS About Phone drawable is staged into native Settings."""
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
        resources = ROOT / "settings/res"
        for name in sorted(names):
            with self.subTest(drawable=name):
                assets = sorted(path for path in resources.rglob(name + ".*")
                                if path.suffix in {".xml", ".png", ".webp"})
                self.assertEqual(len(assets), 1,
                                 f"Expected one source asset for {name}, got {assets}")
                relative = assets[0].relative_to(resources).as_posix()
                target = "packages/apps/Settings/res/" + relative
                self.assertEqual(copies.get(target), "settings/res/" + relative,
                                 f"{name} is referenced by the layout but not installed")


if __name__ == "__main__":
    unittest.main()

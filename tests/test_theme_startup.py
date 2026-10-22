# SPDX-License-Identifier: Apache-2.0
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import customization_port
from prepare import replace_guarded


class ThemeStartupTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.rom = self.root / "rom"
        suite = self.root / "suite"
        (suite / "framework").mkdir(parents=True)
        self.pairs = json.loads((ROOT / "framework/customization-patches.json").read_text())[
            customization_port.SYSTEM_SERVER]
        (suite / "framework/customization-patches.json").write_text(json.dumps({
            customization_port.SYSTEM_SERVER: self.pairs}))
        (suite / "framework/customization-copies.json").write_text("{}")
        self.path = self.rom / customization_port.SYSTEM_SERVER
        self.path.parent.mkdir(parents=True)
        marker = self.rom / "frameworks/base/core/java/android/content/res/Resources.java"
        marker.parent.mkdir(parents=True)
        marker.touch()
        # The selected Android 17 SystemServer calls phase 500 and 550 in
        # startOtherServices, then seals services in startApexServices.
        self.original = '''        t.traceBegin("StartBootPhaseSystemServicesReady");
        mSystemServiceManager.startBootPhase(t, SystemService.PHASE_SYSTEM_SERVICES_READY);
        mSystemServiceManager.startBootPhase(t, SystemService.PHASE_ACTIVITY_MANAGER_READY);
        mSystemServiceManager.sealStartedServices();
'''
        self.path.write_text(self.original)
        context = patch.object(customization_port, "ROOT", suite)
        context.start()
        self.addCleanup(context.stop)

    def apply(self, revert=False):
        changes = customization_port.customization_plan(self.rom, replace_guarded, revert)
        for path, value in changes.items():
            path.write_bytes(value if isinstance(value, bytes) else value.encode())
        return changes

    def test_fresh_start_is_early_and_idempotent_and_reverts_exactly(self):
        self.apply()
        customization_port.verify_theme_service_startup(self.path.read_text())
        self.assertFalse(self.apply())
        self.apply(True)
        self.assertEqual(self.original, self.path.read_text())

    def test_published_late_start_is_rejected_and_upgrades_in_place(self):
        late = self.original.replace(self.pairs[0]["old"], self.pairs[0]["previous"][0])
        self.path.write_text(late)
        with self.assertRaisesRegex(ValueError, "late start"):
            customization_port.verify_theme_service_startup(late)
        self.apply()
        customization_port.verify_theme_service_startup(self.path.read_text())
        self.assertFalse(self.apply())
        self.apply(True)
        self.assertEqual(self.original, self.path.read_text())

    def test_unrecognized_late_start_edit_is_preserved(self):
        late = self.pairs[0]["previous"][0].replace('"StartHeckerThemeEngine"', '"PrivateThemeStart"')
        self.path.write_text(self.original.replace(self.pairs[0]["old"], late))
        before = self.path.read_bytes()
        with self.assertRaises(ValueError):
            self.apply()
        self.assertEqual(before, self.path.read_bytes())


if __name__ == "__main__":
    unittest.main()

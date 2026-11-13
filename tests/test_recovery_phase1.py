# SPDX-License-Identifier: Apache-2.0
"""Exercise the actual pinned PixelOS recovery UI, not mocked guessed anchors."""
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from recovery_phase1 import ROOT_FILES, recovery_phase1_plan


class RecoveryPhase1Tests(unittest.TestCase):
    def setUp(self):
        source_dir = os.environ.get("HECKER_RECOVERY_BASELINE", "")
        if not source_dir:
            self.skipTest("Set HECKER_RECOVERY_BASELINE to the pinned PixelOS recovery checkout")
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.recovery = self.root / "bootable/recovery"
        for relative in ROOT_FILES:
            original = Path(source_dir) / relative
            target = self.recovery / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(original, target)
        self.originals = {p: (self.recovery / p).read_bytes() for p in ROOT_FILES}

    def apply(self, revert=False):
        changes = recovery_phase1_plan(self.root, revert)
        for path, content in changes.items():
            path.write_text(content)
        return changes

    def test_roundtrip_idempotence_and_preserves_platform_surfaces(self):
        changes = self.apply()
        self.assertEqual(len(changes), 2)
        header = (self.recovery / "recovery_ui/include/recovery_ui/screen_ui.h").read_text()
        source = (self.recovery / "recovery_ui/screen_ui.cpp").read_text()
        self.assertIn("void draw_hecker_install_locked();", header)
        self.assertIn("ScreenRecoveryUI::draw_hecker_install_locked()", source)
        self.assertIn("if (default_logo)", source)   # Branding is kept.
        self.assertIn("progressScopeStart + progress * progressScopeSize", source)
        self.assertIn("Now send the package you want to apply", source)
        self.assertIn("Install from ADB complete", source)
        self.assertEqual(self.apply(), {})  # Re-running preparation does not duplicate.
        self.assertEqual(len(self.apply(revert=True)), 2)
        for name, baseline in self.originals.items():
            self.assertEqual((self.recovery / name).read_bytes(), baseline)
        self.assertEqual(self.apply(revert=True), {})

    def test_unreviewed_upstream_source_fails_closed(self):
        target = self.recovery / "recovery_ui/screen_ui.cpp"
        target.write_text(target.read_text().replace(
            "void ScreenRecoveryUI::draw_screen_locked() {",
            "void ScreenRecoveryUI::draw_screen_evolved_locked() {"))
        with self.assertRaisesRegex(ValueError, "Unrecognized recovery UI source"):
            recovery_phase1_plan(self.root)


if __name__ == "__main__":
    unittest.main()

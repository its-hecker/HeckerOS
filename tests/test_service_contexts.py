# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from service_contexts import CANONICAL, CONTEXT, game_service_plan


class ServiceContextsTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.rom = Path(temporary.name)
        self.canonical = self.write(CANONICAL, "hecker_game " + CONTEXT + "\n")

    def write(self, relative, content):
        path = self.rom / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return path

    def test_migrates_the_build_conflict_without_changing_other_services(self):
        before = "# local policy\nactivity u:object_r:activity_service:s0\n"
        legacy = self.write("system/sepolicy/private/service_contexts", before +
                            "  hecker_game\tu:object_r:activity_service:s0 # obsolete fallback\n")
        changes = game_service_plan(self.rom)
        self.assertEqual(changes, {legacy: before})
        self.assertIn("obsolete fallback", legacy.read_text())  # Planning performs no writes.
        for path, content in changes.items():
            path.write_text(content)
        self.assertEqual(game_service_plan(self.rom), {})
        self.assertEqual(self.canonical.read_text(), "hecker_game " + CONTEXT + "\n")

    def test_finds_device_and_vendor_fallbacks_and_preserves_commented_examples(self):
        for relative in ("device/google/coral/sepolicy/private/service_contexts",
                         "vendor/custom/sepolicy/private/service_contexts"):
            self.write(relative, "# hecker_game u:object_r:activity_service:s0\n"
                       "hecker_game u:object_r:activity_service:s0\n")
        self.assertEqual(set(game_service_plan(self.rom).values()),
                         {"# hecker_game u:object_r:activity_service:s0\n"})

    def test_preserves_unknown_policy_and_requires_the_dedicated_owner(self):
        foreign = self.write("system/sepolicy/private/service_contexts",
                             "hecker_game u:object_r:custom_game_service:s0\n")
        with self.assertRaisesRegex(ValueError, "Unfamiliar gaming"):
            game_service_plan(self.rom)
        self.assertIn("custom_game_service", foreign.read_text())
        self.canonical.write_text("hecker_game u:object_r:activity_service:s0\n")
        with self.assertRaisesRegex(ValueError, "exactly one dedicated"):
            game_service_plan(self.rom)


if __name__ == "__main__":
    unittest.main()

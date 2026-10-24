# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from service_contexts import CANONICAL, CONTEXT, game_service_plan, repair


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

    def test_scans_hardware_frameworks_and_policy_templates(self):
        relatives = ("hardware/google/pixel-sepolicy/private/service_contexts",
                     "frameworks/custom/sepolicy/plat_service_contexts.in",
                     "system/other/sepolicy/service_contexts.m4")
        paths = {self.write(name, "hecker_game u:object_r:activity_service:s0\n")
                 for name in relatives}
        self.assertEqual(set(game_service_plan(self.rom)), paths)

    def test_ignores_build_outputs_repo_copies_and_backups(self):
        for name in ("out/soong/merged/service_contexts",
                     "out/hecker-service-context-backups/old/service_contexts",
                     ".repo/projects/old/service_contexts",
                     ".git/old/service_contexts"):
            self.write(name, "hecker_game u:object_r:foreign_service:s0\n")
        self.assertEqual(game_service_plan(self.rom), {})

    def test_checks_platform_policy_even_when_the_project_directory_is_linked(self):
        outside = tempfile.TemporaryDirectory()
        self.addCleanup(outside.cleanup)
        project = Path(outside.name)
        (project / "private").mkdir()
        source = project / "private/service_contexts"
        source.write_text("activity u:object_r:activity_service:s0\n"
                          "hecker_game                               u:object_r:activity_service:s0\n")
        (self.rom / "system").mkdir()
        (self.rom / "system/sepolicy").symlink_to(project, target_is_directory=True)
        changes = game_service_plan(self.rom)
        self.assertEqual(changes, {self.rom / "system/sepolicy/private/service_contexts":
                                  "activity u:object_r:activity_service:s0\n"})
        repair(self.rom, apply=True)
        self.assertEqual(source.read_text(), "activity u:object_r:activity_service:s0\n")

    def test_repair_backs_up_the_source_and_clears_only_cached_context_modules(self):
        before = "activity u:object_r:activity_service:s0\nhecker_game u:object_r:activity_service:s0\n"
        legacy = self.write("hardware/google/pixel-sepolicy/private/service_contexts", before)
        base = "out/soong/.intermediates/system/sepolicy/"
        merged = self.write(base + "contexts/merged_service_contexts/android_common/merged_service_contexts", before)
        test = self.write(base + "contexts/merged_service_contexts_test/android_common/timestamp", "old")
        recovery = self.write(base + "contexts/plat_service_contexts.recovery/android_recovery_common/coral/plat_service_contexts", before)
        retained = [self.write(base + "precompiled_sepolicy/android_common/policy", "keep"),
                    self.write(base + "contexts/plat_property_contexts/android_common/context", "keep"),
                    self.write("out/target/product/coral/system.img", "keep")]
        repair(self.rom, apply=True, clear_cache=True)
        self.assertEqual(legacy.read_text(), "activity u:object_r:activity_service:s0\n")
        self.assertFalse(merged.exists())
        self.assertFalse(test.exists())
        self.assertFalse(recovery.exists())
        self.assertTrue(all(path.read_text() == "keep" for path in retained))
        backup = list((self.rom / "out/hecker-service-context-backups").rglob("service_contexts"))
        self.assertEqual(len(backup), 1)
        self.assertEqual(backup[0].read_text(), before)
        self.assertEqual(game_service_plan(self.rom), {})
        repair(self.rom)

    def test_check_refuses_unrepaired_inputs_and_a_missing_suite(self):
        legacy = self.write("system/sepolicy/private/service_contexts",
                            "hecker_game u:object_r:activity_service:s0\n")
        with self.assertRaisesRegex(ValueError, "rerun this tool with --apply"):
            repair(self.rom)
        self.assertIn("activity_service", legacy.read_text())
        self.assertFalse((self.rom / "out").exists())
        self.canonical.unlink()
        with self.assertRaisesRegex(ValueError, "sync vendor/hecker/floral/suite"):
            repair(self.rom, apply=True)

    def test_cli_discards_a_stale_merge_even_when_the_source_is_already_correct(self):
        cached = self.write(
            "out/soong/.intermediates/system/sepolicy/contexts/merged_service_contexts/android_common/merged_service_contexts",
            "hecker_game " + CONTEXT + "\nhecker_game u:object_r:activity_service:s0\n")
        source = self.canonical.read_bytes()
        result = subprocess.run([sys.executable,
            str(Path(__file__).resolve().parents[1] / "tools/service_contexts.py"),
            "--rom-root", str(self.rom), "--apply", "--clear-cache"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("platform-source scan v3", result.stdout)
        self.assertFalse(cached.exists())
        self.assertEqual(self.canonical.read_bytes(), source)
        self.assertFalse((self.rom / "out/hecker-service-context-backups").exists())


if __name__ == "__main__":
    unittest.main()

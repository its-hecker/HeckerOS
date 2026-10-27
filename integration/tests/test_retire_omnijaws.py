# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from retire_omnijaws import retire


class RetireOmniJawsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.rom = Path(self.temporary.name)
        (self.rom / ".repo").mkdir()
        (self.rom / "build").mkdir()
        (self.rom / "build/envsetup.sh").touch()
        self.source = self.rom / "packages/services/OmniJaws"
        self.source.parent.mkdir(parents=True)

    def test_preserves_adapted_sources_and_build_output_and_can_run_again(self):
        self.source.mkdir()
        (self.source / "Android.bp").write_text("local adapted module\n")
        (self.source / "local-edit.txt").write_bytes(b"uncommitted user edit\x00")
        output = self.rom / "out/soong/build.ninja"
        output.parent.mkdir(parents=True)
        output.write_text("keep compiled output\n")
        destination = retire(self.rom)
        self.assertFalse(self.source.exists())
        self.assertEqual((destination / "Android.bp").read_text(), "local adapted module\n")
        self.assertEqual((destination / "local-edit.txt").read_bytes(), b"uncommitted user edit\x00")
        self.assertEqual(output.read_text(), "keep compiled output\n")
        self.assertIsNone(retire(self.rom))
        self.assertEqual(len(list(destination.parent.parent.iterdir())), 1)

    def test_rejects_source_symlink_without_moving_target(self):
        with tempfile.TemporaryDirectory() as outside:
            target = Path(outside)
            (target / "keep.txt").write_text("keep")
            self.source.symlink_to(target, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, "source symlink"):
                retire(self.rom)
            self.assertTrue(self.source.is_symlink())
            self.assertEqual((target / "keep.txt").read_text(), "keep")

    def test_requires_an_android_source_root(self):
        (self.rom / "build/envsetup.sh").unlink()
        with self.assertRaisesRegex(ValueError, "Android source root"):
            retire(self.rom)


if __name__ == "__main__":
    unittest.main()

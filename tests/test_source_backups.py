# SPDX-License-Identifier: Apache-2.0
"""The Suite must not leave duplicate Blueprint modules under inactive out/."""
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from prepare import source_backup_root


class SourceBackupDirectoryTests(unittest.TestCase):
    def test_default_matches_standard_android_out(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.dict(os.environ, {}, clear=True):
                self.assertEqual(source_backup_root(Path(root)),
                                 Path(root) / "out/hecker-suite-source-backups")

    def test_nonksu_uses_active_out_dir(self):
        with tempfile.TemporaryDirectory() as root:
            with patch.dict(os.environ, {"OUT_DIR": "out-nonksu"}):
                self.assertEqual(source_backup_root(Path(root)),
                                 Path(root) / "out-nonksu/hecker-suite-source-backups")
                self.assertNotEqual(source_backup_root(Path(root)),
                                    Path(root) / "out/hecker-suite-source-backups")

    def test_absolute_output_directory(self):
        with tempfile.TemporaryDirectory() as root, tempfile.TemporaryDirectory() as output:
            with patch.dict(os.environ, {"OUT_DIR": output}):
                self.assertEqual(source_backup_root(Path(root)),
                                 Path(output) / "hecker-suite-source-backups")


if __name__ == "__main__":
    unittest.main()

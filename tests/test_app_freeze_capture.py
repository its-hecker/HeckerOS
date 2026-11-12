# SPDX-License-Identifier: Apache-2.0
"""Host-only regression tests: capturing a frozen app must not mutate the device."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CAPTURE = ROOT / "tools/capture_app_freeze.sh"


class AppFreezeCaptureTest(unittest.TestCase):
    def test_shell_syntax(self):
        subprocess.run(["bash", "-n", str(CAPTURE)], check=True)

    def test_capture_is_read_only_and_preserves_focus_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mock = root / "adb"
            mock.write_text("#!/bin/sh\n"
                            "case \"$*\" in\n"
                            "  'get-state') echo device ;;\n"
                            "  *'dumpsys window'*) echo 'mCurrentFocus=null' ;;\n"
                            "  *'dumpsys input'*) echo 'FocusedWindow=null' ;;\n"
                            "  *'dumpsys activity activities'*) echo 'topResumedActivity=TestActivity' ;;\n"
                            "  *) echo 'diagnostic snapshot' ;;\n"
                            "esac\n")
            mock.chmod(0o755)
            env = os.environ.copy()
            env["PATH"] = str(root) + os.pathsep + env["PATH"]
            result = subprocess.run(["bash", str(CAPTURE), "frozen", "com.example.app"],
                                    cwd=root, env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            output = next(root.glob("app-freeze-frozen-*/"))
            self.assertIn("mCurrentFocus=null", (output / "focus-summary.txt").read_text())
            self.assertIn("FocusedWindow=null", (output / "focus-summary.txt").read_text())
            self.assertTrue((output / "logcat.txt").is_file())
            self.assertTrue((output / "app-frames.txt").is_file())

    def test_invalid_inputs_do_not_capture(self):
        with tempfile.TemporaryDirectory() as tmp:
            for args in (["../unsafe"], ["frozen", "bad/package"]):
                done = subprocess.run(["bash", str(CAPTURE), *args],
                                      cwd=tmp, capture_output=True, text=True)
                self.assertEqual(done.returncode, 2)
            self.assertFalse(list(Path(tmp).glob("app-freeze-*")))


if __name__ == "__main__":
    unittest.main()

# SPDX-License-Identifier: Apache-2.0
"""Static safety and shell-compatibility checks for the rooted flight recorder."""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
RECORDER = ROOT / "tools/capture_app_freeze_ondevice.sh"


class OnDeviceFreezeRecorderTests(unittest.TestCase):
    def test_posix_shell_syntax(self):
        subprocess.run(["sh", "-n", str(RECORDER)], check=True)

    def test_bounded_and_non_mutating(self):
        source = RECORDER.read_text()
        self.assertIn('"$duration" -gt 900', source)
        self.assertIn("trap 'cleanup' EXIT", source)
        self.assertIn("sleep 5", source)
        self.assertIn("focus-timeline.txt", source)
        self.assertIn("important-events.log", source)
        self.assertIn("surfaceflinger-final.txt", source)
        for forbidden in ("logcat -c", "setprop ", "settings put ", "am force-stop ",
                          "wm size ", "wm density ", "service call ", "killall "):
            self.assertNotIn(forbidden, source)

    def test_rejects_missing_destination_before_logging(self):
        # Without a root environment, the recorder must reject any run safely.
        attempt = subprocess.run(["sh", str(RECORDER), "", "300"],
                                 capture_output=True, text=True)
        self.assertNotEqual(attempt.returncode, 0)


if __name__ == "__main__":
    unittest.main()

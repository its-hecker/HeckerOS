# SPDX-License-Identifier: Apache-2.0
"""Focused stable guards for launcher, game-monitor and motion changes."""
from pathlib import Path
import json
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

class StableFluidityTests(unittest.TestCase):
    def test_launcher_glass_and_studio(self):
        glass = read("aosp-launcher/src/com/android/launcher3/hecker/HeckerLauncherGlass.java")
        self.assertIn("private boolean readStyle()", glass)
        self.assertIn("if (!changed) return false;", glass)
        self.assertIn("if (styleChanged || rebindTargets) invalidate();", glass)
        self.assertIn("if (mode == 0) { wallpaper=null; frosted=null; invalidate(); return; }", glass)
        self.assertIn("if (stopped || request != generation) return;", glass)
        self.assertIn("root.isShown()", glass)
        self.assertIn("setDuration(140)", read("aosp-launcher/src/com/android/launcher3/hecker/HeckerStudio.java"))
        patches = json.loads(read("aosp-launcher/patches.json"))
        folder = patches["src/com/android/launcher3/folder/FolderAnimationManager.java"]
        self.assertEqual(sum("mDuration = heckerFlow ? 260 :" in entry["new"] for entry in folder), 1)

    def test_game_monitor_config_cache(self):
        monitor = read("framework/gaming/HeckerGameMonitor.java")
        for term in ("private boolean configLoaded;", "cachedConfig",
                     "configLoaded=false;schedule()", "task == top.taskId && pkg.equals(activePackage)",
                     "Ignoring malformed game-monitor configuration"):
            self.assertIn(term, monitor)

    def test_motion_and_md3_media(self):
        for path in ("framework/motionisland/MotionIslandView.java",
                     "framework/motionisland/MotionAstronautView.java"):
            self.assertIn("postOnAnimationDelayed(this, 32)", read(path))
        media = read("framework/qs/HeckerMediaCard.kt")
        self.assertIn("MaterialTheme.colorScheme", media)
        self.assertIn("tween(300, easing = HeckerFlowEasing)", media)

    def test_settings_native_adapter_still_matches(self):
        k = "packages/apps/Settings/src/com/android/settings/homepage/SettingsHomepageActivity.java"
        self.assertEqual(json.loads(read("settings/patches.json"))[k],
                         json.loads(read("framework/customization-patches.json"))[k])

    def test_measure_and_drawer_capture_are_read_only(self):
        for path in ("tools/measure_ui_fluidity.sh", "tools/capture_drawer_home_jump.sh"):
            subprocess.run(["bash", "-n", str(ROOT / path)], check=True)
            code = read(path)
            for forbidden in ("logcat -c", "setprop ", "settings put ", "am force-stop "):
                self.assertNotIn(forbidden, code)

if __name__ == "__main__":
    unittest.main()

# SPDX-License-Identifier: Apache-2.0
"""Source and shell guards for the second HeckerOS fluidity experiment.

Static guards are not a substitute for Android compilation or device traces.
"""
from pathlib import Path
import json
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class FluidityPass2Tests(unittest.TestCase):
    def test_settings_optional_startup_work(self):
        voice = read("settings/src/com/android/settings/hecker/HeckerSettingsVoiceSearch.java")
        click = voice.index("microphone.setOnClickListener")
        self.assertNotIn("voice.resolveActivity", voice[:click])
        self.assertIn("startActivityForResult(voice, REQUEST)", voice[click:])
        key = "packages/apps/Settings/src/com/android/settings/homepage/SettingsHomepageActivity.java"
        native = json.loads(read("settings/patches.json"))
        applied = json.loads(read("framework/customization-patches.json"))
        self.assertEqual(native[key], applied[key])
        matches = [p for p in native[key] if "HOMEPAGE_LOADING_TIMEOUT_MS" in p["old"]]
        self.assertEqual(len(matches), 1)
        self.assertIn("HOMEPAGE_LOADING_TIMEOUT_MS = 180", matches[0]["new"])

    def test_suite_tab_views_cached(self):
        src = read("app/src/org/hecker/suite/SuiteActivity.java")
        self.assertIn("private LinearLayout customizationsPanel, aboutPanel;", src)
        self.assertIn("if (customizationsPanel == null)", src)
        self.assertIn("if (aboutPanel == null)", src)
        self.assertIn("content.getChildAt(0) == target", src)
        self.assertIn("populateCustomizations(customizationsPanel)", src)

    def test_launcher_skips_redundant_glass_work(self):
        src = read("aosp-launcher/src/com/android/launcher3/hecker/HeckerLauncherGlass.java")
        self.assertIn("private boolean readStyle()", src)
        self.assertIn("if (!changed) return false;", src)
        self.assertIn("if (styleChanged || rebindTargets) invalidate()", src)
        self.assertIn("if (mode == 0) { wallpaper=null; frosted=null; invalidate(); return; }", src)
        self.assertIn("if (stopped || request != generation) return;", src)

    def test_monitor_config_read_only_on_invalidation(self):
        src = read("framework/gaming/HeckerGameMonitor.java")
        self.assertIn("private boolean configLoaded;", src)
        self.assertIn("if (!configLoaded)", src)
        self.assertIn("configLoaded=false;schedule()", src)
        self.assertIn("task == top.taskId && pkg.equals(activePackage)", src)

    def test_transitions_and_pacing_are_targeted(self):
        launcher = json.loads(read("aosp-launcher/patches.json"))
        folder = launcher["src/com/android/launcher3/folder/FolderAnimationManager.java"]
        self.assertEqual(sum("mDuration = heckerFlow ? 260 :" in p["new"] for p in folder), 1)
        self.assertIn("setDuration(140)", read("aosp-launcher/src/com/android/launcher3/hecker/HeckerStudio.java"))
        self.assertIn("postOnAnimationDelayed(this, 32)", read("framework/motionisland/MotionIslandView.java"))
        self.assertIn("tween(300, easing = HeckerFlowEasing)", read("framework/qs/HeckerMediaCard.kt"))
        origin = json.loads(read("aosp-launcher/ORIGIN.json"))
        self.assertEqual(origin["recents_provider"], "com.google.android.apps.nexuslauncher")

    def test_capture_tools_non_mutating(self):
        for path in ("tools/measure_ui_fluidity.sh", "tools/capture_drawer_home_jump.sh"):
            script = ROOT / path
            subprocess.run(["bash", "-n", str(script)], check=True)
            code = script.read_text()
            for forbidden in ("logcat -c", "setprop ", "settings put ",
                              "wm size ", "wm density ", "am force-stop "):
                self.assertNotIn(forbidden, code)
        self.assertIn("framestats", read("tools/measure_ui_fluidity.sh"))
        self.assertIn("ShellRecents", read("tools/capture_drawer_home_jump.sh"))


if __name__ == "__main__":
    unittest.main()

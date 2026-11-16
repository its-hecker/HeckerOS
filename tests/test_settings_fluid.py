# SPDX-License-Identifier: Apache-2.0
"""Host source guards for Settings smoothness promoted to stable."""
from pathlib import Path
import json
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SettingsFluidSourceTests(unittest.TestCase):
    def test_home_row_background_reused_on_identical_binding(self):
        src = (ROOT / "settings/src/com/android/settings/hecker/HeckerHomepageStyle.java").read_text()
        self.assertIn("WeakHashMap<View, RowBackground>", src)
        self.assertIn("cached.drawable.get() != row.getBackground()", src)
        self.assertIn("cached.selected != selected", src)
        self.assertIn("cached.density != density", src)
        self.assertIn("ROW_BACKGROUNDS.put(row,", src)
        self.assertIn("GradientDrawable.OVAL", src)
        self.assertIn("disc.getColor().getDefaultColor() != iconColor", src)

    def test_scrolling_card_decoration_skips_unneeded_path_builds(self):
        src = (ROOT / "settings/src/com/android/settings/hecker/HeckerHomeDecoration.java").read_text()
        self.assertIn("if (!first && !last)", src)
        middle, rounded = src.split("if (!first && !last)", 1)[1].split("} else {", 1)
        self.assertIn("canvas.drawRect(bounds, paint)", middle)
        self.assertIn("path.addRoundRect", rounded)

    def test_brand_span_constructed_once_per_preference(self):
        src = (ROOT / "settings/src/com/android/settings/hecker/HeckerBrandPreference.java").read_text()
        constructor, bind = src.split("@Override public void onBindViewHolder(", 1)
        self.assertIn('new SpannableString("HeckerOS")', constructor)
        self.assertNotIn("new SpannableString", bind)
        self.assertIn("title.setText(brandedTitle)", bind)

    def test_settings_benchmark_is_readable_and_bounded(self):
        script = ROOT / "tools/measure_settings_startup.sh"
        subprocess.run(["bash", "-n", str(script)], check=True)
        src = script.read_text()
        self.assertIn('mode="${2:-warm}"', src)
        self.assertIn('if [[ "$mode" == "cold" ]]', src)
        self.assertIn("adb shell am start -W -a android.settings.SETTINGS", src)
        self.assertNotIn("settings put ", src)
        self.assertNotIn("setprop ", src)

    def test_native_homepage_startup_and_voice_fallback(self):
        voice = (ROOT / "settings/src/com/android/settings/hecker/HeckerSettingsVoiceSearch.java").read_text()
        self.assertNotIn("voice.resolveActivity(activity.getPackageManager())", voice)
        self.assertIn("microphone.setOnClickListener(view -> {", voice)
        self.assertIn("startActivityForResult(voice, REQUEST)", voice)
        self.assertIn("search.performClick()", voice)
        patches = json.loads((ROOT / "settings/patches.json").read_text())
        changes = patches["packages/apps/Settings/src/com/android/settings/homepage/SettingsHomepageActivity.java"]
        matching = [rule for rule in changes if "HOMEPAGE_LOADING_TIMEOUT_MS" in rule["old"]]
        self.assertEqual(len(matching), 1)
        self.assertIn("HOMEPAGE_LOADING_TIMEOUT_MS = 300", matching[0]["old"])
        self.assertIn("HOMEPAGE_LOADING_TIMEOUT_MS = 180", matching[0]["new"])


if __name__ == "__main__":
    unittest.main()

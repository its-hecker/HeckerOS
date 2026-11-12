# SPDX-License-Identifier: Apache-2.0
"""Host source guards for the experimental Settings smoothness branch."""
from pathlib import Path
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


if __name__ == "__main__":
    unittest.main()

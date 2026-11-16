# SPDX-License-Identifier: Apache-2.0
"""Guards for the paired Material You Settings/QS and compact headphone UI."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def colors(folder, filename):
    return {node.get("name"): (node.text or "").strip()
            for node in ET.parse(ROOT / "settings/res" / folder / filename).getroot()}


class MaterialYouConsistencyTests(unittest.TestCase):
    def test_about_phone_shares_homepage_palette_in_both_themes(self):
        for folder in ("values", "values-night"):
            with self.subTest(theme=folder):
                home = colors(folder, "hecker_home.xml")
                about = colors(folder, "hecker_about_colors.xml")
                for about_role, home_role in (
                    ("background", "background"),
                    ("surface", "card"),
                    ("text", "foreground"),
                    ("divider", "separator"),
                ):
                    self.assertEqual(about["hecker_about_" + about_role],
                                     home["hecker_home_" + home_role])
                self.assertTrue(about["hecker_about_hero_start"].startswith(
                    "@android:color/system_accent"))
                self.assertTrue(about["hecker_about_hero_end"].startswith(
                    "@android:color/system_accent"))

    def test_compact_and_expanded_qs_use_dynamic_color_scheme(self):
        folder = ROOT / "framework/qs"
        for filename in ("HeckerGlassQuickSettings.kt",
                         "HeckerReferenceQuickSettings.kt",
                         "HeckerQuickSettings.kt", "HeckerMediaCard.kt"):
            with self.subTest(source=filename):
                source = (folder / filename).read_text()
                self.assertIn("MaterialTheme.colorScheme", source)
        glass = (folder / "HeckerGlassQuickSettings.kt").read_text()
        oneui = (folder / "HeckerReferenceQuickSettings.kt").read_text()
        self.assertIn("fun HeckerGlassCompact", glass)
        self.assertIn("fun HeckerGlassQuickSettings", glass)
        self.assertIn("fun HeckerReferenceCompact", oneui)
        self.assertIn("fun HeckerReferenceQuickSettings", oneui)
        self.assertIn("referenceActions(tile", oneui)
        self.assertIn("scheme.primaryContainer", glass)
        self.assertIn("MaterialTheme.colorScheme.primaryContainer", oneui)

    def test_headphone_popup_is_compact_and_preserves_actions(self):
        source = (ROOT / "framework/headphones/HeckerHeadphonePopup.java").read_text()
        self.assertIn("new LinearLayout.LayoutParams(dp(92), dp(80))", source)
        self.assertIn("new LinearLayout.LayoutParams(-2, dp(40))", source)
        self.assertIn("battery.setBackground(chipShape(chipSurface, outline))", source)
        self.assertNotIn("new LinearLayout.LayoutParams(-1, dp(42))", source)
        self.assertNotIn("new LinearLayout.LayoutParams(-1, dp(180))", source)
        self.assertNotIn("dp(440)", source)
        for unchanged_feature in (
            "updateBattery(); handler.postDelayed(refresh, 500);",
            "getLaunchIntentForPackage(\"org.hecker.librepods\")",
            "settings.setOnClickListener",
            "getRecommendedTimeoutMillis",
            "addCrossWindowBlurEnabledListener",
            "registerReceiver",
        ):
            self.assertIn(unchanged_feature, source)


if __name__ == "__main__":
    unittest.main()

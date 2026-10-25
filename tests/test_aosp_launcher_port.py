# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import json
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from aosp_launcher_port import aosp_launcher_plan, SOURCE
from prepare import replace_guarded


class AospLauncherPortTests(unittest.TestCase):
    def stage(self, rom):
        for name, pairs in json.loads((ROOT / 'aosp-launcher/patches.json').read_text()).items():
            path = rom / SOURCE / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('\n// native separator\n'.join(pair['old'] for pair in pairs))
        (rom / SOURCE / 'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <application android:debuggable="true" />\n</manifest>')

    def apply(self, changes):
        for path, value in changes.items():
            if value is None:
                path.unlink()
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(value)

    def test_fresh_upgrade_and_revert_retain_native_source(self):
        with tempfile.TemporaryDirectory() as directory:
            rom = Path(directory)
            self.stage(rom)
            original = {str(p.relative_to(rom)): p.read_bytes() for p in rom.rglob('*') if p.is_file()}
            self.apply(aosp_launcher_plan(rom, replace_guarded))
            self.assertFalse(aosp_launcher_plan(rom, replace_guarded))
            self.apply(aosp_launcher_plan(rom, replace_guarded, True))
            self.assertEqual(original, {str(p.relative_to(rom)): p.read_bytes() for p in rom.rglob('*') if p.is_file()})

    def test_published_gesture_copy_and_preferences_upgrade_preserving_local_edits(self):
        with tempfile.TemporaryDirectory() as directory:
            rom = Path(directory)
            self.stage(rom)
            self.apply(aosp_launcher_plan(rom, replace_guarded))
            helper = rom / SOURCE / 'src/com/android/launcher3/hecker/HeckerWorkspaceGestures.java'
            helper.write_bytes((ROOT / 'tests/fixtures/aosp-workspace-25f828c.snapshot').read_bytes())
            pref = rom / SOURCE / 'res/xml/launcher_preferences.xml'
            pair = json.loads((ROOT / 'aosp-launcher/patches.json').read_text())['res/xml/launcher_preferences.xml'][0]
            pref.write_text(pref.read_text().replace(pair['new'], pair['previous'][0]) + '\n<!-- private preference -->\n')
            self.apply(aosp_launcher_plan(rom, replace_guarded))
            self.assertIn('private preference', pref.read_text())
            self.assertEqual((ROOT / 'aosp-launcher/src/com/android/launcher3/hecker/HeckerWorkspaceGestures.java').read_bytes(), helper.read_bytes())
            self.assertFalse(aosp_launcher_plan(rom, replace_guarded))
            helper.write_text('// unrelated local gesture implementation')
            with self.assertRaisesRegex(ValueError, 'Preserving unfamiliar'):
                aosp_launcher_plan(rom, replace_guarded)

    def test_published_module_and_manifest_upgrade_without_duplicate_home(self):
        with tempfile.TemporaryDirectory() as directory:
            rom = Path(directory)
            self.stage(rom)
            pairs = json.loads((ROOT / 'aosp-launcher/patches.json').read_text())
            # The exact formerly published HOME module, before the retained weather dependency.
            (rom / SOURCE / 'Android.bp').write_text(pairs['Android.bp'][0]['previous'][-1])
            manifest = rom / SOURCE / 'AndroidManifest.xml'
            generated = rom / SOURCE / 'generated/hecker-launcher/AndroidManifest.xml'
            generated.parent.mkdir(parents=True, exist_ok=True)
            generated.write_text(manifest.read_text().replace('android:debuggable="true"', 'android:debuggable="false"').replace(
                '    <application', '    <uses-permission android:name="android.permission.DEVICE_POWER" />\n'
                '    <uses-permission android:name="android.permission.EXPAND_STATUS_BAR" />\n'
                '    <uses-permission android:name="android.permission.READ_WALLPAPER_INTERNAL" />\n    <application'))
            self.apply(aosp_launcher_plan(rom, replace_guarded))
            blueprint = (rom / SOURCE / 'Android.bp').read_text()
            self.assertEqual(blueprint.count('name: "HeckerLauncher"'), 1)
            self.assertEqual(blueprint.count('"HeckerLauncherGoogleWeather"'), 1)
            self.assertEqual(generated.read_text().count('android:name="com.android.launcher3.hecker.HeckerClockWidget"'), 1)
            self.assertFalse(aosp_launcher_plan(rom, replace_guarded))

    def test_published_icon_size_hook_upgrades_without_final_field_reassignment(self):
        with tempfile.TemporaryDirectory() as directory:
            rom = Path(directory)
            self.stage(rom)
            relative = 'src/com/android/launcher3/BubbleTextView.java'
            pairs = json.loads((ROOT / 'aosp-launcher/patches.json').read_text())[relative]
            pair = next(p for p in pairs if 'mIconSize = a.getDimensionPixelSize' in p['old'])
            path = rom / SOURCE / relative
            path.write_text(path.read_text().replace(pair['old'], pair['previous'][0])
                            + '\n// retained local note\n')
            self.apply(aosp_launcher_plan(rom, replace_guarded))
            self.assertIn(pair['new'], path.read_text())
            self.assertNotIn(pair['previous'][0], path.read_text())
            self.assertEqual(path.read_text().count('mIconSize ='), 1)
            self.assertIn('retained local note', path.read_text())
            self.assertFalse(aosp_launcher_plan(rom, replace_guarded))
            self.apply(aosp_launcher_plan(rom, replace_guarded, True))
            self.assertIn(pair['old'], path.read_text())
            self.assertNotIn(pair['previous'][0], path.read_text())


if __name__ == '__main__':
    unittest.main()

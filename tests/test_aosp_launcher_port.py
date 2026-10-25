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


if __name__ == '__main__':
    unittest.main()

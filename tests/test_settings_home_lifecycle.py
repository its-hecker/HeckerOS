# SPDX-License-Identifier: Apache-2.0
"""Regress the reported TopLevelSettings divider crash using its native callback."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from prepare import replace_guarded
from settings_home_lifecycle import run_lifecycle_probe

RELATIVE = 'packages/apps/Settings/src/com/android/settings/homepage/TopLevelSettings.java'


class SettingsHomeLifecycleTests(unittest.TestCase):
    def setUp(self):
        # Exact onCreateRecyclerView from native Settings revision 3831282d93ecda4d52a6894077482fe01ecea903.
        self.callback = (ROOT / 'tests/fixtures/settings-home-recyclerview-3831282.snapshot').read_text()
        self.pairs = json.loads((ROOT / 'settings/patches.json').read_text())[RELATIVE]

    def test_published_early_divider_call_reproduces_null_list_crash(self):
        pair = self.pairs[1]
        broken = next(value for value in pair['previous'] if 'setDivider(null)' in value)
        source = self.callback.replace(pair['old'], broken)
        with tempfile.TemporaryDirectory() as temporary:
            result = run_lifecycle_probe(source, temporary)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('NullPointerException', result.stderr)
        self.assertIn('TopLevelSettings.onCreateRecyclerView', result.stderr)

    def test_prepared_native_callbacks_survive_first_open_and_view_recreation(self):
        source = self.callback
        for pair in self.pairs[1:]:
            source = replace_guarded(source, pair['old'], pair['new'], RELATIVE)
        with tempfile.TemporaryDirectory() as temporary:
            result = run_lifecycle_probe(source, temporary)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('repeated recreation: PASS', result.stdout)


if __name__ == '__main__':
    unittest.main()

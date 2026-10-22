# SPDX-License-Identifier: Apache-2.0
"""Guard reviewed Launcher migrations and preserve unfamiliar source changes."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import launcher_port
import check_native_ports
from prepare import replace_guarded


class LauncherPortTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        suite = root / 'suite'
        self.suite = suite
        (suite / 'launcher/src').mkdir(parents=True)
        (suite / 'launcher/res/raw').mkdir(parents=True)
        (suite / 'launcher/src/Helper.java').write_text('// current helper\n')
        (suite / 'launcher/res/raw/orbit.json').write_text('{}\n')
        weather = suite / 'launcher/compat/googleweather'
        (weather / 'src').mkdir(parents=True)
        (weather / 'Android.bp.in').write_bytes(
            (ROOT / 'launcher/compat/googleweather/Android.bp.in').read_bytes())
        (weather / 'src/Weather.kt').write_text('// retained weather client\n')
        self.old_copy = '// reviewed earlier helper\n'
        (suite / 'launcher/copy-upgrades.json').write_text(json.dumps({
            'src/com/android/launcher3/hecker/Helper.java': [
                hashlib.sha256(self.old_copy.encode()).hexdigest()]}))
        (suite / 'launcher/patches.json').write_text(json.dumps({'Android.bp': [{
            'old': '// upstream module', 'new': '// current adapter',
            'previous': ['// earlier adapter']}]}))
        self.rom = root / 'rom'
        self.launcher = self.rom / launcher_port.LAUNCHER
        self.launcher.mkdir(parents=True)
        self.bp = self.launcher / 'Android.bp'
        self.bp.write_text('// upstream module\n// maintainer edit\n')
        self.helper = self.launcher / 'src/com/android/launcher3/hecker/Helper.java'
        mocked_root = patch.object(launcher_port, 'ROOT', suite)
        mocked_root.start()
        self.addCleanup(mocked_root.stop)

    def plan(self, revert=False):
        return launcher_port.launcher_plan(self.rom, replace_guarded, revert)

    def write(self, changes):
        for path, content in changes.items():
            if content is None:
                path.unlink()
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)

    def snapshot(self):
        return {str(p.relative_to(self.rom)): p.read_bytes()
                for p in self.rom.rglob('*') if p.is_file()}

    def test_fresh_apply_is_idempotent_and_reverts_exactly(self):
        before = self.snapshot()
        self.write(self.plan())
        self.assertEqual({}, self.plan())
        self.write(self.plan(True))
        self.assertEqual(before, self.snapshot())

    def test_weather_template_emits_one_active_soong_definition(self):
        self.write(self.plan())
        generated = self.launcher / 'generated/hecker-google-weather'
        self.assertEqual((self.suite / 'launcher/compat/googleweather/Android.bp.in').read_bytes(),
                         (generated / 'Android.bp').read_bytes())
        self.assertFalse((generated / 'Android.bp.in').exists())
        self.assertTrue((generated / 'src/Weather.kt').is_file())
        with patch.object(check_native_ports, 'ROOT', self.suite):
            check_native_ports.check_launcher_weather_module(self.rom)
        self.assertEqual({}, self.plan())

    def test_published_duplicate_weather_definition_is_rejected(self):
        self.write(self.plan())
        weather = self.suite / 'launcher/compat/googleweather'
        (weather / 'Android.bp').write_bytes((weather / 'Android.bp.in').read_bytes())
        with patch.object(check_native_ports, 'ROOT', self.suite):
            with self.assertRaisesRegex(AssertionError, 'Duplicate or missing Google weather module'):
                check_native_ports.check_launcher_weather_module(self.rom)

    def test_prepared_checkout_upgrades_reviewed_adapter_and_copy(self):
        self.bp.write_text('// earlier adapter\n// maintainer edit\n')
        self.helper.parent.mkdir(parents=True)
        self.helper.write_text(self.old_copy)
        before = self.snapshot()
        changes = self.plan()
        self.assertEqual(before, self.snapshot(), 'Planning must not mutate the checkout')
        self.assertIn(self.helper, changes)
        self.write(changes)
        self.assertEqual('// current helper\n', self.helper.read_text())
        self.assertEqual('// current adapter\n// maintainer edit\n', self.bp.read_text())
        self.assertEqual({}, self.plan())
        self.write(self.plan(True))
        self.assertFalse(self.helper.exists())
        self.assertEqual('// upstream module\n// maintainer edit\n', self.bp.read_text())

    def test_edited_old_copy_is_preserved_without_partial_writes(self):
        self.helper.parent.mkdir(parents=True)
        self.helper.write_text(self.old_copy + '// local customization\n')
        before = self.snapshot()
        with self.assertRaisesRegex(ValueError, 'Preserving an unfamiliar'):
            self.plan()
        self.assertEqual(before, self.snapshot())

    def test_unreviewed_adapter_is_preserved(self):
        self.bp.write_text('// locally customized adapter\n')
        before = self.snapshot()
        with self.assertRaises(ValueError):
            self.plan()
        self.assertEqual(before, self.snapshot())

    def test_revert_can_remove_reviewed_old_copy(self):
        self.bp.write_text('// current adapter\n')
        self.helper.parent.mkdir(parents=True)
        self.helper.write_text(self.old_copy)
        self.write(self.plan(True))
        self.assertFalse(self.helper.exists())
        self.assertEqual('// upstream module\n', self.bp.read_text())

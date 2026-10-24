# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from retire_launcher import retire, OLD


class LauncherMigrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.rom = Path(self.temp.name)
        (self.rom / '.repo').mkdir()
        (self.rom / 'build').mkdir()
        (self.rom / 'build/envsetup.sh').touch()
        self.source = self.rom / 'packages/apps/Launcher3'
        self.source.mkdir(parents=True)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Test')
        self.git('config', 'user.email', 'test@example.invalid')
        self.git('remote', 'add', 'floral-github', 'https://github.com/' + OLD)
        (self.source / 'Android.bp').write_text('original\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Original')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.source), *args], stderr=subprocess.PIPE)

    def test_preserves_staged_unstaged_and_untracked_edits(self):
        (self.source / 'Android.bp').write_text('staged\n')
        self.git('add', 'Android.bp')
        (self.source / 'Android.bp').write_text('final local source\n')
        (self.source / 'private-binary').write_bytes(b'\x00local\xff')
        destination = retire(self.rom)
        self.assertEqual((destination / 'Android.bp').read_text(), 'final local source\n')
        self.assertEqual((destination / 'private-binary').read_bytes(), b'\x00local\xff')
        self.assertIn(b'+final local source', (destination.parent / 'tracked-changes.patch').read_bytes())
        self.assertFalse(self.source.exists())
        self.assertIsNone(retire(self.rom))

    def test_infinity_checkout_is_also_retired(self):
        self.git('remote', 'set-url', 'floral-github', 'https://gitea.com/projectInfinity-x/packages_apps_Launcher3')
        destination = retire(self.rom)
        self.assertEqual((destination / 'Android.bp').read_text(), 'original\n')
        self.assertFalse(self.source.exists())

    def test_stock_aosp_libraries_are_retained(self):
        self.git('remote', 'set-url', 'floral-github',
                 'https://android.googlesource.com/platform/packages/apps/Launcher3')
        self.assertIsNone(retire(self.rom))
        self.assertEqual('original\n', (self.source / 'Android.bp').read_text())

    def test_unfamiliar_repository_is_preserved(self):
        self.git('remote', 'set-url', 'floral-github', 'https://example.invalid/custom-launcher')
        with self.assertRaisesRegex(ValueError, 'unfamiliar'):
            retire(self.rom)
        self.assertTrue(self.source.exists())

    def test_symlink_is_rejected_before_inspection(self):
        real = self.source.with_name('private-launcher')
        self.source.rename(real)
        self.source.symlink_to(real, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'source symlink'):
            retire(self.rom)
        self.assertTrue((real / 'Android.bp').exists())

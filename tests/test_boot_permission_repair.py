# SPDX-License-Identifier: Apache-2.0
"""Exercise the real root repair script with isolated files and simulated mount commands."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/boot-permission-repair'


class BootPermissionRepairTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.target = self.root / 'system_ext/etc/permissions/privapp-permissions-hecker-netlab.xml'
        self.target.parent.mkdir(parents=True)
        self.original = (ROOT / 'tests/fixtures/netlab-allowlist-de50655.snapshot').read_bytes()
        self.target.write_bytes(self.original)
        self.repair = self.root / 'repair'
        self.repair.mkdir()
        self.payload = self.repair / 'privapp-permissions-hecker-bootfix.xml'
        self.payload.write_bytes((SOURCE / self.payload.name).read_bytes())
        script = (SOURCE / 'repair.sh').read_text().replace(
            '/system_ext/etc/permissions/privapp-permissions-hecker-netlab.xml', str(self.target))
        for app in ('HeckerLauncher', 'HeckerNetLabController'):
            original = '/system_ext/priv-app/' + app + '/' + app + '.apk'
            path = self.root / original.lstrip('/')
            path.parent.mkdir(parents=True)
            path.write_bytes(b'owned app fixture')
            script = script.replace(original, str(path))
        self.script = self.repair / 'repair.sh'
        self.script.write_text(script)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        fakes = {
            'id': 'echo "${REPAIR_TEST_UID:-0}"',
            'readlink': '''if [ "$1" = /proc/self/ns/mnt ] && [ "${REPAIR_TEST_PRIVATE:-0}" = 1 ] && [ "${REPAIR_TEST_ENTERED:-0}" != 1 ]; then echo 'mnt:[2]'; else echo 'mnt:[1]'; fi''',
            'nsenter': '''echo "nsenter $*" >> "$REPAIR_TEST_TRACE"
export REPAIR_TEST_ENTERED=1
shift 4
exec /bin/sh "$@"''',
            'chown': ':',
            'chmod': ':',
            'chcon': 'exit "${REPAIR_TEST_LABEL_FAIL:-0}"',
            'mount': '''echo "mount $*" >> "$REPAIR_TEST_TRACE"
[ "${REPAIR_TEST_MOUNT_FAIL:-0}" = 0 ] || exit 1
/bin/cp "$3" "$REPAIR_TEST_BACKUP"
/bin/cp "$2" "$3"''',
            'umount': '''echo "umount $*" >> "$REPAIR_TEST_TRACE"
/bin/cp "$REPAIR_TEST_BACKUP" "$1"''',
        }
        for name, code in fakes.items():
            path = self.bin / name
            path.write_text('#!/bin/sh\n' + code + '\n')
            path.chmod(0o755)
        self.trace = self.root / 'commands.txt'
        self.env = dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'],
                        REPAIR_TEST_TRACE=str(self.trace), REPAIR_TEST_BACKUP=str(self.root / 'original.xml'))

    def run_script(self, *args, **env):
        return subprocess.run(['/bin/sh', str(self.script), *args], env=dict(self.env, **env),
                              capture_output=True, text=True)

    def assert_untouched(self):
        self.assertEqual(self.original, self.target.read_bytes())
        self.assertFalse((self.repair / 'active').exists())
        self.assertFalse(self.trace.exists() and 'mount --bind' in self.trace.read_text())

    def test_root_is_required_before_any_mount(self):
        result = self.run_script(REPAIR_TEST_UID='2000')
        self.assertNotEqual(result.returncode, 0)
        self.assert_untouched()

    def test_payload_modification_is_rejected(self):
        self.payload.write_text(self.payload.read_text() + '\n<!-- changed -->')
        result = self.run_script()
        self.assertIn('checksum mismatch', result.stderr)
        self.assertNotEqual(result.returncode, 0)
        self.assert_untouched()

    def test_unfamiliar_permission_file_is_preserved(self):
        self.target.write_text('<permissions><unexpected /></permissions>')
        result = self.run_script()
        self.assertIn('Unfamiliar', result.stderr)
        self.assertEqual('<permissions><unexpected /></permissions>', self.target.read_text())
        self.assertFalse(self.trace.exists())

    def test_label_failure_does_not_mount(self):
        result = self.run_script(REPAIR_TEST_LABEL_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assert_untouched()

    def test_mount_failure_keeps_original_file(self):
        result = self.run_script(REPAIR_TEST_MOUNT_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.original, self.target.read_bytes())
        self.assertFalse((self.repair / 'active').exists())

    def test_private_namespace_is_entered_and_repair_is_repeatable(self):
        result = self.run_script(REPAIR_TEST_PRIVATE='1')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('nsenter -t 1 -m /system/bin/sh', self.trace.read_text())
        self.assertEqual(self.payload.read_bytes(), self.target.read_bytes())
        self.assertTrue((self.repair / 'active').exists())
        self.assertEqual(self.run_script().returncode, 0)
        self.assertEqual(self.trace.read_text().count('mount --bind'), 1)

    def test_undo_restores_only_the_temporary_mount(self):
        self.assertEqual(self.run_script().returncode, 0)
        result = self.run_script('--undo')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.original, self.target.read_bytes())
        self.assertFalse((self.repair / 'active').exists())

    def test_payload_preserves_netlab_grants_and_adds_only_logged_omissions(self):
        actual = {(b.get('package'), p.get('name'))
                  for b in ET.parse(self.payload).getroot().findall('privapp-permissions')
                  for p in b.findall('permission')}
        self.assertEqual({('com.android.launcher3', 'android.permission.READ_WALLPAPER_INTERNAL'),
                          ('org.hecker.netlab', 'android.permission.NETWORK_SETTINGS'),
                          ('org.hecker.netlab', 'android.permission.TETHER_PRIVILEGED'),
                          ('org.hecker.netlab', 'android.permission.SCHEDULE_EXACT_ALARM')}, actual)


if __name__ == '__main__':
    unittest.main()

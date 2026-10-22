# SPDX-License-Identifier: Apache-2.0
"""Evaluate the actual product fragment for development and final-build modes."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DevelopmentAdbTests(unittest.TestCase):
    def evaluate(self, variant, mode=None, inherited=()):
        source = 'PRODUCT_PRODUCT_PROPERTIES := ' + ' '.join(inherited) + '\n'
        source += 'include ' + str(ROOT / 'rom/adb/development_adb.mk') + '\n'
        source += 'all:\n\t@printf "%s\\n" $(PRODUCT_PRODUCT_PROPERTIES)\n'
        command = ['make', '--no-print-directory', '-f', '-', 'TARGET_BUILD_VARIANT=' + variant]
        environment = os.environ.copy()
        environment.pop('HECKER_DEV_USB_ADB', None)
        if mode is not None:
            environment['HECKER_DEV_USB_ADB'] = mode
        return subprocess.run(command, input=source, env=environment, text=True, capture_output=True)

    def properties(self, variant, mode=None, inherited=()):
        result = self.evaluate(variant, mode, inherited)
        self.assertEqual(0, result.returncode, result.stderr)
        entries = result.stdout.splitlines()
        values = dict(entry.split('=', 1) for entry in entries)
        self.assertEqual(len(entries), len(values), 'Duplicate product property assignments')
        return values

    def test_debug_variants_enable_usb_and_accept_any_host(self):
        for variant in ['userdebug', 'eng']:
            with self.subTest(variant=variant):
                values = self.properties(variant)
                self.assertEqual('0', values['ro.adb.secure'])
                self.assertEqual('adb', values['persist.sys.usb.config'])
                self.assertEqual('1', values['ro.heckeros.dev_usb_adb'])
                self.assertNotIn('ro.secure', values)
                self.assertNotIn('service.adb.tcp.port', values)
                self.assertNotIn('persist.adb.tcp.port', values)

    def test_final_switch_restores_authentication_and_base_usb_defaults(self):
        for variant in ['userdebug', 'eng', 'user']:
            with self.subTest(variant=variant):
                values = self.properties(variant, 'false', ['persist.sys.usb.config=mtp'])
                self.assertEqual('1', values['ro.adb.secure'])
                self.assertEqual('0', values['ro.heckeros.dev_usb_adb'])
                self.assertEqual('mtp', values['persist.sys.usb.config'])

    def test_user_variant_defaults_to_authenticated_adb(self):
        values = self.properties('user')
        self.assertEqual('1', values['ro.adb.secure'])
        self.assertEqual('0', values['ro.heckeros.dev_usb_adb'])
        self.assertNotIn('persist.sys.usb.config', values)
        result = self.evaluate('user', 'true')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('requires a userdebug or eng build', result.stderr)

    def test_overrides_inherited_usb_defaults_without_property_duplicates(self):
        values = self.properties('userdebug', 'true', [
            'ro.adb.secure=1', 'ro.adb.secure?=1', 'persist.sys.usb.config=mtp',
            'persist.sys.usb.config?=none', 'ro.heckeros.dev_usb_adb=0', 'keep.this=value'])
        self.assertEqual('0', values['ro.adb.secure'])
        self.assertEqual('adb', values['persist.sys.usb.config'])
        self.assertEqual('value', values['keep.this'])

    def test_invalid_mode_is_rejected(self):
        for value in ['1', 'TRUE', '', 'true false', '%']:
            with self.subTest(mode=value):
                result = self.evaluate('userdebug', value)
                self.assertNotEqual(0, result.returncode)
                self.assertIn('must be true or false', result.stderr)

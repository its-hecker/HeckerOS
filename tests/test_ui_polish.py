# SPDX-License-Identifier: Apache-2.0
import hashlib
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import recovery_port
import customization_port
from prepare import replace_guarded


def luminance(color):
    rgb = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return sum(a * b for a, b in zip(linear, (.2126, .7152, .0722)))


class UiPolishTests(unittest.TestCase):
    def test_text_and_selected_buttons_have_readable_contrast(self):
        for mode in ('values', 'values-night'):
            values = {n.get('name'): n.text for n in ET.parse(
                ROOT / 'app/res' / mode / 'hecker_colors.xml').getroot()}
            for foreground, background in [('hecker_foreground', 'hecker_card'),
                    ('hecker_secondary', 'hecker_card'), ('hecker_foreground', 'hecker_button'),
                    ('hecker_on_accent', 'hecker_accent')]:
                light, dark = sorted([luminance(values[foreground]), luminance(values[background])], reverse=True)
                self.assertGreaterEqual((light + .05) / (dark + .05), 4.5, (mode, foreground, background))

    def test_chinese_resources_cover_custom_pages_and_preserve_arguments(self):
        for package in ('app', 'settings'):
            defaults = {n.get('name'): n for p in (ROOT / package / 'res/values').glob('*.xml')
                        for n in ET.parse(p).getroot() if n.tag in ('string', 'string-array')
                        and n.get('translatable') != 'false'}
            translated = {n.get('name'): n for p in (ROOT / package / 'res/values-zh-rCN').glob('*.xml')
                          for n in ET.parse(p).getroot()}
            self.assertFalse(set(defaults) - set(translated))
            for key, original in defaults.items():
                target = translated[key]
                self.assertEqual(original.tag, target.tag)
                self.assertEqual(len(original), len(target), key)
                pattern = r'%(?:\d+\$)?[-+\d.]*[a-zA-Z]'
                self.assertEqual(sorted(re.findall(pattern, ''.join(original.itertext()))),
                                 sorted(re.findall(pattern, ''.join(target.itertext()))), key)

    def test_recovery_art_update_and_exact_restore(self):
        with tempfile.TemporaryDirectory() as temp:
            rom = Path(temp)
            for density in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
                target = rom / f'bootable/recovery/res-{density}/images/logo_image.png'
                target.parent.mkdir(parents=True)
                target.write_bytes((ROOT / 'recovery/upstream' / (density + '.png')).read_bytes())
            original = {p: p.read_bytes() for p in rom.rglob('*.png')}
            changes = recovery_port.recovery_plan(rom)
            self.assertEqual(5, len(changes))
            for p, data in changes.items(): p.write_bytes(data)
            self.assertFalse(recovery_port.recovery_plan(rom))
            for p, data in recovery_port.recovery_plan(rom, True).items(): p.write_bytes(data)
            self.assertEqual(original, {p: p.read_bytes() for p in rom.rglob('*.png')})
            next(iter(original)).write_bytes(b'unrelated custom recovery artwork')
            with self.assertRaises(ValueError): recovery_port.recovery_plan(rom)

    def test_reviewed_adapter_upgrade_preserves_unrecognized_edits(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source, rom = root / 'suite', root / 'rom'
            (source / 'framework').mkdir(parents=True)
            (source / 'adapter.java').write_bytes(b'new adapter')
            (source / 'framework/customization-patches.json').write_text('{}')
            path = 'frameworks/base/Adapter.java'
            (source / 'framework/customization-copies.json').write_text(json.dumps({path: 'adapter.java'}))
            old = b'reviewed old adapter'
            identity = hashlib.sha1(b'blob ' + str(len(old)).encode() + b'\0' + old).hexdigest()
            (source / 'framework/customization-copy-history.json').write_text(json.dumps({path: [identity]}))
            marker = rom / 'frameworks/base/core/java/android/content/res/Resources.java'
            marker.parent.mkdir(parents=True); marker.touch()
            target = rom / path; target.write_bytes(old)
            with patch.object(customization_port, 'ROOT', source):
                changes = customization_port.customization_plan(rom, replace_guarded)
                self.assertEqual(b'new adapter', changes[target])
                target.write_bytes(b'private local edit')
                with self.assertRaises(ValueError): customization_port.customization_plan(rom, replace_guarded)


if __name__ == '__main__': unittest.main()

# SPDX-License-Identifier: Apache-2.0
"""Verify the actual shipped glyphs and lossless preparation of the Pixel font input."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from google_sans_cyrillic import CONFIG, FONT_ROOT, inputs, render, google_sans_cyrillic_plan


class GoogleSansCyrillicTests(unittest.TestCase):
    def test_shipped_fonts_cover_russian_without_taking_latin_or_digits(self):
        inputs()  # Verify the immutable payload hashes as well as cmap contents.
        required = set(map(ord, 'АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ'
                               'абвгдеёжзийклмнопрстуфхцчшщъыьэюя'))
        lock = json.loads((FONT_ROOT / 'ORIGIN.json').read_text())
        for name in lock['files']:
            with TTFont(FONT_ROOT / name) as font:
                self.assertTrue(required <= set(font.getBestCmap()), name)
                self.assertFalse(set(map(ord, 'ABCabc0123456789')) & set(font.getBestCmap()))
                self.assertTrue({'GSUB', 'GPOS', 'fvar'} <= set(font.keys()))
                axes = {axis.axisTag: axis for axis in font['fvar'].axes}
                self.assertLessEqual(axes['wght'].minValue, 400)
                self.assertGreaterEqual(axes['wght'].maxValue, 700)

    def test_named_fonts_aliases_and_existing_faces_are_retained(self):
        original = inputs()
        before, after = ET.fromstring(original), ET.fromstring(render(original))
        names = lambda root: {node.get('name') for node in root if node.get('name')}
        self.assertEqual(names(before), names(after))
        self.assertEqual([node.attrib for node in before.findall('alias')],
                         [node.attrib for node in after.findall('alias')])
        for node in before:
            name = node.get('name')
            if not name or node.tag not in ('family', 'family-list'):
                continue
            result = next(item for item in after if item.get('name') == name)
            old_fonts = [(font.text or '').strip() for font in node.iter('font')]
            new_fonts = [(font.text or '').strip() for font in result.iter('font')]
            self.assertEqual(old_fonts, [text for text in new_fonts if not text.startswith('Hecker')])
            if 'clock' in name:
                self.assertEqual(node.tag, result.tag)
                self.assertFalse(any(text.startswith('Hecker') for text in new_fonts))
            else:
                self.assertEqual(result.tag, 'family-list')
                self.assertEqual(result[0].get('lang'), 'und-Cyrl')
                self.assertEqual(len(result[0].findall('font')), 8)

    def test_prepare_repeat_revert_and_unknown_input_refusal(self):
        original = inputs()
        with tempfile.TemporaryDirectory() as folder:
            rom = Path(folder)
            path = rom / CONFIG
            path.parent.mkdir(parents=True)
            path.write_bytes(original)
            changes = google_sans_cyrillic_plan(rom)
            self.assertEqual(changes, {path: render(original)})
            path.write_bytes(changes[path])
            self.assertEqual(google_sans_cyrillic_plan(rom), {})
            changes = google_sans_cyrillic_plan(rom, revert=True)
            self.assertEqual(changes, {path: original})
            path.write_bytes(changes[path])
            self.assertEqual(google_sans_cyrillic_plan(rom, revert=True), {})
            path.write_bytes(original + b'<!-- local font changes -->')
            with self.assertRaisesRegex(ValueError, 'unfamiliar'):
                google_sans_cyrillic_plan(rom)


if __name__ == '__main__':
    unittest.main()

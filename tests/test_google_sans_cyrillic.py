# SPDX-License-Identifier: Apache-2.0
"""Verify the actual shipped glyphs and lossless preparation of the Pixel font input."""
import hashlib
import json
from pathlib import Path
import sys
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from google_sans_cyrillic import (CONFIG, FONT_ROOT, LEGACY_PREPARED_SHA256, inputs, render,
                                  google_sans_cyrillic_plan, validate_named_families,
                                  check_installed_fonts)
from check_font_config import compile_parser, run_parser


def legacy_config():
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    root = ET.fromstring(render(inputs()), parser=parser)
    for group in root.findall('family-list'):
        family = group.find('family')
        if family is not None and (family.findtext('font') or '').startswith('Hecker'):
            family.set('lang', 'und-Cyrl')
    ET.indent(root, space='  ')
    content = ET.tostring(root, encoding='utf-8', xml_declaration=True) + b'\n'
    if hashlib.sha256(content).hexdigest() != LEGACY_PREPARED_SHA256:
        raise AssertionError('Fixture must reproduce the exact published boot-breaking output')
    return content


class GoogleSansCyrillicTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parser_folder = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.parser_folder.cleanup)
        cls.classes = compile_parser(cls.parser_folder.name)

    def parse_on_host(self, content):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            config = root / 'fonts_customization.xml'
            config.write_bytes(content)
            fonts = root / 'fonts'
            fonts.mkdir()
            # Android's parser filters nonexistent fonts. Stage every referenced
            # filename so validation cannot pass by dropping the original faces.
            # Native font data/rendering is outside this host parser check.
            for font in ET.fromstring(content).iter('font'):
                (fonts / (font.text or '').strip()).touch()
            return run_parser(self.classes, config, fonts)

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
                self.assertEqual(result[0].attrib, {})
                self.assertEqual(len(result[0].findall('font')), 8)

    def test_android_parser_reproduces_boot_failure_and_accepts_fixed_configuration(self):
        broken = self.parse_on_host(legacy_config())
        self.assertNotEqual(broken.returncode, 0)
        self.assertIn('lang cannot be used in FontFamily', broken.stderr)
        self.assertIn('FontListParser.readNamedFamilyList', broken.stderr)
        expected = sum(node.get('customizationType') == 'new-named-family'
                       for node in ET.fromstring(inputs()))
        for content in (inputs(), render(inputs())):
            with self.subTest(prepared=content != inputs()):
                parsed = self.parse_on_host(content)
                self.assertEqual(parsed.returncode, 0, parsed.stderr)
                self.assertEqual(int(parsed.stdout), expected)

    def test_named_family_attribute_guards_match_android_parser(self):
        for tag, attributes in (('family', ('lang', 'variant', 'ignore')),
                                ('family-list', ('name', 'lang', 'variant', 'ignore'))):
            for attribute in attributes:
                for value in ('und-Cyrl', ''):
                    with self.subTest(tag=tag, attribute=attribute, value=value):
                        root = ET.fromstring(render(inputs()))
                        group = next(node for node in root if node.tag == tag)
                        family = group if tag == 'family' else group.find('family')
                        family.set(attribute, value)
                        with self.assertRaisesRegex(ValueError, attribute + ' cannot be used'):
                            validate_named_families(root)
                        parsed = self.parse_on_host(ET.tostring(root))
                        self.assertNotEqual(parsed.returncode, 0)
                        self.assertIn(attribute + ' cannot be used', parsed.stderr)

    def test_language_attribute_remains_valid_for_locale_fallback_families(self):
        root = ET.fromstring(render(inputs()))
        family = ET.SubElement(root, 'family', {'customizationType': 'new-locale-family',
                                               'lang': 'ru', 'operation': 'prepend'})
        ET.SubElement(family, 'font').text = 'HeckerGoogleSansCyrillic-Regular.ttf'
        validate_named_families(root)
        parsed = self.parse_on_host(ET.tostring(root))
        self.assertEqual(parsed.returncode, 0, parsed.stderr)

    def test_exact_previous_output_upgrades_and_reverts_without_accepting_local_edits(self):
        with tempfile.TemporaryDirectory() as folder:
            rom = Path(folder)
            path = rom / CONFIG
            path.parent.mkdir(parents=True)
            previous = legacy_config()
            path.write_bytes(previous)
            self.assertEqual(google_sans_cyrillic_plan(rom), {path: render(inputs())})
            self.assertEqual(google_sans_cyrillic_plan(rom, revert=True), {path: inputs()})
            path.write_bytes(google_sans_cyrillic_plan(rom)[path])
            self.assertEqual(google_sans_cyrillic_plan(rom), {})
            for modified in (previous + b'\n', previous.replace(b'und-Cyrl', b'ru', 1)):
                path.write_bytes(modified)
                with self.assertRaisesRegex(ValueError, 'unfamiliar'):
                    google_sans_cyrillic_plan(rom)
                self.assertEqual(path.read_bytes(), modified)

    def test_built_product_requires_correct_xml_and_both_exact_font_payloads(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            config = output / 'product/etc/fonts_customization.xml'
            config.parent.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, 'font customization'):
                check_installed_fonts(output)
            for stale in (inputs(), legacy_config(), render(inputs()) + b'<!-- stale -->'):
                config.write_bytes(stale)
                with self.assertRaisesRegex(ValueError, 'font customization'):
                    check_installed_fonts(output)
            config.write_bytes(render(inputs()))
            fonts = output / 'product/fonts'
            fonts.mkdir()
            lock = json.loads((FONT_ROOT / 'ORIGIN.json').read_text())
            for name in lock['files']:
                shutil.copyfile(FONT_ROOT / name, fonts / name)
            check_installed_fonts(output)
            for name in lock['files']:
                path = fonts / name
                correct = path.read_bytes()
                for corrupted in (b'', b'x' * len(correct)):
                    path.write_bytes(corrupted)
                    with self.assertRaisesRegex(ValueError, 'exact Cyrillic font'):
                        check_installed_fonts(output)
                path.unlink()
                with self.assertRaisesRegex(ValueError, 'exact Cyrillic font'):
                    check_installed_fonts(output)
                path.write_bytes(correct)
            check_installed_fonts(output)

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

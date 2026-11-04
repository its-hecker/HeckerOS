# SPDX-License-Identifier: Apache-2.0
"""Verify the shipped clock designs/fonts against their pinned upstream bytes."""
from pathlib import Path
import hashlib
import json
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LIB = ROOT/'third_party/xmorphclock'


class XMorphClockTests(unittest.TestCase):
    def test_all_upstream_clock_presets_fonts_and_notices_are_packaged(self):
        lock = json.loads((LIB/'upstream.lock.json').read_text())
        counts = {'clock_faces': 0, 'font': 0, 'licenses': 0}
        for path, digest in lock['files'].items():
            if '/assets/clock_faces/' in path:
                target = LIB/'assets/clock_faces'/Path(path).name; counts['clock_faces'] += 1
            elif '/res/font/' in path:
                target = LIB/'res/font'/('xmorph_'+Path(path).name); counts['font'] += 1
            elif '/assets/licenses/' in path:
                target = LIB/'assets/xmorph_licenses'/Path(path).name; counts['licenses'] += 1
            else:
                continue
            self.assertEqual(hashlib.sha256(target.read_bytes()).hexdigest(), digest, path)
        self.assertEqual(counts['clock_faces'], 86)
        self.assertEqual(counts['font'], 47)
        self.assertGreaterEqual(counts['licenses'], 47)

    def test_renderer_resource_references_resolve_and_runtime_has_no_module_dependency(self):
        resources = {node.get('name') for p in (LIB/'res/values').glob('*.xml') for node in ET.parse(p).getroot()}
        fonts = {p.stem for p in (LIB/'res/font').iterdir()}
        sources = '\n'.join(p.read_text() for p in (LIB/'src').rglob('*.kt'))
        for kind, name in re.findall(r'R\.(string|font)\.(\w+)', sources):
            self.assertIn(name, fonts if kind == 'font' else resources)
        self.assertNotRegex(sources, r'import\s+(?:io\.github\.libxposed|com\.[\w.]+\.xposed)')
        self.assertNotIn('ProcessBuilder(', sources)

    def test_picker_is_private_and_localized(self):
        android = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT/'app/AndroidManifest.xml').getroot()
        activity = next(x for x in manifest.findall('application/activity') if x.get(android+'name')=='.XMorphClockActivity')
        self.assertEqual(activity.get(android+'exported'), 'false')
        default = {x.get('name') for x in ET.parse(ROOT/'app/res/values/xmorph_clock.xml').getroot()}
        for locale in ('values-ru', 'values-zh-rCN'):
            self.assertEqual(default, {x.get('name') for x in ET.parse(ROOT/'app/res'/locale/'xmorph_clock.xml').getroot()})


if __name__ == '__main__': unittest.main()

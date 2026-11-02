# SPDX-License-Identifier: Apache-2.0
"""Add a Cyrillic-only Google Sans family to the pinned Pixel font configuration."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FONT_ROOT = ROOT / 'fonts/google-sans-cyrillic'
CONFIG = 'vendor/pixel/gsans/common/proprietary/product/etc/fonts_customization.xml'


def inputs():
    lock = json.loads((FONT_ROOT / 'ORIGIN.json').read_text())
    original = (FONT_ROOT / 'pixelos-fonts-customization.xml').read_bytes()
    if hashlib.sha256(original).hexdigest() != lock['pixelos_input']['sha256']:
        raise ValueError('Pixel font configuration input differs from its immutable lock')
    if hashlib.sha256((FONT_ROOT / 'OFL.txt').read_bytes()).hexdigest() != lock['inputs']['OFL.txt']['sha256']:
        raise ValueError('Bundled Google Sans license differs from its immutable lock')
    for name, info in lock['files'].items():
        content = (FONT_ROOT / name).read_bytes()
        if len(content) != info['size'] or hashlib.sha256(content).hexdigest() != info['sha256']:
            raise ValueError('Cyrillic font differs from its immutable lock: ' + name)
    return original


def render(original):
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    root = ET.fromstring(original, parser=parser)
    for index, node in enumerate(list(root)):
        name = node.get('name', '')
        if node.get('customizationType') != 'new-named-family' or 'clock' in name:
            continue
        if node.tag == 'family':
            group = ET.Element('family-list', dict(node.attrib))
            node.attrib = {key: value for key, value in node.attrib.items()
                           if key not in {'name', 'customizationType'}}
            group.append(node)
            root.remove(node)
            root.insert(index, group)
        elif node.tag == 'family-list':
            group = node
        else:
            raise ValueError('Unknown named Pixel font family: ' + str(node.tag))
        # The subset has no Latin/digit glyphs. Those keep using the original
        # Flex designs and axes; Cyrillic resolves here before a Roboto fallback.
        cyrillic = ET.Element('family', {'lang': 'und-Cyrl'})
        for style in ('normal', 'italic'):
            for weight in (400, 500, 600, 700):
                font = ET.SubElement(cyrillic, 'font', {'weight': str(weight), 'style': style})
                font.text = 'HeckerGoogleSansCyrillic-' + ('Italic' if style == 'italic' else 'Regular') + '.ttf'
                for tag, value in [('wght', str(weight)), ('GRAD', '0'),
                                   ('opsz', '17' if 'text' in name or 'body' in name else '18')]:
                    ET.SubElement(font, 'axis', {'tag': tag, 'stylevalue': value})
        group.insert(0, cyrillic)
    ET.indent(root, space='  ')
    return ET.tostring(root, encoding='utf-8', xml_declaration=True) + b'\n'


def google_sans_cyrillic_plan(rom, revert=False):
    path = rom / CONFIG
    if not path.is_file():
        return {}
    original = inputs()
    prepared = render(original)
    current = path.read_bytes()
    if current not in (original, prepared):
        raise ValueError('Preserving unfamiliar Pixel font customization: ' + str(path))
    target = original if revert else prepared
    return {path: target} if current != target else {}


if __name__ == '__main__':
    original = inputs()
    print('Pinned Cyrillic fonts and Pixel font configuration: PASS')
    print('Named families with Cyrillic fallback:', len(ET.fromstring(render(original)).findall('family-list')))

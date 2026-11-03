# SPDX-License-Identifier: Apache-2.0
"""Add a Cyrillic-only Google Sans family to the pinned Pixel font configuration."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FONT_ROOT = ROOT / 'fonts/google-sans-cyrillic'
CONFIG = 'vendor/pixel/gsans/common/proprietary/product/etc/fonts_customization.xml'
# Exact output of this adapter at Suite 335d398d9fe3a0e2f487424be667b1d8cad10f7f.
# Accept it only for migration/revert; its nested lang attribute kills Zygote.
LEGACY_PREPARED_SHA256 = 'e606e8315cad959520194c7d23e5665065a96f2058b63fd96d6088b78467ce61'


def validate_named_families(root):
    """Enforce the named-family rules in Android 17's FontListParser.

    Language selection is valid for locale fallback families, but not for a
    named family or any family nested inside a named family-list.
    """
    names = set()
    for node in root:
        if node.get('customizationType') != 'new-named-family':
            continue
        name = node.get('name')
        if name is None or name in names:
            raise ValueError('new-named-family requires a unique name attribute')
        names.add(name)
        if node.tag == 'family':
            families, forbidden = [node], ('lang', 'variant', 'ignore')
        elif node.tag == 'family-list':
            families, forbidden = node.findall('family'), ('name', 'lang', 'variant', 'ignore')
        else:
            raise ValueError('Unknown named Pixel font family: ' + str(node.tag))
        for family in families:
            for attribute in forbidden:
                if attribute in family.attrib:
                    raise ValueError(attribute + ' cannot be used in named font family ' + name)


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
        # Named lists forbid lang/variant/ignore on their child families.
        # The font's Cyrillic-only cmap provides script coverage on its own.
        cyrillic = ET.Element('family')
        for style in ('normal', 'italic'):
            for weight in (400, 500, 600, 700):
                font = ET.SubElement(cyrillic, 'font', {'weight': str(weight), 'style': style})
                font.text = 'HeckerGoogleSansCyrillic-' + ('Italic' if style == 'italic' else 'Regular') + '.ttf'
                for tag, value in [('wght', str(weight)), ('GRAD', '0'),
                                   ('opsz', '17' if 'text' in name or 'body' in name else '18')]:
                    ET.SubElement(font, 'axis', {'tag': tag, 'stylevalue': value})
        group.insert(0, cyrillic)
    validate_named_families(root)
    ET.indent(root, space='  ')
    return ET.tostring(root, encoding='utf-8', xml_declaration=True) + b'\n'


def google_sans_cyrillic_plan(rom, revert=False):
    path = rom / CONFIG
    if not path.is_file():
        return {}
    original = inputs()
    prepared = render(original)
    current = path.read_bytes()
    if current not in (original, prepared) and hashlib.sha256(current).hexdigest() != LEGACY_PREPARED_SHA256:
        raise ValueError('Preserving unfamiliar Pixel font customization: ' + str(path))
    target = original if revert else prepared
    return {path: target} if current != target else {}


def check_installed_fonts(output):
    """Reject stale font XML/payloads in PRODUCT_OUT before accepting a build."""
    expected = render(inputs())
    installed = output / 'product/etc/fonts_customization.xml'
    if not installed.is_file() or installed.read_bytes() != expected:
        raise ValueError('Built product font customization differs from the boot-safe Cyrillic configuration')
    lock = json.loads((FONT_ROOT / 'ORIGIN.json').read_text())
    for name, info in lock['files'].items():
        path = output / 'product/fonts' / name
        if not path.is_file() or path.stat().st_size != info['size'] or \
                hashlib.sha256(path.read_bytes()).hexdigest() != info['sha256']:
            raise ValueError('Built product is missing the exact Cyrillic font: ' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--product-out', type=Path)
    args = parser.parse_args()
    original = inputs()
    prepared = render(original)
    if args.product_out:
        check_installed_fonts(args.product_out)
        print('Installed boot-safe Pixel font configuration and Cyrillic payloads: PASS')
    print('Pinned Cyrillic fonts and Pixel font configuration: PASS')
    print('Named families with Cyrillic fallback:', len(ET.fromstring(prepared).findall('family-list')))


if __name__ == '__main__':
    main()

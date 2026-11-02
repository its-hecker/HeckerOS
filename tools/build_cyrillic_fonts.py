#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Reproduce the Cyrillic-only Google Sans payloads from immutable upstream fonts."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.parse
import urllib.request
import fontTools
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / 'fonts/google-sans-cyrillic'


def build(lock, name, data):
    if fontTools.version != lock['fonttools_version']:
        raise ValueError('Use fonttools==' + lock['fonttools_version'])
    font = TTFont(io.BytesIO(data), recalcTimestamp=False)
    options = subset.Options()
    options.layout_features = ['*']
    options.name_IDs = ['*']
    options.name_legacy = True
    options.name_languages = ['*']
    glyphs = subset.Subsetter(options=options)
    glyphs.populate(unicodes=list(range(0x400, 0x530)) + list(range(0x2de0, 0x2e00))
                   + list(range(0xa640, 0xa6a0)))
    glyphs.subset(font)
    out = io.BytesIO()
    font.save(out)
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / 'out/google-sans-sources')
    parser.add_argument('--write', action='store_true', help='Write generated fonts and update output hashes')
    args = parser.parse_args()
    lock_path = FONTS / 'ORIGIN.json'
    lock = json.loads(lock_path.read_text())
    args.cache.mkdir(parents=True, exist_ok=True)
    for name, info in lock['inputs'].items():
        cached = args.cache / name
        if not cached.is_file():
            url = ('https://raw.githubusercontent.com/' + lock['repository'] + '/' + lock['revision']
                   + '/' + lock['path'] + '/' + urllib.parse.quote(name, safe=''))
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read(info['size'] + 1)
            if len(data) != info['size'] or hashlib.sha256(data).hexdigest() != info['sha256']:
                raise ValueError('Upstream font input differs: ' + name)
            cached.write_bytes(data)
        data = cached.read_bytes()
        if len(data) != info['size'] or hashlib.sha256(data).hexdigest() != info['sha256']:
            raise ValueError('Cached font input differs: ' + name)
        if name == 'OFL.txt':
            if data != (FONTS / name).read_bytes():
                raise ValueError('Bundled font license differs')
            continue
        output_name = 'HeckerGoogleSansCyrillic-' + ('Italic' if 'Italic' in name else 'Regular') + '.ttf'
        output = build(lock, name, data)
        digest = hashlib.sha256(output).hexdigest()
        if args.write:
            (FONTS / output_name).write_bytes(output)
            lock['files'][output_name] = {'sha256': digest, 'size': len(output)}
        elif (FONTS / output_name).read_bytes() != output or lock['files'][output_name]['sha256'] != digest:
            raise ValueError('Font does not reproduce byte-for-byte: ' + output_name)
        print(output_name + ': reproducible Cyrillic subset verified')
    if args.write:
        lock_path.write_text(json.dumps(lock, indent=2) + '\n')


if __name__ == '__main__':
    main()

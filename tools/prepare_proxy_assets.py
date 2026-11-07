#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Prepare checksum-locked arm64 TCP core and safe YAML parser before Soong."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def verified(data, lock):
    return len(data) == lock['size'] and hashlib.sha256(data).hexdigest() == lock['sha256']

def download(path, lock, offline=False):
    if path.exists():
        if not verified(path.read_bytes(), lock):
            raise ValueError('Checksum differs: ' + path.name)
        return path
    if offline:
        raise ValueError('Missing verified TCP asset: ' + path.name)
    req = urllib.request.Request(lock['url'], headers={'User-Agent': 'HeckerVPN-build/1'})
    with urllib.request.urlopen(req, timeout=60) as response:
        data = response.read(lock['size'] + 1)
    if not verified(data, lock):
        raise ValueError('Downloaded TCP asset checksum differs: ' + path.name)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.partial')
    temporary.write_bytes(data)
    temporary.replace(path)
    return path

def prepare(cache=None, offline=False):
    lock = json.loads((ROOT / 'prebuilts/mihomo/assets.lock.json').read_text())
    cache = cache or ROOT / 'generated/proxy-assets'
    target = ROOT / 'prebuilts/mihomo/arm64/libclash.so'
    if target.exists():
        if not verified(target.read_bytes(), lock['core']):
            raise ValueError('TCP core checksum differs')
    else:
        apk = download(cache / 'WhiteVPN-V1.7.1-arm64-v8a.apk', lock['apk'], offline)
        with zipfile.ZipFile(apk) as archive:
            entry = archive.getinfo(lock['core']['entry'])
            if entry.file_size != lock['core']['size']:
                raise ValueError('Unexpected TCP core archive size')
            data = archive.read(entry)
        if not verified(data, lock['core']):
            raise ValueError('Extracted TCP core checksum differs')
        if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
            raise ValueError('TCP core is not arm64 little-endian ELF')
        target.parent.mkdir(parents=True, exist_ok=True)
        staged = target.with_suffix('.so.partial')
        staged.write_bytes(data)
        staged.replace(target)
    download(ROOT / 'prebuilts/mihomo/snakeyaml.jar', lock['yaml'], offline)
    print('Pinned WhiteVPN 1.7.1 native core and SnakeYAML checksums: PASS')

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--cache', type=Path)
    p.add_argument('--offline', action='store_true')
    args = p.parse_args()
    prepare(args.cache, args.offline)

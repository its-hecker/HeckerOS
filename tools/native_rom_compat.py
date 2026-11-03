# SPDX-License-Identifier: Apache-2.0
"""Atomic, guarded Android 17 adapters for the reviewed VoltageOS ROM-visibility work."""
import json
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def native_rom_compat_plan(rom, replace, revert=False):
    if not (rom / 'bionic/libc/Android.bp').is_file():
        return {}
    changes = {}
    history_file = ROOT / 'native-compat/copy-history.json'
    history = json.loads(history_file.read_text()) if history_file.exists() else {}
    for relative, pairs in json.loads((ROOT / 'native-compat/patches.json').read_text()).items():
        path = rom / relative
        current = path.read_text()
        target = current
        for pair in reversed(pairs) if revert else pairs:
            target = replace(target, pair['old'], pair['new'], str(path), revert)
        if target != current:
            changes[path] = target
    for destination, source in json.loads((ROOT / 'native-compat/copies.json').read_text()).items():
        path = rom / destination
        content = (ROOT / source).read_bytes()
        if path.exists() and path.read_bytes() != content:
            current = path.read_bytes()
            sha = hashlib.sha1(b'blob ' + str(len(current)).encode() + b'\0' + current).hexdigest()
            if sha not in history.get(destination, []):
                raise ValueError('Preserving unfamiliar native ROM adapter: ' + str(path))
        if revert:
            if path.exists():
                changes[path] = None
        elif not path.exists() or path.read_bytes() != content:
            changes[path] = content
    return changes

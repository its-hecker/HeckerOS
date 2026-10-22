# SPDX-License-Identifier: Apache-2.0
"""Atomic source plan for the Android 17 lyrics and theme adapters."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def customization_plan(rom, replace, revert=False):
    base = rom / "frameworks/base"
    if not (base / "core/java/android/content/res/Resources.java").exists(): return {}
    changes = {}
    for relative, pairs in json.loads((ROOT / "framework/customization-patches.json").read_text()).items():
        path = rom / relative
        current = path.read_text()
        target = current
        for pair in reversed(pairs) if revert else pairs:
            # Upgrade only explicitly reviewed earlier adapters. A prepared
            # checkout must not require reverting every unrelated native port.
            if not revert:
                for previous in pair.get("previous", []):
                    if previous in target and pair["new"] not in target:
                        target = replace(target, previous, pair["new"], str(path))
            target = replace(target, pair["old"], pair["new"], str(path), revert)
        if target != current: changes[path] = target
    copies = json.loads((ROOT / "framework/customization-copies.json").read_text())
    for relative, source in copies.items():
        path = rom / relative
        expected = (ROOT / source).read_bytes()
        if path.exists() and path.read_bytes() != expected:
            raise ValueError("Preserving an unfamiliar HeckerOS adapter: " + str(path))
        if revert:
            if path.exists(): changes[path] = None
        elif not path.exists(): changes[path] = expected
    return changes

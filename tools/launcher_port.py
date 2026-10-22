# SPDX-License-Identifier: Apache-2.0
"""Guarded Ortus-to-PixelOS adapters; all changes join prepare.py's atomic plan."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = Path("packages/apps/Launcher3")


def launcher_plan(rom, replace, revert=False):
    launcher = rom / LAUNCHER
    if not launcher.is_dir(): return {}
    changes = {}
    for relative, pairs in json.loads((ROOT / "launcher/patches.json").read_text()).items():
        path = launcher / relative
        current = path.read_text()
        target = current
        for pair in reversed(pairs) if revert else pairs:
            if not revert:
                for previous in pair.get("previous", []):
                    if previous in target and pair["new"] not in target:
                        target = replace(target, previous, pair["new"], str(path))
            target = replace(target, pair["old"], pair["new"], str(path), revert)
        if target != current: changes[path] = target
    copies = {"src/com/android/launcher3/hecker/" + path.name: path
              for path in (ROOT / "launcher/src").glob("*.java")}
    copies.update({"generated/hecker-suite/res/" + str(path.relative_to(ROOT / "launcher/res")): path
                   for path in (ROOT / "launcher/res").rglob("*") if path.is_file()})
    upgrades = json.loads((ROOT / "launcher/copy-upgrades.json").read_text())
    for relative, source in copies.items():
        path = launcher / relative
        expected = source.read_text()
        current = path.read_text() if path.exists() else None
        reviewed = current is not None and hashlib.sha256(path.read_bytes()).hexdigest() in upgrades.get(relative, [])
        if current is not None and current != expected and not reviewed:
            raise ValueError("Preserving an unfamiliar Hecker Launcher file: " + str(path))
        if revert:
            if path.exists(): changes[path] = None
        elif current != expected: changes[path] = expected
    return changes

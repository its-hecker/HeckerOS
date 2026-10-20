# SPDX-License-Identifier: Apache-2.0
"""Guarded Ortus-to-PixelOS adapters; all changes join prepare.py's atomic plan."""
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
            target = replace(target, pair["old"], pair["new"], str(path), revert)
        if target != current: changes[path] = target
    copies = {"src/com/android/launcher3/hecker/" + path.name: path
              for path in (ROOT / "launcher/src").glob("*.java")}
    copies.update({"generated/hecker-suite/res/" + str(path.relative_to(ROOT / "launcher/res")): path
                   for path in (ROOT / "launcher/res").rglob("*") if path.is_file()})
    for relative, source in copies.items():
        path = launcher / relative
        expected = source.read_text()
        if path.exists() and path.read_text() != expected:
            raise ValueError("Preserving an unfamiliar Hecker Launcher file: " + str(path))
        if revert:
            if path.exists(): changes[path] = None
        elif not path.exists(): changes[path] = expected
    return changes

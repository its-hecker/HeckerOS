# SPDX-License-Identifier: Apache-2.0
"""Guarded recovery branding updates, included in the existing atomic source plan."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def recovery_plan(rom, revert=False):
    recovery = rom / "bootable/recovery"
    if not recovery.exists():
        return {}
    changes = {}
    for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        relative = f"res-{density}/images/logo_image.png"
        path = recovery / relative
        original = (ROOT / "recovery/upstream" / (density + ".png")).read_bytes()
        replacement = (ROOT / "recovery" / relative).read_bytes()
        if not path.exists() or path.read_bytes() not in (original, replacement):
            raise ValueError("Preserving unfamiliar recovery artwork: " + str(path))
        expected = original if revert else replacement
        if path.read_bytes() != expected:
            changes[path] = expected
    return changes

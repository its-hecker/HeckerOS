# SPDX-License-Identifier: Apache-2.0
"""Atomic source plan for the Android 17 lyrics and theme adapters."""
import json
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SYSTEM_SERVER = "frameworks/base/services/java/com/android/server/SystemServer.java"


def verify_theme_service_startup(source):
    start = "mSystemServiceManager.startService(com.android.server.theme.ThemeEngineManagerService.class);"
    phase = "mSystemServiceManager.startBootPhase(t, SystemService.PHASE_SYSTEM_SERVICES_READY);"
    if source.count(start) != 1 or source.count(phase) != 1 or source.index(start) > source.index(phase):
        raise ValueError("Theme engine must start exactly once before system-services-ready; "
                         "a late start misses its settings observer boot phase")


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
        if not revert and relative == SYSTEM_SERVER:
            verify_theme_service_startup(target)
        if target != current: changes[path] = target
    copies = json.loads((ROOT / "framework/customization-copies.json").read_text())
    history_file = ROOT / "framework/customization-copy-history.json"
    history = json.loads(history_file.read_text()) if history_file.exists() else {}
    for relative, source in copies.items():
        path = rom / relative
        expected = (ROOT / source).read_bytes()
        if path.exists() and path.read_bytes() != expected:
            current = path.read_bytes()
            identity = hashlib.sha1(b"blob " + str(len(current)).encode() + b"\0" + current).hexdigest()
            if identity not in history.get(relative, []):
                raise ValueError("Preserving an unfamiliar HeckerOS adapter: " + str(path))
        if revert:
            if path.exists(): changes[path] = None
        elif not path.exists() or path.read_bytes() != expected: changes[path] = expected
    return changes

# SPDX-License-Identifier: Apache-2.0
"""Migrate the old gaming Binder fallback to the ROM-owned service label."""

from pathlib import Path


SERVICE = "hecker_game"
CONTEXT = "u:object_r:hecker_game_service:s0"
LEGACY_CONTEXT = "u:object_r:activity_service:s0"
CANONICAL = Path("vendor/hecker/floral/suite/rom/sepolicy/private/service_contexts")


def game_service_plan(rom, revert=False):
    # Reverting UI adapters must never restore a conflicting SELinux mapping.
    if revert:
        return {}
    canonical = rom / CANONICAL
    if not canonical.is_file():
        return {}  # Standalone Suite preparation has no ROM-owned game policy.
    entries = [line.split("#", 1)[0].split()
               for line in canonical.read_text().splitlines()]
    if [entry for entry in entries if entry and entry[0] == SERVICE] != [[SERVICE, CONTEXT]]:
        raise ValueError("Gaming policy needs exactly one dedicated service label: " + str(canonical))

    changes = {}
    for root in (rom / "system/sepolicy", rom / "device", rom / "vendor"):
        for path in sorted(root.rglob("*service_contexts")):
            if not path.is_file() or path.resolve() == canonical.resolve():
                continue
            current = path.read_text()
            retained = []
            for line in current.splitlines(keepends=True):
                fields = line.split("#", 1)[0].split()
                if fields and fields[0] == SERVICE:
                    if len(fields) != 2 or fields[1] not in {CONTEXT, LEGACY_CONTEXT}:
                        raise ValueError("Unfamiliar gaming service label; preserving " + str(path))
                    if fields[1] == LEGACY_CONTEXT:
                        continue
                retained.append(line)
            target = "".join(retained)
            if target != current:
                changes[path] = target
    return changes

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Migrate the old gaming Binder fallback to the ROM-owned service label."""

import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import sys


SERVICE = "hecker_game"
CONTEXT = "u:object_r:hecker_game_service:s0"
LEGACY_CONTEXT = "u:object_r:activity_service:s0"
CANONICAL = Path("vendor/hecker/floral/suite/rom/sepolicy/private/service_contexts")


def context_files(rom):
    # Policy may also be supplied by hardware/, frameworks/ or another project.
    # Never treat outputs, .repo copies or our backups as editable source inputs.
    # The platform-private input produces plat_service_contexts. Check it
    # explicitly: os.walk does not descend into linked project directories.
    platform = rom / "system/sepolicy/private/service_contexts"
    if platform.is_file():
        yield platform
    skipped = {".git", ".repo", "out", ".cache", "node_modules", "__pycache__"}
    for root, directories, filenames in os.walk(rom):
        directories[:] = sorted(name for name in directories if name not in skipped)
        for name in sorted(filenames):
            if name.removesuffix(".in").removesuffix(".m4").endswith("service_contexts"):
                path = Path(root) / name
                if path != platform:
                    yield path


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
    for path in context_files(rom):
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


def clear_context_cache(rom):
    root = rom / "out/soong/.intermediates/system/sepolicy/contexts"
    cleared = []
    if root.is_dir():
        for path in sorted(root.iterdir()):
            if path.name.removesuffix(".recovery").endswith(("service_contexts", "service_contexts_test")):
                if path.is_symlink() or path.is_file():
                    path.unlink()
                else:
                    shutil.rmtree(path)
                cleared.append(path)
    return cleared


def repair(rom, apply=False, clear_cache=False):
    print("Gaming SELinux repair: platform-source scan v3", flush=True)
    print("Repair tool:", Path(__file__).resolve(), flush=True)
    canonical = rom / CANONICAL
    if not canonical.is_file():
        raise ValueError("Missing ROM-owned gaming policy; sync vendor/hecker/floral/suite: " + str(canonical))
    changes = game_service_plan(rom)
    print("Keep:", str(CANONICAL), SERVICE, CONTEXT, flush=True)
    for path in changes:
        print("Remove obsolete gaming label:", path.relative_to(rom), flush=True)
    if changes and not apply:
        raise ValueError("Conflicting gaming labels remain; rerun this tool with --apply")
    if apply and changes:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        backup = rom / "out/hecker-service-context-backups" / stamp
        originals = {path: path.read_bytes() for path in changes}
        for path in changes:
            destination = backup / path.relative_to(rom)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
        written = []
        try:
            for path, content in changes.items():
                temporary = path.with_name(path.name + ".hecker-tmp")
                temporary.write_text(content)
                temporary.replace(path)
                written.append(path)
        except OSError:
            for path in reversed(written):
                path.write_bytes(originals[path])
            raise
        finally:
            for path in changes:
                path.with_name(path.name + ".hecker-tmp").unlink(missing_ok=True)
        print("Originals saved under:", backup.relative_to(rom), flush=True)
        if game_service_plan(rom):
            raise ValueError("Gaming labels changed during repair; inspect the source before building")
    if apply and (changes or clear_cache):
        cleared = clear_context_cache(rom)
        print("Cleared cached service-context modules:", len(cleared), flush=True)
    print("Gaming service labels: consistent with hecker_game_service", flush=True)
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-root", type=Path, default=Path.cwd())
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--check", action="store_true")
    parser.add_argument("--clear-cache", action="store_true",
                        help="Regenerate only cached service contexts and their tests")
    args = parser.parse_args()
    if args.clear_cache and not args.apply:
        parser.error("--clear-cache requires --apply")
    try:
        repair(args.rom_root.resolve(), args.apply, args.clear_cache)
    except (OSError, ValueError) as error:
        print("Gaming SELinux repair:", error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

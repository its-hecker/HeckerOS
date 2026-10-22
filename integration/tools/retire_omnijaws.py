#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Preserve the retired, locally adapted OmniJaws checkout before repo sync."""

from pathlib import Path
import shutil
import sys
import tempfile


def retire(rom):
    rom = Path(rom).resolve()
    if not (rom / ".repo").is_dir() or not (rom / "build/envsetup.sh").is_file():
        raise ValueError("Run from the Android source root containing .repo and build/envsetup.sh")
    source = rom / "packages/services/OmniJaws"
    if source.is_symlink() or not source.resolve().is_relative_to(rom):
        raise ValueError("Preserving unexpected OmniJaws source symlink; review it before syncing")
    if not source.exists():
        print("OmniJaws checkout is already absent; ready to sync.")
        return None
    if not source.is_dir():
        raise ValueError("Preserving unexpected non-directory OmniJaws source")
    backups = rom / "out/retired-projects"
    if not backups.resolve().is_relative_to(rom):
        raise ValueError("The retired-project backup directory must stay inside this checkout")
    backups.mkdir(parents=True, exist_ok=True)
    destination = Path(tempfile.mkdtemp(prefix="omnijaws-", dir=backups)) / "OmniJaws"
    shutil.move(str(source), str(destination))
    print("Preserved retired OmniJaws source at " + str(destination))
    return destination


if __name__ == "__main__":
    try:
        retire(Path.cwd())
    except (OSError, ValueError, shutil.Error) as error:
        print("OmniJaws retirement:", error, file=sys.stderr)
        sys.exit(1)

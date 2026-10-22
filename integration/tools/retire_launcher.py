#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Preserve the old RisingOS launcher and local edits before syncing Infinity-X."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

OLD = "RisingOS-Revived/android_packages_apps_Launcher3"
NEW = "projectinfinity-x/packages_apps_launcher3"


def retire(rom):
    rom = Path(rom).resolve()
    if not (rom / ".repo").is_dir() or not (rom / "build/envsetup.sh").is_file():
        raise ValueError("Run from the Android source root containing .repo and build/envsetup.sh")
    source = rom / "packages/apps/Launcher3"
    if source.is_symlink() or not source.resolve().is_relative_to(rom):
        raise ValueError("Preserving unexpected launcher source symlink; review it before syncing")
    if not source.exists():
        print("Launcher checkout is absent; ready to sync Infinity-X.")
        return None
    if not source.is_dir():
        raise ValueError("Preserving unexpected non-directory launcher source")
    def git(*args):
        return subprocess.check_output(["git", "-C", str(source), *args], stderr=subprocess.PIPE)
    urls = git("config", "--get-regexp", r"^remote\..*\.url$").decode()
    if NEW in urls.lower() and OLD not in urls:
        print("Infinity-X launcher is already selected; preserving the current checkout.")
        return None
    if OLD not in urls:
        raise ValueError("Preserving an unfamiliar launcher repository; review its source before migrating")
    revision = git("rev-parse", "HEAD").decode().strip()
    changes = git("diff", "--binary", "HEAD")
    backups = rom / "out/retired-projects"
    if not backups.resolve().is_relative_to(rom):
        raise ValueError("Launcher backups must stay inside this Android checkout")
    backups.mkdir(parents=True, exist_ok=True)
    folder = Path(tempfile.mkdtemp(prefix="rising-launcher-", dir=backups))
    (folder / "REVISION.txt").write_text(OLD + "\n" + revision + "\n")
    (folder / "tracked-changes.patch").write_bytes(changes)
    destination = folder / "Launcher3"
    # The complete source directory preserves tracked and untracked files. The
    # separate patch remains usable even if repo replaces its shared Git metadata.
    shutil.move(str(source), str(destination))
    print("Preserved previous launcher and local edits at " + str(destination))
    print("Now sync packages/apps/Launcher3 with repo sync --force-sync.")
    return destination


if __name__ == "__main__":
    try:
        retire(Path.cwd())
    except (OSError, ValueError, shutil.Error, subprocess.CalledProcessError) as error:
        print("Launcher migration:", error, file=sys.stderr)
        sys.exit(1)

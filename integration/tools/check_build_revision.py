#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Stop a build when the synced core projects differ from the selected manifest."""
import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

CORE = {"vendor/hecker/floral/suite", "frameworks/base", "packages/apps/Settings"}


def head(path):
    return subprocess.check_output(["git", "-C", str(path), "rev-parse", "HEAD"], text=True).strip()


def check(rom, manifest):
    nodes = ET.parse(manifest / "floral.xml").getroot()
    projects = {p.get("path"): p.get("revision", "") for p in nodes
                if p.tag in {"project", "extend-project"}}
    for path in sorted(CORE):
        expected = projects.get(path, "")
        if not re.fullmatch(r"[0-9a-f]{40}", expected):
            raise ValueError("Core project must be pinned to a commit: " + path)
        actual = head(rom / path)
        if actual != expected:
            raise ValueError(f"Revision mismatch for {path}: selected {expected}, synced {actual}. Run repo sync before building.")
        print(f"Build source: {path} = {actual}")
    suite = rom / "vendor/hecker/floral/suite"
    dirty = subprocess.check_output(["git", "-C", str(suite), "status", "--porcelain", "--untracked-files=normal"], text=True)
    if dirty:
        raise ValueError("Suite contains local source changes. Commit or preserve them before building a manifest-pinned release.")
    print("Build manifest: " + head(manifest))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    try:
        check(args.rom_root.resolve(), Path(__file__).resolve().parents[1])
    except (OSError, ValueError, subprocess.CalledProcessError) as failure:
        raise SystemExit("HeckerOS build revision check: " + str(failure))

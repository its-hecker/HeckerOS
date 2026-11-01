#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Prepare pinned depth inputs before Soong; no dependency binaries live in Git."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def valid(path, expected):
    return path.is_file() and not path.is_symlink() and digest(path) == expected


def prepare(offline=False, source=None):
    lock = json.loads((ROOT / "prebuilts/depth/inputs.lock.json").read_text())
    missing = {name for name, expected in lock["files"].items()
               if not valid(ROOT / name, expected)}
    if not missing:
        print("Depth model and LiteRT inputs match all four pinned hashes.")
        return
    if offline:
        raise ValueError("Missing or damaged depth inputs: " + ", ".join(sorted(missing))
                         + "; run python3 tools/fetch_depth_assets.py while online")
    for relative in lock["files"]:
        path = ROOT / relative
        if path.is_symlink() or any(parent.is_symlink() for parent in path.parents
                                    if parent.is_relative_to(ROOT)):
            raise ValueError("Refusing symlinked depth input: " + relative)
    cache = ROOT / "generated/depth-inputs"
    cache.mkdir(parents=True, exist_ok=True)
    for name, item in lock["downloads"].items():
        outputs = item["outputs"]
        if not missing.intersection(outputs.values()):
            continue
        target = cache / name
        if not valid(target, item["sha256"]) or target.stat().st_size != item["size"]:
            with tempfile.TemporaryDirectory(dir=cache) as temporary:
                staged = Path(temporary) / name
                if source is not None:
                    shutil.copyfile(source / name, staged)
                else:
                    request = urllib.request.Request(item["url"], headers={
                        "User-Agent": "HeckerOS-pinned-depth/1"})
                    with urllib.request.urlopen(request, timeout=45) as response, staged.open("wb") as output:
                        size = 0
                        while block := response.read(65536):
                            size += len(block)
                            if size > item["size"]:
                                raise ValueError("Depth download exceeds locked size: " + name)
                            output.write(block)
                if staged.stat().st_size != item["size"] or not valid(staged, item["sha256"]):
                    raise ValueError("Depth download differs from locked bytes: " + name)
                staged.replace(target)
        with tempfile.TemporaryDirectory(dir=cache) as temporary:
            for member, relative in outputs.items():
                output = ROOT / relative
                staged = Path(temporary) / output.name
                if member:
                    with zipfile.ZipFile(target) as archive:
                        staged.write_bytes(archive.read(member))
                else:
                    shutil.copyfile(target, staged)
                if not valid(staged, lock["files"][relative]):
                    raise ValueError("Extracted depth input differs from locked bytes: " + relative)
                output.parent.mkdir(parents=True, exist_ok=True)
                staged.replace(output)
    for relative, expected in lock["files"].items():
        if not valid(ROOT / relative, expected):
            raise ValueError("Depth input failed final verification: " + relative)
    print("Prepared the four checksum-verified depth model and LiteRT inputs.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Verify existing outputs without downloads")
    parser.add_argument("--source", type=Path, help="Existing directory with the exact pinned source downloads")
    arguments = parser.parse_args()
    try:
        prepare(arguments.offline, arguments.source)
    except (OSError, ValueError, zipfile.BadZipFile, KeyError) as error:
        print("Depth preparation:", error, file=sys.stderr)
        sys.exit(1)

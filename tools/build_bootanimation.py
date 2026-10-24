#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Verify the approved terminal boot animation without regenerating its artwork.

The user-supplied ZIP is stored in checksum-pinned binary chunks. Default
invocation and --check reconstruct its exact bytes and validate the payload;
neither preparation nor CI regenerates the artwork. Requires Pillow.
"""

import argparse
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def assemble(path):
    metadata = json.loads((ROOT / "bootanimation/payload.json").read_text())
    payload = bytearray()
    for chunk in metadata["chunks"]:
        data = (ROOT / "bootanimation" / chunk["file"]).read_bytes()
        require(len(data) == chunk["size"] and hashlib.sha256(data).hexdigest() == chunk["sha256"],
                "Boot animation source chunk differs: " + chunk["file"])
        payload.extend(data)
    require(len(payload) == metadata["size"] and hashlib.sha256(payload).hexdigest() == metadata["sha256"],
            "Assembled boot animation differs from the approved ZIP")
    if path.is_file() and path.read_bytes() == payload:
        return
    temporary = path.with_name(path.name + ".hecker-tmp")
    try:
        temporary.write_bytes(payload)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def check(path):
    metadata = json.loads((ROOT / "bootanimation/payload.json").read_text())
    require(path.stat().st_size == metadata["size"], "Boot animation size differs from the approved payload")
    require(hashlib.sha256(path.read_bytes()).hexdigest() == metadata["sha256"],
            "Boot animation SHA-256 differs from the approved payload")
    width, height = metadata["resolution"]
    fps = metadata["fps"]
    parts = metadata["parts"]
    descriptor = f"{width} {height} {fps}\n" + "".join(
        f"{part['type']} {part['repeat']} {part['pause']} {part['path']}\n" for part in parts)
    expected = {"desc.txt"}
    for part in parts:
        expected.update(f"{part['path']}/{n:05d}.png" for n in range(part["frames"]))
    require(metadata["intro_frames"] == parts[0]["frames"] and
            metadata["loop_frames"] == parts[1]["frames"], "Boot animation part metadata differs")
    with zipfile.ZipFile(path) as archive:
        items = archive.infolist()
        require(len(items) == len(expected) and {item.filename for item in items} == expected,
                "Boot animation has missing, duplicate or unexpected archive entries")
        require(all(item.compress_type == zipfile.ZIP_STORED for item in items),
                "Android boot animation entries must use STORE compression")
        require(archive.read("desc.txt") == descriptor.encode(),
                "Boot animation descriptor differs from the approved sequence")
        for item in items:
            if item.filename == "desc.txt":
                continue
            with Image.open(io.BytesIO(archive.read(item))) as frame:
                require(frame.format == "PNG" and frame.size == (width, height),
                        "Invalid boot animation frame: " + item.filename)
                frame.load()
    print(f"Boot animation: {metadata['intro_frames']} intro + {metadata['loop_frames']} loop frames; "
          f"{width}x{height}; {fps} fps; STORE ZIP; PNGs and locked SHA-256 verified.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate the approved ZIP (also the default)")
    parser.parse_args()
    try:
        target = ROOT / "bootanimation/bootanimation.zip"
        assemble(target)
        check(target)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print("Boot animation:", error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

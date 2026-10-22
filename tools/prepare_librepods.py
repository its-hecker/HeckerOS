#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fetch the exact original-signed LibrePods FOSS APK selected for HeckerOS."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def prepare(offline=False):
    folder = ROOT / "prebuilts/librepods"
    source = json.loads((folder / "source.json").read_text())
    target = folder / "prebuilt/LibrePods.apk"

    def verified(path):
        return (path.is_file() and path.stat().st_size == source["size"]
                and hashlib.sha256(path.read_bytes()).hexdigest() == source["sha256"])

    if verified(target):
        print("Original signed LibrePods APK: verified")
        return
    if offline:
        raise ValueError("Missing or invalid LibrePods APK; run tools/prepare_librepods.py online")
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".download")
    try:
        request = urllib.request.Request(source["url"], headers={"User-Agent": "HeckerOS-builder/1"})
        with urllib.request.urlopen(request, timeout=60) as response, temporary.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
                if output.tell() > source["size"]:
                    raise ValueError("LibrePods download exceeds the locked size")
        if not verified(temporary):
            raise ValueError("LibrePods APK checksum mismatch")
        with zipfile.ZipFile(temporary) as apk:
            if "AndroidManifest.xml" not in apk.namelist():
                raise ValueError("LibrePods payload is not an APK")
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    print("Original signed LibrePods APK: fetched and verified")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    prepare(args.offline)

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Optionally import Evolution X emoji font files into a *local* HeckerOS build.

No third-party font binary is checked into HeckerOS. Before distributing ROMs
that include these files, obtain and verify appropriate redistribution rights.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
from urllib.error import URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "fonts/emoji/prebuilt"
# Immutable upstream commit and verified Git blob identities (not branch URLs).
SOURCE_COMMIT = "1a97709e05739e0d4b05b37583a01eaa4df7cac8"
BASE_URL = ("https://raw.githubusercontent.com/Evolution-X/vendor_extras/"
            + SOURCE_COMMIT + "/prebuilt/product/fonts/")
ASSETS = {
    "LunarisIosEmoji.ttf": (35321980, "a4d2cd98b0e9f253ed633d6bb79fcc1a4bbceb8c"),
    "SamsungColorEmoji.ttf": (19404336, "6544a950294a6997106a0669f67bbeab0c99e8da"),
    "SwiftUIEmoji.ttf": (25243132, "1a49bbd57fbafca8afc6fd31a9ab2448f93991ca"),
    "FacebookEmoji.ttf": (39425672, "08b92f608c5e3d0b4a14461bcefd9aca67e0fb1d"),
}


def identity(path):
    """Git blob SHA-1, including the canonical blob header."""
    size = path.stat().st_size
    digest = hashlib.sha1(("blob " + str(size) + "\0").encode("ascii"))
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return size, digest.hexdigest()


def verify(path, expected):
    return path.is_file() and not path.is_symlink() and identity(path) == expected


def fetch_asset(url, target, expected):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() or target.is_symlink():
        if verify(target, expected):
            print("Already verified:", target.name)
            return
        raise ValueError("Unrecognized existing file; refusing overwrite: " + str(target))
    handle, temporary = tempfile.mkstemp(prefix=".hecker-emoji-", dir=target.parent)
    os.close(handle)
    part = Path(temporary)
    try:
        request = Request(url, headers={"User-Agent": "HeckerOS-Emoji-Importer/1.0"})
        with urlopen(request, timeout=50) as stream, part.open("wb") as output:
            length = 0
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                length += len(chunk)
                if length > expected[0]:
                    raise ValueError("Upstream file exceeds pinned size: " + target.name)
                output.write(chunk)
        if not verify(part, expected):
            raise ValueError("Upstream font identity mismatch: " + target.name)
        os.replace(part, target)
        print("Verified imported font:", target.name)
    finally:
        part.unlink(missing_ok=True)


def import_assets(source_dir=None, target_dir=DESTINATION, fetch=False):
    target_dir = Path(target_dir)
    if not fetch and source_dir is None:
        raise ValueError("Choose --fetch or --source-dir to import assets")
    for name, expected in ASSETS.items():
        destination = target_dir / name
        if source_dir is None:
            fetch_asset(BASE_URL + name, destination, expected)
            continue
        supplied = Path(source_dir) / name
        if not verify(supplied, expected):
            raise ValueError("Missing or unexpected supplied font: " + name)
        target_dir.mkdir(parents=True, exist_ok=True)
        if destination.exists() or destination.is_symlink():
            if not verify(destination, expected):
                raise ValueError("Unrecognized existing file: " + str(destination))
        else:
            shutil.copyfile(supplied, destination)
        print("Verified imported font:", name)
    return 4


def check_assets(target_dir=DESTINATION):
    missing = [name for name, expected in ASSETS.items()
               if not verify(Path(target_dir) / name, expected)]
    if missing:
        raise ValueError("Emoji font packs missing/invalid: " + ", ".join(missing))
    print("All four emoji packs have the pinned upstream identities")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--fetch", action="store_true",
                      help="Explicitly fetch four pinned fonts from Evolution X")
    mode.add_argument("--source-dir", type=Path,
                      help="Import four already obtained fonts")
    mode.add_argument("--check", action="store_true",
                      help="Offline verification of already staged fonts")
    args = parser.parse_args()
    try:
        if args.check:
            check_assets()
        else:
            print("Review each font's redistribution rights before publishing ROM builds.")
            import_assets(source_dir=args.source_dir, fetch=args.fetch)
            check_assets()
    except (OSError, ValueError, URLError) as error:
        parser.exit(1, "Hecker emoji asset import failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()

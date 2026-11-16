#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate transparent, size-correct OrangeFox About and small-icon assets.

The existing splash PNG is intentionally opaque and contains its own words.
It must never be used in the About logo slot or as an inline icon.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw


def build(source: Path, output: Path) -> None:
    original = Image.open(source).convert("RGBA")
    width, height = original.size
    # Matches the approved recovery art rendered by build_recovery_art.py:
    # a circular face of width 84/256 of the canvas, starting 8/160 down.
    if width * 160 != height * 256:
        raise ValueError("Unsupported HeckerOS recovery splash dimensions")
    factor = width / 256
    size = round(84 * factor)
    left = (width - size) // 2
    top = round(8 * factor)
    portrait = original.crop((left, top, left + size, top + size))
    output.mkdir(parents=True, exist_ok=True)
    for filename, diameter, canvas in (
        ("about.png", 150, (176, 204)),
        ("icon.png", 40, (48, 48)),
    ):
        image = Image.new("RGBA", canvas, (0, 0, 0, 0))
        # Supersample the circle mask so black corners of the original RGB
        # splash are never visible around the genuine HeckerOS caracal.
        sample = diameter * 4
        mask = Image.new("L", (sample, sample), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, sample - 1, sample - 1), fill=255)
        mask = mask.resize((diameter, diameter), Image.Resampling.LANCZOS)
        head = portrait.resize((diameter, diameter), Image.Resampling.LANCZOS)
        xy = ((canvas[0] - diameter) // 2, (canvas[1] - diameter) // 2)
        image.paste(head, xy, mask)
        image.save(output / filename, optimize=True)
        if Image.open(output / filename).getextrema()[-1][0] != 0:
            raise ValueError("Branding icon has no transparent pixels")
    print("Hecker Recovery About and icon branding: PASS")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("splash", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.splash, args.output)

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Render the approved Hecker caracal into recovery's five native density assets."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def build():
    mascot = Image.open(ROOT / "app/res/drawable-nodpi/hecker_mascot.webp").convert("RGBA")
    face = mascot.crop((470, 0, 1060, 590))
    for density, scale in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2,
                           "xxhdpi": 3, "xxxhdpi": 4}.items():
        # The canvas dimensions match the inherited resources exactly.
        size = (round(256 * scale), round(160 * scale))
        image = Image.new("RGB", size, (0, 0, 0))
        draw = ImageDraw.Draw(image)
        width = round(84 * scale)
        portrait = face.resize((width, width), Image.Resampling.LANCZOS)
        mask = Image.new("L", portrait.size)
        ImageDraw.Draw(mask).ellipse((0, 0, width - 1, width - 1), fill=255)
        left, top = (size[0] - width) // 2, round(8 * scale)
        image.paste(portrait, (left, top), mask)
        draw.ellipse((left, top, left + width, top + width), outline="#7DAAFF", width=max(1, round(scale)))
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", round(23 * scale))
        secondary = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", round(12 * scale))
        draw.text((size[0] / 2, 100 * scale), "HeckerOS", font=font, fill="#A6C6FF", anchor="mt")
        draw.text((size[0] / 2, 132 * scale), "RECOVERY", font=secondary, fill="#C4CFDF", anchor="mt")
        target = ROOT / f"recovery/res-{density}/images/logo_image.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target, optimize=True)
        print(target.relative_to(ROOT), image.size)


if __name__ == "__main__":
    build()

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Render Hecker's approved 4-second caracal intro and a seamless 2-second ring loop.
Requires Pillow, numpy and ffmpeg. Generated imagery is the checked-in keyframe sheet;
ffmpeg interpolates its poses. No network or image generation occurs during a ROM build.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import subprocess
import tempfile
import zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
W, H, FPS, INTRO, LOOP = 720, 1200, 30, 120, 60
SOURCE = ROOT / 'bootanimation/source/caracal-keyframes.png'

def smooth(v):
    v = min(1., max(0., v))
    return v * v * (3 - 2 * v)

def font(size, bold=False):
    path = Path('/usr/share/fonts/truetype/dejavu') / ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf')
    return ImageFont.truetype(str(path), size)

def interpolate(folder):
    sheet = Image.open(SOURCE).convert('RGB')
    assert sheet.size == (1448, 1086), 'Review changed keyframe registration'
    poses = [sheet.crop(((n % 4) * 362, (n // 4) * 362, (n % 4 + 1) * 362, (n // 4 + 1) * 362)) for n in range(12)]
    # Omit frame 9: it winks the opposite eye. The approved wink uses frame 8.
    timeline = [(0., 0), (.4, 0), (.6, 2), (.8, 3), (1., 4), (1.2, 5),
                (1.4, 6), (1.8, 6), (2., 7), (2.2, 9), (2.4, 10), (2.6, 11), (3.8, 11)]
    for n in range(40):
        pose = max((p for t, p in timeline if t <= n / 10), default=0)
        poses[pose].save(folder / f'pose-{n:03d}.png')
    process = subprocess.run(['ffmpeg', '-v', 'error', '-threads', '2', '-framerate', '10', '-i', str(folder / 'pose-%03d.png'),
        '-vf', 'minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1',
        '-pix_fmt', 'rgb24', '-f', 'rawvideo', 'pipe:1'], capture_output=True, check=True)
    data = np.frombuffer(process.stdout, np.uint8).reshape((-1, 362, 362, 3))
    return [Image.fromarray(a) for a in data], poses[-1]

def ring(time, intro=False):
    layer = Image.new('RGB', (W, H))
    draw = ImageDraw.Draw(layer)
    x, y, radius = 360, 493, 162
    reveal = smooth(time / .4) if intro else 1.
    theta = (time * math.pi) - math.pi / 2
    for n in range(480):
        a = 2 * math.pi * n / 480
        b = a + 2 * math.pi / 480 + .003
        dist = abs(math.atan2(math.sin(a - theta), math.cos(a - theta)))
        dist2 = abs(math.atan2(math.sin(a - theta - math.pi), math.cos(a - theta - math.pi)))
        glint = math.exp(-dist * dist / .018) + .5 * math.exp(-dist2 * dist2 / .028)
        base = 25 + 65 * (.5 + .5 * math.cos(a - .8))
        color = tuple(int(min(255, v * reveal)) for v in (base + 160 * glint, base * 1.25 + 170 * glint, base * 2.4 + 190 * glint))
        draw.line((x + radius * math.cos(a), y + radius * math.sin(a), x + radius * math.cos(b), y + radius * math.sin(b)), fill=color, width=2)
    glow = layer.filter(ImageFilter.GaussianBlur(9))
    layer = Image.fromarray(np.minimum(255, np.asarray(layer, dtype=np.uint16) + np.asarray(glow, dtype=np.uint16) * 3).astype(np.uint8))
    return layer

def render(t, cat, intro):
    image = Image.new('RGB', (W, H))
    alpha = smooth((t - .4) / .42) if intro else 1.
    size = 318
    face = cat.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new('L', (size, size))
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=int(255 * alpha))
    image.paste(face, (360 - size // 2, 493 - size // 2), mask)
    image = Image.fromarray(np.maximum(np.asarray(image), np.asarray(ring(t if intro else t % 2, intro))).astype(np.uint8))
    a = smooth((t - 3.) / .5) if intro else 1.
    if a:
        label = Image.new('RGB', (W, H))
        draw = ImageDraw.Draw(label)
        name, os = 'Hecker', 'OS'
        f = font(45, True)
        total = draw.textlength(name + os, font=f)
        start = (W - total) / 2
        draw.text((start, 686), name, font=f, fill=(243, 245, 250))
        draw.text((start + draw.textlength(name, font=f), 686), os, font=f, fill=(66, 181, 255))
        tag = 'B E Y O N D  O R D I N A R Y .'
        small = font(11)
        draw.text(((W - draw.textlength(tag, font=small)) / 2, 751), tag, font=small, fill=(173, 185, 204))
        image = Image.fromarray(np.maximum(np.asarray(image), (np.asarray(label) * a).astype(np.uint8)))
    return image

def check(path):
    with zipfile.ZipFile(path) as archive:
        assert archive.read('desc.txt') == b'720 1200 30\np 1 0 part0\np 0 0 part1\n'
        assert len(archive.infolist()) == 1 + INTRO + LOOP
        assert archive.testzip() is None
        for item in archive.infolist():
            assert item.compress_type == zipfile.ZIP_STORED
            if item.filename.endswith('.png'):
                frame = Image.open(io.BytesIO(archive.read(item)))
                assert frame.size == (W, H)
        first = np.asarray(Image.open(io.BytesIO(archive.read('part1/00000.png'))), dtype=np.int16)
        last = np.asarray(Image.open(io.BytesIO(archive.read('part1/00059.png'))), dtype=np.int16)
        assert np.mean(np.abs(first-last)) < .12, 'Loop seam exceeds normal ring movement'
        intro_last = np.asarray(Image.open(io.BytesIO(archive.read('part0/00119.png'))), dtype=np.int16)
        assert np.mean(np.abs(first-intro_last)) < .12, 'Intro does not join the loop'
    record = ROOT / 'bootanimation/payload.json'
    if record.exists():
        metadata = json.loads(record.read_text())
        assert metadata['source_sha256'] == hashlib.sha256(SOURCE.read_bytes()).hexdigest()
        assert metadata['size'] == path.stat().st_size
        assert metadata['sha256'] == hashlib.sha256(path.read_bytes()).hexdigest()
    print('Boot animation: 120 intro + 60 seamless loop frames; 30 fps; STORE ZIP; valid PNGs.')

def build(preview=True):
    target = ROOT / 'bootanimation/bootanimation.zip'
    with tempfile.TemporaryDirectory(prefix='hecker-boot-') as temporary:
        folder = Path(temporary)
        motion, final = interpolate(folder)
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_STORED) as archive:
            def put(name, content):
                item = zipfile.ZipInfo(name, date_time=(2026, 10, 20, 0, 0, 0))
                item.external_attr = 0o100644 << 16
                archive.writestr(item, content)
            # Both parts may exit immediately when Android finishes booting.
            put('desc.txt', b'720 1200 30\np 1 0 part0\np 0 0 part1\n')
            frames = []
            for n in range(INTRO + LOOP):
                intro = n < INTRO
                t = n / FPS if intro else (n - INTRO) / FPS
                pose = motion[min(max(0, n - 12), len(motion) - 1)] if intro else final
                frame = render(t, pose, intro)
                stream = io.BytesIO()
                frame.save(stream, format='PNG', optimize=True)
                put(f"{'part0' if intro else 'part1'}/{n if intro else n-INTRO:05d}.png", stream.getvalue())
                if preview:
                    path = folder / f'preview-{n:05d}.png'
                    frame.resize((360, 600), Image.Resampling.LANCZOS).save(path)
                    frames.append(path)
            if preview:
                # Native-size viewport on coral's 1440x3040 panel, shown at half resolution.
                subprocess.run(['ffmpeg', '-v', 'error', '-y', '-framerate', str(FPS), '-i', str(folder / 'preview-%05d.png'),
                    '-vf', 'pad=720:1520:(ow-iw)/2:(oh-ih)/2', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p',
                    '-movflags', '+faststart', str(ROOT / 'bootanimation/preview-coral.mp4')], check=True)
    payload = target.read_bytes()
    (ROOT / 'bootanimation/payload.json').write_text(json.dumps({'sha256': hashlib.sha256(payload).hexdigest(), 'size': len(payload), 'resolution': [W,H], 'fps': FPS, 'intro_frames': INTRO, 'loop_frames': LOOP, 'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest()}, indent=2) + '\n')
    check(target)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--no-preview', action='store_true')
    args = parser.parse_args()
    if args.check: check(ROOT / 'bootanimation/bootanimation.zip')
    else: build(not args.no_preview)

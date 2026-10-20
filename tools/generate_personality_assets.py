#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate the original, self-contained Hecker orbit Lottie for Suite and Launcher."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fixed(value):
    return {"a": 0, "k": value}


def motion(start, end):
    return {"a": 1, "k": [
        {"t": 0, "s": [start], "e": [end], "o": {"x": [0.33], "y": [0.33]},
         "i": {"x": [0.67], "y": [0.67]}}, {"t": 120, "s": [end]}]}


def layer(index, shapes, rotation=0):
    return {"ddd": 0, "ind": index, "ty": 4, "nm": "Hecker orbit " + str(index),
            "sr": 1, "ks": {"o": fixed(100), "r": motion(0, rotation) if rotation else fixed(0),
            "p": fixed([160, 100, 0]), "a": fixed([0, 0, 0]), "s": fixed([100, 100, 100])},
            "ao": 0, "shapes": shapes, "ip": 0, "op": 120, "st": 0, "bm": 0}


def ellipse(x, y, width, height, color, filled=False):
    return [{"ty": "el", "d": 1, "s": fixed([width, height]), "p": fixed([x, y])},
            {"ty": "fl" if filled else "st", "c": fixed(color), "o": fixed(100),
             **({"r": 1} if filled else {"w": fixed(2), "lc": 2, "lj": 2})}]


def generate():
    cyan, violet = [0.05, 0.78, 0.85, 1], [0.62, 0.48, 0.99, 1]
    layers = [layer(1, ellipse(0, 0, 128, 128, cyan))]
    for index in range(8):
        angle = index * math.pi / 4
        radius = 79 if index % 2 else 65
        shapes = ellipse(math.cos(angle) * radius, math.sin(angle) * radius,
                         7 if index % 2 else 10, 7 if index % 2 else 10,
                         violet if index % 2 else cyan, True)
        layers.append(layer(index + 2, shapes, 360 if index % 2 else -360))
    path = {"i": [[0, 0]] * 6, "o": [[0, 0]] * 6,
            "v": [[-20, -28], [-20, 28], [-20, 0], [20, 0], [20, -28], [20, 28]], "c": False}
    layers.append(layer(10, [{"ty": "sh", "ks": fixed(path)},
                            {"ty": "st", "c": fixed(cyan), "o": fixed(100),
                             "w": fixed(8), "lc": 2, "lj": 2}]))
    document = {"v": "5.7.4", "fr": 30, "ip": 0, "op": 120, "w": 320, "h": 200,
                "nm": "Hecker orbital monogram", "ddd": 0, "assets": [], "layers": layers}
    for relative in ["app/res/raw/suite_orbit.json", "launcher/res/raw/hecker_orbit.json"]:
        path = ROOT / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(document, separators=(",", ":")) + "\n")


if __name__ == "__main__": generate()

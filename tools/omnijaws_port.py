# SPDX-License-Identifier: Apache-2.0
"""Guarded OmniJaws UI compatibility plan, applied with the other native ports."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OMNIJAWS = Path("packages/services/OmniJaws")


def omnijaws_copies():
    client = (ROOT / "launcher/src/OmniJawsClient.java").read_text()
    package = "package com.android.launcher3.hecker;"
    if client.count(package) != 1:
        raise ValueError("Unfamiliar shared OmniJaws client package")
    return {
        "app/src/main/java/org/omnirom/omnijaws/hecker/HeckerWeatherUi.kt":
            (ROOT / "omnijaws/HeckerWeatherUi.kt").read_text(),
        "app/src/main/res/values/hecker_weather.xml":
            (ROOT / "omnijaws/hecker_weather.xml").read_text(),
        "app/src/main/java/org/omnirom/omnijaws/hecker/OmniJawsClient.java":
            client.replace(package, "package org.omnirom.omnijaws.hecker;", 1),
    }


def omnijaws_plan(rom, replace, revert=False):
    project = rom / OMNIJAWS
    if not project.is_dir():
        return {}
    changes = {}
    for relative, pairs in json.loads((ROOT / "omnijaws/patches.json").read_text()).items():
        path = project / relative
        current = path.read_text()
        target = current
        for pair in reversed(pairs) if revert else pairs:
            target = replace(target, pair["old"], pair["new"], str(path), revert)
        if target != current:
            changes[path] = target
    for relative, expected in omnijaws_copies().items():
        path = project / relative
        if path.exists() and path.read_text() != expected:
            raise ValueError("Preserving an unfamiliar OmniJaws adapter: " + str(path))
        if revert:
            if path.exists(): changes[path] = None
        elif not path.exists():
            changes[path] = expected
    return changes

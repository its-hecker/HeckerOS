#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile and exercise the pinned Floral display policy's real Java methods.

This is a host regression, not panel or full-system-server validation. Android
service/sensor fixtures are distinguished from the production methods inserted
verbatim from DisplayModeDirector. Inputs are locked by commit and SHA-256.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DIRECTOR = "services/core/java/com/android/server/display/mode/DisplayModeDirector.java"
HELPER = "services/core/java/com/android/server/display/mode/LowZoneBrightnessHysteresis.java"
CONFIG = "core/res/res/values/config.xml"
SYMBOLS = "core/res/res/values/symbols.xml"
MARGIN = "config_lowZoneRefreshRateHysteresis"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_inputs(lock, name, local, cache):
    spec = lock[name]
    result = {}
    for path, digest in spec["files"].items():
        target = local / path if local else cache / name / spec["revision"] / path
        if not target.exists() and not local:
            url = ("https://raw.githubusercontent.com/" + spec["repository"] + "/"
                   + spec["revision"] + "/" + path)
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read()
            require(hashlib.sha256(data).hexdigest() == digest, "Digest mismatch: " + url)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        data = target.read_bytes()
        require(hashlib.sha256(data).hexdigest() == digest, "Digest mismatch: " + str(target))
        result[path] = data.decode("utf-8")
    return result


def method(source, signature):
    require(source.count(signature) == 1, "Review changed native signature: " + signature)
    start = source.index(signature)
    # Mask Java strings/comments so braces in diagnostic text cannot end a method.
    masked = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*',
                    lambda match: " " * len(match.group()), source, flags=re.S)
    body = masked.index("{", start)
    depth = 1
    for end in range(body + 1, len(masked)):
        if masked[end] == "{":
            depth += 1
        elif masked[end] == "}":
            depth -= 1
            if depth == 0:
                return source[start:end + 1]
    raise ValueError("Unterminated native method: " + signature)


def integer(resources, name):
    nodes = resources.findall("integer[@name='" + name + "']")
    require(len(nodes) == 1, "Missing/duplicate integer " + name)
    return int(nodes[0].text)


def resource_checks(framework, device):
    resources = ET.fromstring(framework[CONFIG])
    require(integer(resources, MARGIN) == 0, "Other devices must retain native policy")
    symbols = ET.fromstring(framework[SYMBOLS])
    require(len(symbols.findall("java-symbol[@type='integer'][@name='" + MARGIN + "']")) == 1,
            "The new internal R.integer must be exported")
    for panel, entry, rate in (("coral", 74, 90), ("flame", 16, 60)):
        path = panel + "/overlay/frameworks/base/core/res/res/values/config.xml"
        r = ET.fromstring(device[path])
        require(integer(r, MARGIN) == 4, panel + " hysteresis must be four raw steps")
        require(integer(r, "config_defaultRefreshRateInZone") == rate,
                panel + " panel-specific refresh rate changed")
        for name, expected in (("config_brightnessThresholdsOfPeakRefreshRate", entry),
                               ("config_ambientThresholdsOfPeakRefreshRate", -1)):
            array = r.find("integer-array[@name='" + name + "']")
            require(array is not None and [int(n.text) for n in array] == [expected],
                    panel + " brightness/ambient policy mismatch: " + name)
        require(integer(r, "config_defaultPeakRefreshRate") == 90,
                panel + " adaptive 90 Hz must remain available")


def run(framework, device, aapt2):
    resource_checks(framework, device)
    director = framework[DIRECTOR]
    require("R.integer." + MARGIN in director, "Observer is not wired to the opt-in resource")
    restart = method(director, "private void restartObserver()")
    require("resetLowZoneHysteresis();" in restart, "Config reload must clear hysteresis")
    methods = [method(director, signature) for signature in (
        "private boolean isInsideLowZone(float brightness, float lux)",
        "private boolean isInsideHighZone(float brightness, float lux)",
        "private boolean hasValidLowZone()",
        "private boolean hasValidHighZone()",
        "private void updateFlickerRefreshRateVotes()",
        "private void resetLowZoneHysteresis()",
        "public void onRefreshRateSettingChangedLocked(float min, float max)",
        "void onLowPowerModeEnabledLocked(boolean enabled)",
        "void setDefaultDisplayState(int state)",
    )]
    template = (ROOT / "tests/display/NativeBrightnessObserver.java.in").read_text()
    require(template.count("/* PRODUCTION_METHODS */") == 1, "Invalid host fixture")
    constructor = method(director,
                         "BrightnessObserver(Context context, Handler handler, Injector injector,")
    constructor = constructor.replace("BrightnessObserver(", "NativeBrightnessObserver(", 1)
    host = template.replace("/* PRODUCTION_METHODS */", "\n\n".join(methods))
    require(host.count("/* PRODUCTION_CONSTRUCTOR */") == 1, "Invalid constructor fixture")
    host = host.replace("/* PRODUCTION_CONSTRUCTOR */", constructor)
    with tempfile.TemporaryDirectory(prefix="hecker-display-policy-") as temporary:
        directory = Path(temporary)
        package = directory / "com/android/server/display/mode"
        package.mkdir(parents=True)
        helper = package / "LowZoneBrightnessHysteresis.java"
        helper.write_text(framework[HELPER])
        observer = package / "NativeBrightnessObserver.java"
        observer.write_text(host)
        tests = ROOT / "tests/display/DisplayFlickerTest.java"
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-Xlint:all",
                        "-Werror", "-d", str(directory), str(helper), str(observer), str(tests)],
                       check=True)
        subprocess.run(["java", "-ea", "-cp", str(directory),
                        "com.android.server.display.mode.DisplayFlickerTest"], check=True)
        if aapt2:
            resource_sets = [("framework", framework[CONFIG]), ("symbols", framework[SYMBOLS])]
            resource_sets += [(panel, device[panel + "/overlay/frameworks/base/core/res/res/values/config.xml"])
                              for panel in ("coral", "flame")]
            for label, xml in resource_sets:
                values = directory / label / "values"
                output = directory / label / "compiled"
                values.mkdir(parents=True)
                output.mkdir()
                filename = values / ("symbols.xml" if label == "symbols" else "config.xml")
                filename.write_text(xml)
                subprocess.run([str(aapt2), "compile", "-o", str(output), str(filename)],
                               check=True)
            print("Framework internal symbol and Coral/Flame resource compilation: PASS")
    print("Opt-in resources, panel thresholds and production observer wiring: PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--framework-root", type=Path)
    parser.add_argument("--device-root", type=Path)
    parser.add_argument("--cache", type=Path, default=ROOT / "generated/display-flicker")
    parser.add_argument("--aapt2", type=Path)
    args = parser.parse_args()
    lock = json.loads((ROOT / "tests/display/source-lock.json").read_text())
    framework = read_inputs(lock, "framework", args.framework_root, args.cache)
    device = read_inputs(lock, "device", args.device_root, args.cache)
    run(framework, device, args.aapt2)


if __name__ == "__main__":
    main()

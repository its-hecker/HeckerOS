#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Source-level guards for HeckerOS Smoothness 2.0.

Run from a standalone HeckerOS clone or a synchronized Android tree.
This is NOT a substitute for compilation, phone boot, or Perfetto analysis.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def check_source():
    def source(path):
        p = ROOT / path
        assert p.is_file(), f"missing {path}"
        return p.read_text(encoding="utf-8")

    launcher = source("aosp-launcher/src/com/android/launcher3/hecker/HeckerLauncherGlass.java")
    assert "geometryRevision" in launcher and "styleRevision" in launcher
    assert "scanAfterLayout" in launcher and "postDelayed(scanAfterLayout, 120)" in launcher
    assert "WORKER.execute" in launcher
    game = source("app/src/org/hecker/suite/GamingService.java")
    assert "lastFpsUiAt" in game and "now-lastFpsUiAt>=250" in game
    island = source("framework/motionisland/MotionIslandView.java")
    assert "updateMotion()" in island and "isAttachedToWindow()" in island
    assert "artworkClip.rewind()" in island and "clippedWidth" in island
    qs = source("framework/qs/HeckerGlassQuickSettings.kt")
    assert "remember(tiles, columns, connections)" in qs
    media = source("framework/qs/HeckerMediaCard.kt")
    assert "delay(48)" in media and "repeatOnLifecycle(Lifecycle.State.RESUMED)" in media
    battery = source("framework/themes/HeckerBatteryDrawable.java")
    assert "FRAME_DELAY_MS = 50;" in battery and "!isVisible()" in battery
    product = source("rom/common.mk")
    assert "PRODUCT_DEXPREOPT_SPEED_APPS += SystemUI HeckerLauncher HeckerSuite" in product
    assert "supports_background_blur?=1" in product
    print("PASS: launcher, QS, GameSpace, animation lifecycles, ART product")


def check_android_tree(tree):
    device = tree / "device/google/coral"
    framework = tree / "frameworks/base"
    if not device.is_dir() or not framework.is_dir():
        print("SKIP: Android device/framework trees are not mounted")
        return
    power = json.loads((device / "configs/power/powerhint.json").read_text())
    actions = [a for a in power["Actions"] if a.get("PowerHint") == "LAUNCH"]
    assert len(actions) >= 8
    assert all(a.get("Duration") <= 1500 for a in actions)
    camera = [a for a in power["Actions"] if a.get("PowerHint") == "CAMERA_LAUNCH"]
    assert camera and all(a.get("Duration") == 1000 for a in camera)
    prop = (device / "product.prop").read_text()
    assert "ro.lmk.use_psi=true" in prop and "ro.config.low_ram=false" in prop
    vend = (device / "vendor.prop").read_text()
    assert "ro.surface_flinger.set_idle_timer_ms=150" in vend
    assert "ro.surface_flinger.use_content_detection_for_refresh_rate=true" in vend
    blur = (framework / "packages/SystemUI/src/com/android/systemui/window/ui/BlurChoreographer.kt").read_text()
    assert "sameVisibleEffect" in blur and "wasUpdateScheduledForThisFrame" in blur
    assert "Multiple blur values emitted in the same frame" not in blur
    print("PASS: Power HAL, PSI LMKD, adaptive display, SystemUI blur coalescing")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rom-root", type=Path, default=Path.cwd(),
                        help="Android tree path for additional integration checks")
    args = parser.parse_args()
    check_source()
    check_android_tree(args.rom_root)
    print("PASS: static smoothness guards; physical testing remains necessary")


if __name__ == "__main__":
    main()

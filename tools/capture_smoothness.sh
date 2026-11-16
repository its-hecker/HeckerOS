#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Capture both baseline and test build with the same app-launch/QS/recents workflow.
set -euo pipefail
label="${1:-smoothness}"
if [[ ! "$label" =~ ^[a-zA-Z0-9_-]+$ ]]; then echo "Use a simple label" >&2; exit 2; fi
out="${PWD}/smoothness-${label}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
adb get-state >/dev/null
adb shell getprop ro.heckeros.suite_revision > "$out/suite_revision.txt"
adb shell getprop ro.heckeros.manifest_revision > "$out/manifest_revision.txt"
adb shell getprop ro.build.fingerprint > "$out/fingerprint.txt"
adb shell 'cat /proc/pressure/memory 2>/dev/null || true' > "$out/psi-memory.txt"
adb shell 'cat /proc/swaps 2>/dev/null || true; cat /sys/block/zram0/mm_stat 2>/dev/null || true' > "$out/zram.txt"
adb shell 'cat /proc/meminfo' > "$out/meminfo.txt"
adb shell dumpsys meminfo com.android.systemui > "$out/systemui-memory.txt"
adb shell dumpsys gfxinfo com.android.systemui framestats > "$out/systemui-framestats.txt"
adb shell dumpsys gfxinfo com.android.launcher3 framestats > "$out/launcher-framestats.txt"
adb shell dumpsys thermalservice > "$out/thermal.txt"
adb shell dumpsys display > "$out/display.txt"
adb shell dumpsys power > "$out/power.txt"
adb shell 'dumpsys activity processes | head -n 250' > "$out/processes.txt"
echo "Saved diagnostics: $out"
echo "Record Perfetto FrameTimeline while opening/closing QS, using recents and launching apps."
echo "Compare the same actions and thermal conditions on cnb_latest_stable vs perf/heckeros-2.0."

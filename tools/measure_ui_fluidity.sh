#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Read-only UI frame/power snapshots for baseline/candidate comparisons.
# Run from a computer with an authorized ADB-connected Coral.
set -euo pipefail
label="${1:-baseline}"
if [[ ! "$label" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "Usage: bash $0 baseline|candidate" >&2; exit 2
fi
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
[[ "$(adb get-state 2>/dev/null)" == "device" ]] || {
  echo "Connect an authorized device" >&2; exit 1
}
out="hecker-fluidity-${label}-$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$out"
adb shell getprop ro.build.fingerprint > "$out/build.txt"
adb shell getprop ro.product.device > "$out/device.txt"
for package in com.android.settings com.android.systemui com.android.launcher3 org.hecker.suite com.google.android.apps.nexuslauncher; do
  adb shell dumpsys gfxinfo "$package" framestats > "$out/gfx-${package}.txt" 2>&1 || true
  adb shell dumpsys meminfo "$package" > "$out/mem-${package}.txt" 2>&1 || true
done
adb shell cat /proc/pressure/cpu > "$out/psi-cpu.txt" 2>&1 || true
adb shell cat /proc/pressure/memory > "$out/psi-memory.txt" 2>&1 || true
adb shell cat /proc/pressure/io > "$out/psi-io.txt" 2>&1 || true
adb shell cat /proc/swaps > "$out/swaps.txt" 2>&1 || true
adb shell dumpsys thermalservice > "$out/thermal.txt" 2>&1 || true
adb shell dumpsys power > "$out/power.txt" 2>&1 || true
echo "Captured read-only UI/memory/thermal snapshots: $out"
echo "Compare after the same Settings, Suite, drawer, Quick Settings and Recents workflow."

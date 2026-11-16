#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Capture a Home gesture launched from the app drawer without changing settings.
# Usage: bash tools/capture_drawer_home_jump.sh 20
set -euo pipefail
duration="${1:-20}"
if [[ ! "$duration" =~ ^[0-9]+$ ]] || ((duration<10 || duration>120)); then
  echo "Usage: $0 [10..120 seconds]" >&2; exit 2
fi
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
command -v timeout >/dev/null || { echo "GNU timeout is required" >&2; exit 2; }
[[ "$(adb get-state 2>/dev/null)" == "device" ]] || {
  echo "Connect an authorized Android device" >&2; exit 1
}
out="home-drawer-jump-$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$out"
adb shell getprop ro.build.fingerprint > "$out/build.txt"
adb shell getprop ro.product.device > "$out/device.txt"
adb shell dumpsys activity activities > "$out/activity-before.txt" 2>&1 || true
adb shell dumpsys window > "$out/window-before.txt" 2>&1 || true
echo "Begin reproducing the drawer-to-Home gesture now. Recorder stops after $duration seconds."
# Logcat is not cleared; keep full context so the trace can be correlated
# with ShellRecents, InputDispatcher and Quickstep animation transitions.
timeout -k 3s "${duration}s" adb logcat -v threadtime -b main -b system -b events > "$out/logcat.txt" 2>&1 || code=$?
if [[ "${code:-0}" != "0" && "${code:-0}" != "124" ]]; then
  echo "adb logcat returned code $code (see logcat.txt)" >&2
fi
adb shell dumpsys activity activities > "$out/activity-after.txt" 2>&1 || true
adb shell dumpsys window > "$out/window-after.txt" 2>&1 || true
adb shell dumpsys input > "$out/input-after.txt" 2>&1 || true
echo "Saved diagnostic traces to $out"
echo "Review/redact package names, windows and log lines before sharing."

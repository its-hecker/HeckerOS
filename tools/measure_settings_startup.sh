#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Collect comparable Settings app-launch timings; does not change ROM settings.
# Usage: bash tools/measure_settings_startup.sh baseline warm 7
#        bash tools/measure_settings_startup.sh candidate cold 7
set -euo pipefail

label="${1:-baseline}"
mode="${2:-warm}"
runs="${3:-7}"
if [[ ! "$label" =~ ^[a-zA-Z0-9_-]+$ || ! "$mode" =~ ^(warm|cold)$
      || ! "$runs" =~ ^[1-9][0-9]*$ ]] || (( runs > 30 )); then
    echo "Usage: $0 baseline|candidate warm|cold [1..30]" >&2
    exit 2
fi
command -v adb >/dev/null || { echo "Requires adb in PATH." >&2; exit 2; }
[[ "$(adb get-state 2>/dev/null)" == "device" ]] || {
    echo "Connect an authorized Android device." >&2; exit 1;
}

out="settings-bench-${label}-${mode}-$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$out"
printf "run,status,this_time_ms,total_time_ms,wait_time_ms\n" > "$out/startup.csv"
adb shell getprop ro.build.fingerprint > "$out/build-fingerprint.txt"
adb shell getprop ro.product.device > "$out/device.txt"
adb shell getprop ro.build.version.incremental > "$out/build-incremental.txt"

if [[ "$mode" == "cold" ]]; then
    echo "Cold mode force-stops Settings between launches. Save any ongoing Settings work."
else
    echo "Warm mode returns Home between launches without killing Settings."
fi
for (( i=1; i<=runs; i++ )); do
    if [[ "$mode" == "cold" ]]; then
        adb shell am force-stop com.android.settings
    else
        adb shell input keyevent KEYCODE_HOME
    fi
    sleep 1
    # -W waits for Activity launch; launch output includes platform timings when available.
    adb shell am start -W -a android.settings.SETTINGS > "$out/launch-${i}.txt" 2>&1 || {
        echo "Settings launch failed on run $i; see $out/launch-${i}.txt" >&2
        exit 1
    }
    # Do not substitute zero for missing timings or claim an already-active task is a cold start.
    status=$(sed -n 's/^Status: *//p' "$out/launch-${i}.txt" | head -n1)
    this_ms=$(sed -n 's/^ThisTime: *//p' "$out/launch-${i}.txt" | head -n1)
    total_ms=$(sed -n 's/^TotalTime: *//p' "$out/launch-${i}.txt" | head -n1)
    wait_ms=$(sed -n 's/^WaitTime: *//p' "$out/launch-${i}.txt" | head -n1)
    printf '%d,%s,%s,%s,%s\n' "$i" "${status:-NA}" "${this_ms:-NA}" "${total_ms:-NA}" "${wait_ms:-NA}" >> "$out/startup.csv"
    printf 'run %d/%d: TotalTime=%s ms\n' "$i" "$runs" "${total_ms:-unavailable}"
done
adb shell dumpsys gfxinfo com.android.settings framestats > "$out/gfxinfo-framestats.txt" 2>&1 || true
adb shell dumpsys meminfo com.android.settings > "$out/meminfo.txt" 2>&1 || true
echo "Saved Settings timings to: $out"
echo "Compare identical warm/cold modes, screen state and thermal conditions."

#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Read-only display snapshots while reproducing low-brightness flicker.
set -eo pipefail
capture_dir="${1:-hecker-display-$(date -u +%Y%m%dT%H%M%SZ)}"
if [[ "$(adb get-state 2>/dev/null)" != device ]]; then
    echo "Connect and authorize the phone in adb first." >&2
    exit 1
fi
mkdir -- "$capture_dir"
{
    adb shell getprop ro.build.fingerprint
    adb shell getprop ro.product.device
    for key in screen_brightness screen_brightness_float screen_brightness_mode min_refresh_rate peak_refresh_rate; do
        echo "$key"
        adb shell settings get system "$key"
    done
    echo "Extra dim enabled"
    adb shell settings get secure reduce_bright_colors_activated
} > "$capture_dir/settings.txt"
echo "Reproduce the flicker now; collecting 20 seconds of display state."
for sample in $(seq 0 10); do
    adb shell dumpsys display > "$capture_dir/display-$sample.txt"
    if [[ "$sample" != 10 ]]; then sleep 2; fi
done
adb logcat -d -v threadtime -t 5000 -s DisplayPowerController DisplayModeDirector DisplayDeviceConfig BrightnessMappingStrategy BrightnessSynchronizer SurfaceFlinger \
    > "$capture_dir/display-logcat.txt"
echo "Saved display state in $capture_dir"

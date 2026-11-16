#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Read-only before/after capture for the HeckerOS 2.0 Coral kernel/device trial.
# Run on a host with Android platform-tools: bash tools/capture_kernel_device_phase2.sh baseline
set -euo pipefail
label="${1:-trial}"
if [[ ! "$label" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "Use a short alphanumeric capture label" >&2
    exit 2
fi
adb get-state >/dev/null
out="${PWD}/heckeros-kernel-phase2-${label}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
adb shell getprop ro.build.fingerprint > "$out/build-fingerprint.txt"
adb shell getprop ro.heckeros.version > "$out/heckeros-version.txt"
adb shell uname -a > "$out/kernel.txt"
adb shell 'for path in /sys/devices/system/cpu/cpu0/cpufreq/schedutil/down_rate_limit_us /sys/devices/system/cpu/cpu4/cpufreq/schedutil/down_rate_limit_us /sys/devices/system/cpu/cpu7/cpufreq/schedutil/down_rate_limit_us /sys/block/sda/queue/scheduler /sys/block/sda/queue/read_ahead_kb /sys/block/sde/queue/scheduler /sys/block/sdf/queue/scheduler /sys/module/workqueue/parameters/power_efficient /proc/sys/net/ipv4/tcp_congestion_control /proc/sys/net/ipv4/tcp_available_congestion_control /proc/sys/net/core/default_qdisc /sys/block/zram0/disksize /sys/block/zram0/comp_algorithm; do echo "$path"; if [ -r "$path" ]; then cat "$path"; else echo unavailable; fi; done' > "$out/runtime-tuning.txt"
adb shell 'cat /proc/pressure/memory 2>/dev/null || true; cat /proc/swaps 2>/dev/null || true; cat /sys/block/zram0/mm_stat 2>/dev/null || true' > "$out/memory-pressure.txt"
adb shell dumpsys gfxinfo com.android.systemui framestats > "$out/systemui-frames.txt"
adb shell dumpsys gfxinfo com.android.launcher3 framestats > "$out/launcher-frames.txt"
adb shell dumpsys meminfo com.android.systemui > "$out/systemui-memory.txt"
adb shell dumpsys thermalservice > "$out/thermal.txt"
adb shell dumpsys power > "$out/power.txt"
echo "Read-only diagnostics saved in: $out"
echo "Record Perfetto FrameTimeline separately with identical app/gesture workloads."

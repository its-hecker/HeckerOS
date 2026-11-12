#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Read-only ADB capture for an app that is visibly stuck until the Recents round-trip.
# Run once WHILE frozen, without entering Recents, then again after returning.
set -uo pipefail
label="${1:-frozen}"
if [[ ! "$label" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "Usage: bash tools/capture_app_freeze.sh frozen|recovered [package.name]" >&2
    exit 2
fi
pkg="${2:-}"
if [[ -n "$pkg" && ! "$pkg" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]; then
    echo "Invalid Android package name" >&2
    exit 2
fi
if ! command -v adb >/dev/null || ! command -v timeout >/dev/null; then
    echo "Requires adb and GNU timeout on a USB-connected Linux host." >&2
    exit 2
fi
if [[ "$(timeout -k 2s 8s adb get-state 2>/dev/null)" != "device" ]]; then
    echo "Connect an authorized Android device with USB debugging before capturing." >&2
    exit 1
fi

out="$PWD/app-freeze-${label}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
printf '%s\n' "Capture label: $label" "Host UTC: $(date -u +%FT%TZ)" \
    "Package: ${pkg:-not provided}" > "$out/metadata.txt"

capture() {
    local file="$1"; shift
    # A stuck service may leave dumpsys waiting. Time out each probe independently;
    # keep all successful snapshots even if one probe fails.
    if timeout -k 2s 12s adb "$@" > "$out/$file" 2> "$out/$file.err"; then
        rm -f "$out/$file.err"
    else
        printf 'Capture failed or timed out: %s\n' "$file" >> "$out/failures.txt"
    fi
}
capture build.txt shell getprop ro.build.fingerprint
capture suite.txt shell getprop ro.heckeros.suite_revision
capture uptime.txt shell cat /proc/uptime
capture window.txt shell dumpsys window
capture input.txt shell dumpsys input
capture activities.txt shell dumpsys activity activities
capture transitions.txt shell dumpsys activity service com.android.wm.shell/.ShellService
capture surface-layers.txt shell dumpsys SurfaceFlinger --list
# Include the compositor scheduler/active layer snapshot, not just layer names.
# This matters if frames stop being presented while input still reaches the app.
capture surfaceflinger-state.txt shell dumpsys SurfaceFlinger
capture display.txt shell dumpsys display
capture scheduler.txt shell 'for n in cpu memory io; do echo "=== $n ==="; cat /proc/pressure/$n 2>/dev/null; done'
capture meminfo.txt shell cat /proc/meminfo
capture systemui-frames.txt shell dumpsys gfxinfo com.android.systemui framestats
capture launcher-frames.txt shell dumpsys gfxinfo com.android.launcher3 framestats
if [[ -n "$pkg" ]]; then capture app-frames.txt shell dumpsys gfxinfo "$pkg" framestats; fi
# Only recent log lines: logcat can contain URLs, package names or personal data.
capture logcat.txt logcat -d -v threadtime -b main -b system -b events -t 2500
grep -E -i 'mCurrentFocus|mFocusedApp|mInputFocus|FocusedWindow|FocusedApplication|topResumedActivity|mResumedActivity|Input dispatching timed out|no focused window' \
    "$out/window.txt" "$out/input.txt" "$out/activities.txt" > "$out/focus-summary.txt" 2>/dev/null || true
grep -E -i 'Ignoring duplicate VSYNC|Skipped [0-9]+ frames|Missed App frame|RemoteAnimationTargets|Unknown focus tokens|Input dispatching timed out|not responding|WindowManager: Finish Transition|BLASTBufferQueue|Surface failed to disconnect' \
    "$out/logcat.txt" > "$out/rendering-signals.txt" 2>/dev/null || true
echo "Saved read-only freeze diagnostics: $out"
echo "Capture once as 'frozen' before Recents, then once as 'recovered' afterward."
echo "Review/redact logcat.txt and window titles before sharing; neither apps nor services were restarted."

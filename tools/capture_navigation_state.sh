#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Read-only capture. Run once while gestures are frozen, then after the Settings workaround.
set -euo pipefail
navigation_adb=(adb)
if [[ "${1:-}" == --serial ]]; then
    [[ $# -ge 3 ]] || { echo 'Usage: capture_navigation_state.sh [--serial SERIAL] frozen|recovered [OUTPUT_DIR]' >&2; exit 2; }
    navigation_adb+=(-s "$2"); shift 2
fi
navigation_label="${1:-}"
[[ "$navigation_label" == frozen || "$navigation_label" == recovered ]] || {
    echo 'Usage: capture_navigation_state.sh [--serial SERIAL] frozen|recovered [OUTPUT_DIR]' >&2; exit 2;
}
navigation_root="${2:-out/navigation-diagnostics}"
navigation_output="$navigation_root/$(date -u +%Y%m%dT%H%M%SZ)-$navigation_label"
"${navigation_adb[@]}" get-state >/dev/null
mkdir -p -- "$navigation_output"
navigation_capture() {
    local navigation_name="$1"; shift
    if ! timeout 25s "${navigation_adb[@]}" "$@" >"$navigation_output/$navigation_name.txt" 2>&1; then
        echo "Incomplete: $navigation_name (partial output retained)" >&2
    fi
}
navigation_capture input shell dumpsys input
navigation_capture windows shell dumpsys window windows
navigation_capture activities shell dumpsys activity activities
navigation_capture recents shell dumpsys activity recents
navigation_capture services shell dumpsys activity services
navigation_capture systemui shell dumpsys activity service com.android.systemui/.SystemUIService
navigation_capture navigation-mode shell settings get secure navigation_mode
navigation_capture recents-component shell cmd overlay lookup android android:string/config_recentsComponentName
navigation_capture logcat logcat -b main -b system -b events -d -t 3000 -v threadtime
echo "Saved $navigation_label capture: $navigation_output"

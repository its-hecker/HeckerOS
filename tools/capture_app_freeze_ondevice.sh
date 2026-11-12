#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Bounded, read-only foreground-app freeze recorder for rooted Android/Termux.
# Start BEFORE using the app: su -c 'sh SCRIPT /data/data/com.termux/files/home/hecker-freeze 300'
# Outputs are private to Termux by default. Review/redact before sharing.
set -u

target="${1:-}"
duration="${2:-300}"
case "$duration" in
  ''|*[!0-9]*) echo "Duration must be an integer number of seconds" >&2; exit 2 ;;
esac
if [ "$duration" -lt 30 ] || [ "$duration" -gt 900 ]; then
  echo "Use a duration between 30 and 900 seconds" >&2
  exit 2
fi
if [ "$(id -u)" != 0 ]; then
  echo "Run as root: su -c 'sh SCRIPT OUTPUT_DIRECTORY 300'" >&2
  exit 1
fi
if [ -z "$target" ] || [ "$target" = "/" ]; then
  echo "Provide a private output directory, for example Termux HOME/hecker-freeze." >&2
  exit 2
fi
umask 077
stamp="$(date -u +%Y%m%d-%H%M%S)"
out="${target%/}/freeze-${stamp}"
mkdir -p "$out" || exit 1
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
{
  echo "Start UTC: $(now)"
  echo "Duration seconds: $duration"
  echo "Device: $(getprop ro.product.device)"
  echo "Build: $(getprop ro.build.fingerprint)"
  echo "No properties, power policies or processes are changed by this recorder."
} > "$out/about.txt"

# Continuous, tag-filtered event stream captures transients that a later
# logcat -d might miss. Never clear Android's existing logcat buffers.
logcat -v threadtime -b main -b system -b events -s \
  ActivityTaskManager:V ActivityManager:V WindowManager:V WindowManagerShell:V \
  InputDispatcher:V InputManagerService:V SurfaceFlinger:V \
  Choreographer:V FrameTracker:V HWUI:V BLASTBufferQueue:V \
  ViewRootImpl:V ShellRecents:V RecentsView:V TaskAnimationManager:V \
  BufferQueueProducer:V libEGL:V > "$out/important-events.log" 2>&1 &
logpid=$!
cleanup() {
  kill "$logpid" 2>/dev/null || true
  wait "$logpid" 2>/dev/null || true
}
trap 'cleanup' EXIT
trap 'exit 130' INT TERM HUP

start="$(date +%s)"
count=0
while :; do
  elapsed=$(( $(date +%s) - start ))
  [ "$elapsed" -ge "$duration" ] && break
  {
    echo "===== $(now) elapsed=${elapsed}s ====="
    # The important distinction: foreground Activity != input-focused window.
    dumpsys -t 3 window 2>&1 | grep -Ei \
      'mCurrentFocus|mFocusedApp|mTopFocusedDisplayId|mInputFocus|mFocus|focusedWindow|focusedApp|unknown focus|InputMonitor' | head -n 40
    dumpsys -t 3 input 2>&1 | grep -Ei \
      'FocusedWindow|FocusedApplication|FocusedDisplay|TouchStates|Input dispatching timed out|DispatchEnabled|DispatchFrozen' | head -n 35
    dumpsys -t 3 activity activities 2>&1 | grep -Ei \
      'topResumedActivity|mResumedActivity|mFocusedRootTask|mLastPausedActivity|TaskRecord|topActivity=' | head -n 35
  } >> "$out/focus-timeline.txt"
  if [ $(( count % 6 )) -eq 0 ]; then
    # Less frequent compositor/display probes: keep the recorder lightweight.
    dumpsys -t 4 SurfaceFlinger --list > "$out/layers-${count}.txt" 2>&1 || true
    dumpsys -t 4 display > "$out/display-${count}.txt" 2>&1 || true
    { echo "===== $(now) ====="; cat /proc/pressure/cpu /proc/pressure/memory /proc/pressure/io 2>/dev/null; } >> "$out/pressure.txt"
  fi
  count=$((count + 1))
  sleep 5
done

# Store detailed state at the end, not during the foreground freeze itself.
dumpsys -t 6 SurfaceFlinger > "$out/surfaceflinger-final.txt" 2>&1 || true
dumpsys -t 6 window > "$out/window-final.txt" 2>&1 || true
dumpsys -t 6 input > "$out/input-final.txt" 2>&1 || true
dumpsys -t 6 activity activities > "$out/activities-final.txt" 2>&1 || true
logcat -d -v threadtime -b main -b system -b events -t 2500 > "$out/logcat-tail.txt" 2>&1 || true
echo "End UTC: $(now)" >> "$out/about.txt"
echo "Saved to: $out"
echo "Review/redact window titles, package details and logs before sharing."

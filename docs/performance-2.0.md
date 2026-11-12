# HeckerOS Smoothness 2.0 — Coral experiment

This is a **performance integration branch**, not a published/validated 2.0 release.
The ROM version property remains unchanged until ServerHive builds and Coral tests pass.
Start on Coral, then replay the reviewed changes on Flame with a separate device manifest.

## Implemented code tracks

| # | Area | Change | What to verify |
|---|---|---|---|
| 1 | SystemUI / QS blur | In the matching frameworks/base branch, collapse requests with identical quantized radius/scale; keep one Choreographer callback per vsync, suppress per-frame warning spam. Memoize selected QS tile page topology. | No rendering regressions across QQS/full QS, notification shade and lockscreen; collect FrameTimeline. |
| 2 | Power HAL | Shorten **LAUNCH** hint lifetime from 3000ms to 1500ms for 17 CPU/GPU/bus actions; retain CAMERA_LAUNCH and sustained/game/thermal hints. | Check hot and cold app startup, FPS, heat and battery vs stable; revert if late launch phases regress. |
| 3 | Memory | Enable PSI-based LMKD on the PSI-capable Floral kernel; retain 6 GB normal-RAM model and fast LZ4 zRAM setup. **No force-kill or arbitrary swappiness change.** | Check /proc/pressure/memory, swap, lmkd events and background app retention after long sessions. |
| 4 | Launcher | Carry over the previous performance branch's style/geometry caching; debounce full view-tree scans until layout settles. Keep off-UI-thread wallpaper decode and blur. | Home, dock, folders, widgets, recents, icon packs, accessibility and wallpaper changes. |
| 5 | Background telemetry | Bound active GameSpace FPS redraw/status work to 4Hz; retain native task FPS callback, no polling after sessions stop. | Games, FPS overlay, hidden/visible panel, mode switching, notifications and suspend. |
| 6 | Animations | Bound decorative media glow to about 20Hz, charging shimmer to 20Hz; stop Motion Island activity when detached/hidden and reuse its artwork path/ellipsized text. | No visible judder of nondecorative animations; AOD, battery charging, media and Motion Sense. |
| 7 | ART | Prioritize speed dexpreopt for SystemUI, HeckerLauncher and HeckerSuite. Other packages stay on the platform's profile-oriented default. | Ensure these modules resolve in Soong, measure image size and cold start; GMS/OTA compatibility. |
| 8 | 90Hz | Extend SurfaceFlinger idle selection timeout 80 -> 150ms. **Do not force** fixed 90Hz; retain content detection, touch/thermal/low-brightness gates. | Measure 60/90 transitions, idle draw, thermal and low brightness flicker. |

## Source guard

From a cloned Suite repository:

```bash
python3 tools/check_smoothness_2.py
```

From the fully synced Android root:

```bash
python3 vendor/hecker/floral/suite/tools/check_smoothness_2.py --rom-root "$PWD"
```

These checks validate source wiring and expected code paths, **not** Android compilation or performance.

## Baseline/experiment capture

With a USB-connected Coral and a working `adb`:

```bash
bash vendor/hecker/floral/suite/tools/capture_smoothness.sh stable
# Flash/boot the test image, repeat identical tasks after thermal stabilization.
bash vendor/hecker/floral/suite/tools/capture_smoothness.sh perf2
```

Repeat each scenario at least five times. Capture Perfetto FrameTimeline for actual missed frames and UI thread/render-thread/GPU scheduling. Test idle/battery separately; don't mistake animation shortening for faster rendering.

Core acceptance scenarios:
1. Expand QQS/full QS and close quickly 20+ times, with and without media, OneUI and native layouts, slider mirror and blur 0/high.
2. Home/recents swipes, drawer search, large icon pack, open/close folder and widget; repeat after wallpaper change.
3. Cold/warm launch Camera, Settings, Chrome, messaging; do it once at ambient and after 10 minutes of sustained workload.
4. 2-hour mixed navigation/media/notifications: no systematic app reload increase vs stable; no elevated PSI thrashing or LMKD kill spikes.
5. GameSpace inactive vs game foreground; FPS counters and cleanup do not run while not in a game.
6. Motion Sense/Face Unlock, IR camera and Dolby still work; preserve KernelSU Next and plugin pins.
7. Brightness/lockscreen 60/90Hz and ambient: no flicker regressions in low light.
8. Check SystemUI, Launcher, Hecker Suite compilation, ROM output size, first boot/OTA behavior and SELinux denials.

Use the **same build variant**, apps, brightness, refresh policy, battery state and approximate device temperature on both runs. No performance gains are claimed until the traces are compared.

## Intermittent foreground-app freeze triage (before switching to Recents)

A frozen frame or unresponsive touch that recovers after opening and closing Recents
does **not** prove an app ANR. Potential causes include lost input focus, a stale
BLAST/SurfaceFlinger surface, an app-specific render-thread stall, or a refresh-rate
transition. Earlier November logs included a Launcher input timeout with no focused
window; do not assume all apps share that cause without a fresh freeze-time capture.

From a **USB-connected Linux host with authorized ADB**, while the application
is still frozen, run the read-only capture **before touching Recents**:

```bash
bash vendor/hecker/floral/suite/tools/capture_app_freeze.sh frozen
# Now open Recents, return to the SAME app without force-closing it:
bash vendor/hecker/floral/suite/tools/capture_app_freeze.sh recovered
```

Optionally give an app package as the second argument to capture its FrameStats
(e.g. `frozen com.example.app`). Each command records a separate timestamped
`app-freeze-<label>-*` directory with input/window focus, resumed Activity,
surface layers, display mode, pressure, frames and bounded logcat. Compare
`focus-summary.txt` and `window.txt` first. A null focused window with a
resumed Activity supports investigating WM/input-focus transitions; valid focus
with stale app frame timestamps points toward rendering. Correlate with Perfetto
FrameTimeline and SurfaceFlinger before modifying the compositor.

The capture does not restart an app, force a frame, change properties, or
grant permissions. Dumpsys/logcat can expose application names, URLs or
notification metadata: review and redact sensitive information before sharing.
Do not treat the diagnostics as proof that random freezes are fixed. Verify with
the same app and same steps on stable and the experimental 2.0 build.

## 12 November 2026 LogFox capture: two reported app freezes

The user reported two freezes while recording a 45-second LogFox excerpt
(approximately 19:13:14–19:13:59 PKT). Both return-to-Recents gestures
originate in `org.telegram.plus`, which uses the unchanged Pixel Launcher
Quickstep/Recents provider; Hecker Launcher is the separate Home provider.

Observed signals in this log, without asserting causation:

- **21 HWC duplicate VSYNC warnings** from SurfaceFlinger in bursts.
  SurfaceFlinger is filtering repeated hardware timestamps. This warrants a
  controlled 60 Hz versus adaptive-90 Hz comparison but does not prove a
  multi-second compositor stall.
- Telegram resumed from **the normal cached-app freezer** at 19:13:23.233,
  followed by **641 skipped frames** at 19:13:23.243. The ~7-second interval
  since it was frozen explains most of that skipped-frame counter and is
  **not** evidence of foreground main-thread overload.
- The START_RECENTS transitions #1169 and #1174 lasted **7,718.874 ms**
  and **13,097.082 ms** respectively because they remained active until a
  user action completed Recents. Long lifetime alone is not a stuck
  transition; both received finish callbacks.
- Quickstep logged **four missing animation-target task 170** errors after
  returning to the separate Hecker Home task, plus numerous
  `A resource failed to call release` and one
  `HardwareBuffer.close` warning. Review Quickstep's task-snapshot
  lifecycle and separate Home/Recents compatibility if later traces
  correlate them with the freeze.
- The native launcher logged SurfaceView/BLAST disconnect cleanup warnings,
  while SystemUI logged one 54 ms shade-collapse jank frame and several
  duplicate blur-value warnings. The existing 2.0 blur deduplication may
  address those warnings, **not** necessarily the reported app freezes.
- **No explicit input-dispatch ANR, fatal app exception or focus-window
  timeout** appears in this excerpt. Earlier November launcher ANR is a
  separate event, not established as the same issue.

**Next controlled comparison**: Disable *Smooth Display* temporarily
(60 Hz) and repeat the same app interactions, then re-enable it and repeat
at adaptive 90 Hz. Keep temperature, apps and gestures constant. For a
future freeze, collect `capture_app_freeze.sh frozen` **before** entering
Recents and `recovered` afterward; correlate input focus with
SurfaceFlinger and per-app FrameStats. Do not globally disable cached-app
freezer, force-restart apps, alter Quickstep transaction handling or
rewrite HWC scheduling without isolating the actual failure.

## Rooted Pixel 4 XL: on-device flight recorder (no PC required)

When a freeze disappears as soon as Recents opens, a log collected only
**after** leaving the app cannot establish whether the frozen app had focus.
Start this low-frequency recorder from Termux **before** reproducing the issue,
then switch to the target app without exiting the rooted process:

```sh
curl -fL 'https://raw.githubusercontent.com/its-hecker/HeckerOS/perf/heckeros-2.0/tools/capture_app_freeze_ondevice.sh' -o "$HOME/hecker-freeze.sh"
mkdir -p "$HOME/hecker-freeze"
su -c "sh $HOME/hecker-freeze.sh $HOME/hecker-freeze 300" > "$HOME/hecker-freeze-run.log" 2>&1 &
```

Use the app normally for up to five minutes. This captures focused-window,
foreground Activity, InputDispatcher, FrameTracker, HWC and Quickstep messages
continuously, including before Recents changes the state; sampled Surface
layers and pressure help separate input stalls from rendering failures. At
five minutes it terminates, leaving timestamped snapshots under
`$HOME/hecker-freeze/freeze-*`. It requires existing KernelSU/su permission.
No phone property, governor, settings or app process is changed.

The recorder runs as root and uses a private umask, so completed capture
directories and their files may be root-owned even though the parent directory
belongs to Termux. **Wait for the 300-second recorder to finish** before
changing ownership or creating an archive. Verify its exit state and files:

```sh
cat "$HOME/hecker-freeze-run.log"
ls -la "$HOME/hecker-freeze"
```

If a `freeze-YYYYMMDD-HHMMSS` directory exists, restore read access **only**
to the Termux app UID/GID (not world-readable), then make an archive:

```sh
su -c "chown -R $(id -u):$(id -g) $HOME/hecker-freeze"
tar -czf "$HOME/hecker-freeze.tar.gz" -C "$HOME" hecker-freeze
ls -lh "$HOME/hecker-freeze.tar.gz"
```

Review/redact potentially sensitive activity titles, app details and logs
before sharing. If `hecker-freeze-run.log` reports a failure or the output
directory is empty, include the run log (after redaction) instead of claiming
capture succeeded. This is for evidence gathering, not a claimed freeze fix.

**60Hz control**: the reported freeze persists even with Smooth Display off.
Therefore disabling 90Hz transitions is not an effective workaround; retain
default display behavior. Prioritize stale input focus/gesture consumers
versus an app rendering stall at a single captured freeze timestamp.


## 12 November 20:46 PKT rooted recorder — predictive Back not completed, Recents re-entry freeze

**Corrected user reproducer (12 November 22:07 PKT):** From inside an app,
perform an edge Back gesture **without actually navigating back** (the user
may cancel the gesture or Back may fail to navigate), swipe to **Recents**,
then select/re-enter that same app. The UI freezes **on returning to the
app**, not necessarily while swiping Back/Home. Leaving the app again and
returning recovers it. The prior description, "completed Back then
immediately Home, which freezes", was incorrect.

The supplied `hecker-freeze.tar.gz` contains an older 19:43 PKT session and
another started at 20:46:45 PKT. The latter was archived before its final
snapshots but has `important-events.log` and `focus-timeline.txt`.

The second recorder contains a *compatible gesture sequence*:

- **20:48:19.806:** Edge-swipe takes gesture ownership from Settings and the
  swipe-up monitor. WM Shell starts `PREDICTIVE_BACK` **#1525** at
  **20:48:19.818**, with Settings task **263** focused.
- **20:48:20.395:** `CLOSE_PREDICTIVE_BACK` **#1526** contains
  `restoreBackNav`. Both **#1525** and **#1526** finish by 20:48:20.433.
  The restore operation supports a *cancelled/restored* predictive-back
  transition rather than a successfully completed Back navigation.
- **20:48:20.796–20:48:20.836:** Pixel Launcher Quickstep starts
  `START_RECENTS_TRANSITION` **#1527**; InputDispatcher transfers input
  ownership from Settings and edge-swipe to the swipe-up monitor.
  The transition's closing/pausing task is Settings **263**, but
  `RecentsView.onGestureAnimationStart` initially describes Telegram
  task **262**. This task-target discrepancy is worth checking, not proof
  of the freeze.
- **20:48:21.630:** Edge-swipe *again* steals input from
  `recents_animation_input_consumer` while the Recents session is ongoing.
  Investigate input-consumer lifecycle and whether touch focus is cleanly
  restored on re-entering the task.
- **20:48:34.939:** The Recents controller reports cleanup and a finish
  callback after user navigation. Its approximately 14-second interactive
  lifetime does **not** by itself prove a stuck animation.
- **20:48:36.116:** A second `PREDICTIVE_BACK` **#1530** occurs, but it is
  followed by a `CLOSE` request for Settings **263** instead of
  `restoreBackNav`. **Do not** mislabel this second sequence as a
  cancelled Back gesture.
- The sampled InputDispatcher state has `DispatchEnabled: true` and
  `DispatchFrozen: false` at sampled times. The capture contains no
  unambiguous freeze-time ANR or confirmed stuck input consumer.
  5-second sampling can miss transient handoff failures.

**Leading hypotheses (not confirmed fixes):** predictive-back cancellation
cleanup, stale Quickstep/Recents animation input consumer, mismatched
current-task snapshot/target, focus or app surface restoration when selecting
the task from Recents. The device uses Pixel Launcher Quickstep as Recents
provider and Hecker Launcher separately as Home provider. Do not attribute
the cause to either without direct evidence.

**Required regression cases** with the same target app and display settings:
1. Back *cancelled* / navigates nowhere → Recents → re-enter same task.
2. Back *committed*, actually navigates → Recents → re-enter task.
3. Recents → re-enter task without Back.
4. Back cancelled → pause two seconds → Recents → re-enter task.

When the UI freezes, preserve a *timestamped frozen vs recovered* focus,
input-consumer, frame, and surface snapshot before touching Home/Recents to
recover. Examine `BackAnimationController`, `EdgeBackGestureHandler`,
`ShellRecents`, `InputDispatcher`, and Quickstep transitions at the
same timestamp. Avoid global gesture suppression, forced transition
completion, launcher replacement, or stable-branch merges without a
buildable, independently verified cause.


## Experimental Recents task-surface restore candidate (12 November 2026)

**Source patch (not yet a verified freeze fix):**
`its-hecker/heckeros_frameworks_base` on `perf/heckeros-2.0`, source
commit `3eaa11f515566f1499e858fdffdf581bc7aef44c`, with regression
test commit `9394d9ea861bb82b309948eb0d0c50dcc5d378ba`.
The Coral `HeckerOs-localmanifest` performance branch now pins
`9394d9ea861bb82b309948eb0d0c50dcc5d378ba`.

When `RecentsController.finishInner()` returns to the pausing/current app,
it already reorders the task and calls `SurfaceControl.Transaction.show()`.
The experiment explicitly sets that *returning task surface's* alpha to
`1f` in the same finish transaction, to avoid carrying a stale surface
alpha through canceled predictive Back → Recents → app re-entry.
This **does not disable predictive Back**, affect the Recents→Home branch,
or change touch/input dispatch policies. It targets a frozen-*looking*
compositor surface, **not** all causes of actual unresponsive touch.
A new `RecentsTransitionHandlerTest` verifies that this return-to-app
transaction both shows the task surface and restores opacity.

**Validation:** The linked manifest checks are green; Android WM Shell JUnit,
full Soong build, and device reproduction **have not been run**. Treat this
as an experimental *candidate*, not confirmation of a fixed freeze.

Build the paired revisions from `perf/heckeros-2.0`. On Coral, test these
four cases repeatedly against the previous stable image:
1. Partially swipe Back and cancel it → Recents → re-enter same task.
2. Commit Back → Recents → re-enter same task.
3. Recents → re-enter task without any Back gesture.
4. Cancel Back → wait two seconds → Recents → re-enter same task.

Repeat with Settings, Chrome, Telegram and ChatGPT. Inspect touch,
frame updates, focused window, current resumed activity and surface alpha.
If touch is still blocked with correct focus, capture an immediate frozen
snapshot; this change must not be claimed effective unless measured. Also
validate translucency, PiP, split-screen, task switching, launcher/Home,
live tiles, refresh rate and gesture cancellation. Roll back the framework
pin if those features regress. Keep stable and OTA untouched.

## Rollback

No main/default branches were modified. Use the previous `cnb_latest_stable` manifest for a known baseline. Do not cherry-pick only the suite without the matching framework and device tree commits; pinned revisions are part of this experiment.

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

## Rollback

No main/default branches were modified. Use the previous `cnb_latest_stable` manifest for a known baseline. Do not cherry-pick only the suite without the matching framework and device tree commits; pinned revisions are part of this experiment.

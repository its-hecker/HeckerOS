# HeckerOS fluidity refinement — Coral experimental pass 2

**Branch:** `perf/heckeros-fluid-pass2-20261113` based on
`perf/settings-fluid-20261113`, preserving the previously promoted eight
performance improvements from `cnb_latest_stable`. This is a source-level
experiment and must not be published as a stable release/OTA without testing.

## Changes in this pass

### Settings startup
- The Hecker Settings voice-search adapter no longer resolves a speech
  recognizer through PackageManager synchronously during homepage startup.
  The system recognizer is invoked on tap, retaining the existing fallback
  to Settings search on a missing/blocked handler.
- On top of pass 1's preference row/drawable allocation reductions, the
  native homepage suggestion fallback is changed from **300 ms to 180 ms**.
  Optional suggestions that arrive slowly may not appear on that launch.
  This is a bounded, experimental responsiveness tradeoff and must be
  tested for flicker, contextual cards, accessibility and deep-link behavior.

### Suite dashboard
- Cache Customizations and About panels **per Activity instance**.
  Switching tabs detaches/reattaches the existing panel instead of
  allocating the full grid and About content for every toggle.
- The cache is not static, and configuration changes build fresh
  activity-local views. Existing controls, callbacks and save-state remain.

### Launcher and memory
- Recompute glass geometry revision only if the actual styling changes.
  Redraw glass panels only for changed style/targets; skip an offscreen
  global-layout scan. Keep late layout/widget discovery.
- When glass is disabled, avoid decoding/storing a full wallpaper bitmap.
  Reload asynchronously when enabled again. Skip queued blur jobs whose
  generation no longer matches, using visible cross-thread state.
- Native game monitoring now reads/parses its Settings configuration once
  per user/config epoch. ContentObserver/user-switch invalidation keeps
  it current. Avoid repeated PackageManager classification for the
  already-observed focused game/package. Retain event-driven task checks;
  do not poll more frequently.

### Motion and frame pacing
- Optional Hecker-flow folder animation: **360 ms → 260 ms**, with its
  existing spring/easing and reduced-motion/battery-saver gate intact.
- Launcher Studio page fade: **180 ms → 140 ms**, retaining the
  ValueAnimator-enabled check.
- Media-glow activity change: **500 ms → 300 ms**. Keep decorative
  media light updates at their previously reduced cadence.
- Motion Island and Motion Sense astronaut posts are aligned with vsync
  via View.postOnAnimationDelayed(32 ms), without increasing redraw
  frequency or creating a frame loop when hidden.
- Added `tools/measure_ui_fluidity.sh` for comparable Settings,
  SystemUI, Home, Pixel Quickstep, pressure, memory and thermal snapshots.

## Important unresolved gesture issues

**Cancelled Back → Recents → re-enter app can still freeze.** This is an
independent functional bug; the previous experimental task-alpha patch
was proven ineffective and must **not** be promoted from that branch.

**Home-gesture closing jump for an app only in the drawer:** Hecker Launcher
is a HOME-only Launcher3 app (`com.android.launcher3`); the installed
Google Pixel Launcher (`com.google.android.apps.nexuslauncher`) remains
the **Quickstep/Recents animation provider**. That provider owns target
selection and remote task-to-Home animation. The available source-controlled
Hecker Launcher cannot safely force Pixel Quickstep to use a missing Home
icon as an animation target. Avoid faking an icon target or injecting a
global window animation override: this can cause wrong landing positions,
back/home races and other navigation bugs.

`tools/capture_drawer_home_jump.sh` captures the transition and focus
state without changing ROM settings. Reproduce:
1. App **only in drawer** → launch → swipe Home.
2. Same app **pinned to visible desktop** → launch → swipe Home.
3. App in a folder → launch → swipe Home.
4. Repeat with current Hecker HOME and, if desired, separately test
   Pixel HOME as the default (do not alter Quickstep provider in production).

Compare shell/Quickstep transition events and FrameTimeline. The correct
eventual fix is in a source-owned Quickstep provider's app-close-to-Home
fallback/target mapping, or an independently vetted modification to the
existing Quickstep provider—not a Hecker Launcher UI-only patch.
No claim that the drawer jump has been fixed in this pass.

## Acceptance and measurement

- Build full Coral ROM with the paired local-manifest branch; ensure
  Hecker Launcher, Settings and SystemUI compile and boot.
- Compare pass-1/cnb baseline with pass-2 using the same build variant,
  refresh-rate setting, battery and thermal state.
- Compare cold/warm Settings startup via
  `bash tools/measure_settings_startup.sh baseline cold 7`, then candidate;
  inspect `am start -W` raw output. It is *not* a fully-drawn metric.
- Run the same 5-minute Settings → Suite tabs → folder open/close →
  Quick Settings → Recents workflow for baseline and candidate;
  capture `bash tools/measure_ui_fluidity.sh baseline` then candidate.
  Use Perfetto FrameTimeline for real 60/90 Hz deadlines and 95th-percentile
  frame-time comparisons.
- Check all Suite links, About, Lottie and Android lifecycle events,
  dark theme, search, voice-search fallback, suggestion cards, accessibility,
  app drawer, widgets, folders, wallpapers, glass enable/disable, rapid
  gesture sequences and thermal/battery behavior.
- Revert any optimization that introduces flicker, mis-targeted animation,
  lost suggestions/search, stale game monitor state, increased retained
  bitmaps, ANRs, battery regression or increased missed frame deadlines.

**Validation status:** Code guards and workflow included; Android unit,
full-Soong compile, boot, cold/warm launch and device FrameTimeline
verification are separate required acceptance steps.

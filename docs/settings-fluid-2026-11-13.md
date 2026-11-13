# HeckerOS Settings fluidity — stable source integration, 13 November 2026

**Target:** `cnb_latest_stable` — selective promotion of Settings-only work from
`perf/settings-fluid-20261113` and `perf/heckeros-fluid-pass2-20261113`.
Source merged, but no speedup is claimed until a full ROM build and on-device
comparison. Do not publish an OTA without the acceptance checks below. The existing predictive Back → Recents freeze
remains unresolved and is **out of scope** for this change.

## Implemented — reduce repeated homepage work

1. `HeckerHomepageStyle.bind()` used to create three `GradientDrawable`
   objects, two inset drawables and a `RippleDrawable` on **every**
   preference row bind, even if neither selection nor density changed.
   Cache only per-View selection/density and a weak reference to the existing
   background. Reuse the background on ordinary rebinds; rebuild if the
   selected state, display density or actual background changes. Recycling
   remains correct because the ripple doesn't depend on the preference key.
   The original ripple/highlight visuals are preserved.
2. Reuse each icon frame's circular `GradientDrawable`; change its colour
   only when the recycled row binds to a differently coloured preference.
   Icon glyphs, restrictions, destination links and original per-row titles
   stay native.
3. Construct the decorative `HeckerOS` branded title span once per
   `HeckerBrandPreference` instance, rather than on every bind.
4. Render ordinary middle-row card backgrounds as rectangles, avoiding
   rounded-path construction on every scroll frame; retain rounded corners
   for group-start and group-end rows.
5. Add `tests/test_settings_fluid.py` and CI source/shell guards.
6. Add `tools/measure_settings_startup.sh` for reproducible cold/warm
   Settings launch timings and `gfxinfo` snapshots.

**Scope:** This targets UI allocations on binding/rebinding and scrolling.
It may improve Settings first draw and scrolling, but no benefit or percentage
has been verified by measurement yet.

## Startup changes included from pass 2 (not device validated)

The Settings voice-search UI no longer performs synchronous PackageManager
speech-activity resolution while binding the homepage. It invokes the system
recognizer on tap, with the existing Settings-search fallback retained.

The Android 17 SettingsHomepageActivity optional-suggestions visibility timeout
changes from 300 ms to 180 ms via the guarded settings patch adapter.
It can reduce a startup wait but may omit slowly arriving contextual suggestions
on that launch. Validate against real providers, theme changes, search,
accessibility and deep links; revert this one timer adjustment if it causes
missing cards or noticeable flicker. The timer itself is not proof of faster
rendering, and remaining startup cost should be profiled with Perfetto.

## Repeatable performance comparison

Build and flash two **same-variant** ROM images (stable baseline, then
experimental) and compare on the same Coral device. For each one run on a
computer with authorized ADB:

```bash
# From a checked-out HeckerOS Suite repository:
bash tools/measure_settings_startup.sh baseline warm 7
bash tools/measure_settings_startup.sh baseline cold 7
# After flashing the test build:
bash tools/measure_settings_startup.sh candidate warm 7
bash tools/measure_settings_startup.sh candidate cold 7
```

The script stores per-run `am start -W` output and a CSV, plus Settings
`gfxinfo framestats` and memory state. **Cold** mode calls
`am force-stop com.android.settings`; save unfinished Settings tasks first.
Warm mode returns Home without killing Settings. No ROM property,
governor or policy is changed. `TotalTime` is a launch wait metric, **not**
the same as fully-drawn time or a rendered frame percentile.

Compare the median launch times for like-for-like modes, inspect the raw
launch outputs for failures or missing timings, and gather Perfetto
FrameTimeline traces for Settings, shade, Recents and launcher interactions.
For 90 Hz, inspect missed 11.1 ms frame deadlines, not animation duration.
Use consistent screen brightness, refresh mode, battery/thermal state,
app data, launcher and build variant.

### Acceptance

- Home appears and scrolls with no changed card colour, ripple or selection
  outline, even after repeatedly entering/exiting Settings and changing
  light/dark mode, display size and font scale.
- Account, connected devices, battery, search, Settings deep links,
  accessibility, parental controls, contextual suggestions and Hecker Suite
  shortcuts still work.
- Both cold and warm launch times and frame percentiles show no regression;
  aim to reduce median loading latency and 95th-percentile jank in traces.
- No elevated memory consumption, repeated drawable invalidation, crash/ANR,
  excessive cached-app eviction, thermal increase or measurable battery loss.
- Existing back/Recents freeze remains tracked separately; don't mark it
  resolved based on a Settings performance patch.

## Next profiling targets

1. `SettingsHomepageActivity`: the 300 ms suggestion gate; native category
   providers, search indexing and any sync Binder/PM lookups on main thread.
2. Hecker Suite's own category screens: lazy controller initialization,
   avoid reloading full themes and icon assets on every tab visit.
3. QS and lock screen: FrameTimeline missed deadlines during blur/transition
   and Motion Island motion; preserve accessibility and system visual semantics.
4. Launcher: cold/warm app launch, Recents task snapshot redraw and icon
   retrieval; debug unfinished Back gesture freeze as a separate correctness
   issue.
5. Power/memory: inspect LMKD pressure, thermal throttling and wakeups before
   any further boost or idle policy changes.

**Do not** globally disable animations, force 90 Hz, keep all apps resident,
reduce LMKD thresholds indiscriminately or force CPU/GPU governors. Those
approaches can increase heat, power use and frame instability.

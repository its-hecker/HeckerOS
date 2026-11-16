# HeckerOS 2.0 — Launcher, gaming and animation stable-source promotion (13 Nov 2026)

**Scope:** Selectively brought from `perf/heckeros-fluid-pass2-20261113` to Coral `cnb_latest_stable` and Flame `perf/flame-smoothness` without replacing the existing MD3 QS/media style or the newer Flame/Coral configuration.

## Included
- Hecker Launcher glass: avoid offscreen scans and redundant geometry/invalidation; drop stale wallpaper blur jobs, and do not decode wallpaper when glass is disabled.
- Launcher Studio fade 180 → 140 ms; optional Hecker-flow folder transition 360 → 260 ms. Native defaults apply when Hecker-flow is disabled.
- Game Monitor: cache per-user configuration, recheck on observed changes, reuse known current game identity rather than repeated PackageManager classification.
- Motion Island and Motion Sense astronaut: align active decorative frames with View.postOnAnimationDelayed(32 ms), avoiding new loops while hidden.
- Media glow transitions 500 → 300 ms, preserving MD3 dynamic colors and the current decorative refresh cadence.
- Non-mutating ADB tools to measure UI fluidity and capture the **separate** app-drawer-to-Home animation jump.

## Explicitly excluded
- **No Back → Recents freeze investigation, workaround, or task-alpha changes**.
- No untested Quickstep/Recents provider replacement or assertion that the drawer-to-Home jump is fixed. That transition is owned by the Pixel Quickstep provider, not the Launcher3 HOME-only UI.
- No Suite tab-caching experiment; the current system-blur and other stable Suite controls are preserved.

## Validation before release
1. Run source tests and compile the entire ROM for both devices.
2. Confirm the Launcher is stable with glass on/off, wallpaper changes, folders, widgets and drawer; check that background images are released when glass is disabled.
3. Verify Game Monitor reacts to config changes, user switching, game → non-game transitions and resume.
4. Verify Motion Island/astronaut stop drawing when hidden and media transitions use Material You colors.
5. Check folder and Studio animations at 60/90 Hz, animation reduction, and battery saver.
6. Collect frame timing and thermal/LMKD data to ensure no regression; don't infer improved FPS/battery life from source-level changes alone.

The drawer closing animation remains an open issue. The Back/Recents screen freeze was intentionally untouched.

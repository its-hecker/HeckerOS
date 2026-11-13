# HeckerOS navigation pill — cosmetic-only stable integration

Date: 2026-11-13
Devices: Pixel 4 XL (coral) and Pixel 4 (flame)

## What is on stable

The existing `cnb_latest_stable` and Flame `perf/flame-smoothness` trees
already contained the **functional** pill-safe fix before this promotion:

- Only the visual `org.hecker.overlay.launcher.hidepill` RRO is packaged.
- `HeckerGestureSpace`, which set framework `taskbar_stashed_size` to
  zero, is not in `PRODUCT_PACKAGES`. The framework's native bottom
  inset / touch geometry is preserved.
- The toggle changes the visual handle only, and turns off the legacy
  framework inset RRO atomically if it is still installed and enabled.
- Opening Gestures and navigation also repairs a legacy enabled inset
  off the UI thread, without changing the user's hide-pill choice.
- The implementation checks RRO target packages and respects current-user
  scope. Existing Home/Back/Recents navigation must remain native.

This promotion brings across extra migration, multiuser, wrong-overlay
target and failure-state host regressions, checks that the RRO affects
*colors only*, adds a dedicated GitHub Actions test job, and updates
localized descriptions. It does **not** copy the old experimental controller
over the already-merged stable implementation. The preference also
refreshes the toggle availability after a migration.

## Not included

- The older Recents task-alpha experiment, or any Quickstep animation
  provider or inset geometry changes.
- No claim that the Back/Recents freeze or app-drawer-to-Home jump is fixed
  on a device until a ROM build is flashed and reproduced successfully.

## Acceptance on physical Coral/Flame

1. Build the ROM using the updated manifest; check Suite/Settings and
   the `HeckerHideGesturePill` overlay are included and boot normally.
2. Hide the pill and reproduce the prior interrupted Back → Recents →
   return-to-app interaction repeatedly, then turn it back on and repeat.
3. Verify native bottom spacing, gesture targets, rotations, split screen,
   PiP and 3-button navigation are unchanged.
4. On an OTA upgrade from an old ROM with the framework zero-inset
   overlay enabled, open Gestures and navigation. Confirm the legacy
   overlay is no longer enabled; check cosmetic hide preference persisted.
5. Check overlay state via the read-only command:
   `adb shell cmd overlay list --user 0`.
   The legacy navspace overlay must be absent or disabled.
6. Test both launcher modes used by the ROM and verify that the
   visual hide-pill RRO is actually effective in the installed Quickstep
   build. If the handle remains visible, fix the drawing target only,
   not `taskbar_stashed_size`.
7. Review the new `gesture-pill` CI job plus full Android build logs.

The source-level fix is promoted, but physical-device behavior has
not been independently verified in this GitHub-only change.

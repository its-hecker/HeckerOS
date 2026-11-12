# HeckerOS gesture-pill regression — freeze after disabled navigation handle

Date: 2026-11-13
Branch: `fix/gesture-pill-freeze-20261113`
Based on: `cnb_latest_stable` (independent of the experimental Settings/performance branches)

## User-reproduced behavior

With "Hide gesture pill and bottom space" **enabled**, the user reports
a UI freeze after partially swiping Back → opening Recents → returning
to the app. With the pill **enabled/visible**, those freezes do not occur.
This is stronger evidence than the prior Recents task-surface opacity
hypothesis, which did not fix the freeze.

## Source inspection

The original controller toggled two runtime resource overlays together:

1. `org.hecker.overlay.launcher.hidepill`: Pixel Launcher
   `taskbar_stashed_handle_light_color` and
   `taskbar_stashed_handle_dark_color` become transparent.
2. `org.hecker.overlay.framework.navspace`: framework
   `taskbar_stashed_size` is changed to **0dp**.

The second overlay changes taskbar layout/inset geometry rather than only
the visual navigation handle. The input/animation handoff with Pixel
Launcher Quickstep may depend on that geometry. This is the **leading
hypothesis, not yet a proven low-level root cause**.

## Experimental fix

- Retire `HeckerGestureSpace` from `PRODUCT_PACKAGES`. Keep its source
  declaration and old package ID for migration/recovery compatibility.
- The setting now toggles only the cosmetic Pixel Launcher handle overlay,
  and simultaneously disables the legacy framework zero-inset overlay if
  it still exists.
- On entering the Gestures and navigation settings page, repair any
  previously enabled legacy inset overlay off the UI thread; retain the
  user's cosmetic pill preference. This avoids a persisted unsafe setting
  when flashing onto an older installed build.
- When the old overlay is missing on a clean ROM, the toggle continues
  to operate on its visual part without depending on the missing package.
- Localized labels explicitly state that gesture spacing remains native.
- The host Java gesture overlay test now verifies safe toggle, upgrade
  recovery, failure behavior, other-user isolation, missing legacy overlay,
  and overlay target validation. Additional source guards ensure the
  zero-inset RRO isn't packaged and that the handle RRO changes colors only.

## Device tests before stable merge

Test both Coral userdebug and Flame when possible:

1. On the currently installed ROM, show the pill and repeat cancelled Back
   → Recents → re-enter app multiple times. Verify freeze disappears.
2. On experimental ROM, toggle cosmetic pill **hidden** and repeat the same
   scenario 20+ times with Settings, Chrome, Telegram and another app.
3. Toggle pill visible and repeat; also try rapid Back→Home, app-drawer
   → Home, split-screen, PiP, home gesture from a full-screen game.
4. Verify the bottom application inset and gesture hit region stay native.
   Confirm overlay status:
   `cmd overlay list --user 0 | grep -E "navspace|hidepill"`.
   The new build should not install `org.hecker.overlay.framework.navspace`.
5. OTA migration: flash without wiping data from a build where both
   legacy RROs were enabled. Open Gestures and navigation; confirm the
   legacy space overlay is absent or disabled and that touch works.
6. Verify gestures in three-button mode and with accessibility
   touch navigation. Check all status/nav icons and rotation.
7. Check that the visual hide effect is still effective. If only the
   stashed-taskbar handle changes, consider a separate properly targeted
   SystemUI **drawing-only** overlay rather than reintroducing 0dp insets.

**Do not** merge the ineffective Recents task-alpha patch. Do not claim
that the exact freeze is fixed until the user confirms it on a booted build.

## Current build workaround (Termux, owner user)

```sh
su -c 'cmd overlay disable --user 0 org.hecker.overlay.framework.navspace'
su -c 'cmd overlay disable --user 0 org.hecker.overlay.launcher.hidepill'
su -c 'cmd overlay list --user 0 | grep -E "navspace|hidepill"'
```

The commands only change two HeckerOS overlays and preserve ordinary
gesture navigation. A reboot may be needed for the old input state to settle.

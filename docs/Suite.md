# Hecker Suite behavior and device validation

HeckerOS Android 17 targets coral first. The approved Settings design, two-column Suite cards, offline random Lottie greeting and central About page accompany the native App Lock, Motion Sense, Gaming and infrared camera pages. Suite also provides native Quick Settings controls, launcher selection, opt-in synchronized lyrics and the offline Lunaris theme picker.

## Architecture

`HeckerSuite` is platform-signed, privileged, installed on `system_ext`, and has
its own UID. The dashboard is exported; the three Quick Settings services require the system BIND_QUICK_SETTINGS_TILE permission. App Lock configuration is guarded
by a strong biometric/device-credential prompt; every actual selection change
is authenticated again by Android's system-owned setup flow.

`LOCK_APPS` remains `internal|role|recents`. A small guarded source patch adds a
separate **signature** permission and requires the exact privileged Suite UID
and platform signature before permitting App Lock metadata queries and requests
for the native authenticated setup PendingIntent. The Suite never receives
`TEST_LOCK_APPS`, cannot set native unlock state, and cannot fake a system
authentication result. All existing lockout, token and strong-biometric logic is
retained.

The `hecker_suite` release configuration inherits `cp2a` and enables
`android.security.app_lock_apis`, `android.security.app_lock_core`, and SystemUI
`scene_container` through
normal aconfig value sets. No generated Java flags, base release flags or
device-wide runtime configuration are overwritten.

## Integration

Use the coral build instructions in the root README. All sources are pinned by the dedicated HeckerOS local manifest. The complete product is `hecker_coral-hecker_suite-userdebug`; rebuilding Suite alone cannot enable framework, Settings or SystemUI changes.

The preparation command validates every source anchor before writing, is idempotent, and preserves backups under `out/hecker-suite-source-backups`. To undo only owned, unchanged adapters before syncing a different source baseline, run:

```bash
python3 vendor/hecker/floral/suite/tools/prepare.py --revert
```

Motion Sense's separate Settings entry and duplicate maintainer/contact sections are hidden in Suite builds. Original plugin files, hardware permissions and upstream credits remain intact.

The Launcher page sets Android's native HOME role and offers an explicit restart when changing the signed Recents provider overlay. Double tap to sleep calls the native power API; it does not use device administrator locking. Pixel Launcher remains installed.

Lock-screen lyrics are off by default. After opt-in, SystemUI sends the playing song's artist/title to LRCLIB, matches the recording and synchronizes LRC timestamps with native playback position/speed. They follow lock-screen media visibility and stop on AOD. Suite itself has no Internet permission.

Theme assets are hash-verified before Soong. The picker uses preinstalled, platform-signed resource APKs and native overlay transactions; it does not download or sideload apps at runtime. Keep warning/no-internet/satellite/unknown battery states native when no matching themed state exists.

## Gaming mode

The native hub follows the approved charcoal/cyan design: original procedural
game artwork with the installed app's icon, game cards, and Games/Tools/Style tabs.
Games declaring `CATEGORY_GAME` or `FLAG_IS_GAME` are detected automatically;
untagged games can be added manually. Long-press a game card to remove it.
Accent choices are cyan, violet, lime and amber, with edge positioning and opacity.

Enable Gaming mode once in the hub. The user-enabled foreground service has an
ongoing notification with a Turn off action. It restores after boot only if
previously enabled. Native task-stack events discover games opened from any
launcher. There is no idle polling; active sessions refresh status once a second.
An inward swipe on the edge handle opens the dashboard. The floating counter can
be moved by holding and dragging it.

The dashboard includes:

- A game-task `WindowManager.registerTaskFpsCallback` counter and live history.
  This measures the task's presented frames, not display refresh rate. Missing or
  stale samples show an em dash; there are no mock readings or shell polling.
- Wi-Fi/mobile connection status, battery percentage and **battery temperature**.
  The thermometer is not a claimed CPU/GPU temperature sensor or thermal camera.
- Media volume and a temporary display-brightness adjustment. Brightness returns
  to Android's normal control when the gaming session ends.
- Cleanup requests for cached, non-system apps in the current user only. The
  game, Suite, shortcut UIDs, launcher, keyboard and active foreground/services
  are excluded. No force-stop, root command, memory-gain or FPS-boost claim.
- Focus through Android 17's app-owned implicit DND rule, after the user grants
  policy access. Leaving a game deactivates Suite's own rule.
- A screenshot action that redacts secure/protected surfaces and saves a PNG to
  `Pictures/HeckerSuite`. The gaming overlay is hidden for the capture.
- Screen recording through Android's consent flow, with an ongoing Stop action.
  This version records **video without audio**, at up to 1920 pixels on the long
  edge and 30 fps, and saves MP4s to `Movies/HeckerSuite`.
- Touch lock, released by holding the screen for two seconds or leaving the game.

The shortcut rail accepts up to five installed apps. Shortcuts use native
`WINDOWING_MODE_FREEFORM` tasks with bounds and an always-on-top request, preserving
the game's separate task underneath. They are real app windows with Android's
window controls, rather than WebViews or app screenshots. Apps that declare they
cannot resize report unavailability; they are not silently launched full screen.
Native App Lock and all other system authorization still run before a shortcut
opens. A visible game session is retained while a shortcut popup is focused.

The build installs `hecker-suite-freeform.xml` on `system_ext` to advertise native
freeform-window support. It does not enable force-resizing or globally override
app compatibility. `HeckerSuite` requires this file and its expanded, same-partition
privileged allowlist. The Suite dashboard is the only exported activity; the monitor,
recording service, consent activity and boot receiver are private. There is no
Internet permission, accessibility service or unprotected external control API.
Gaming overlays and FPS subscriptions end at the lock screen, when switching away
from a game, or when Gaming mode is disabled. Screen capture/recording and the
native freeform window decorations still require validation in an actual ROM.

## Infrared camera behavior

The viewer requests CAMERA permission normally and has a narrow SYSTEM_CAMERA
allowlist. It identifies the front MONO/NIR sensor metadata and Pixel 4's known
physical NIR IDs 6/7, including physical-camera routing when available. It tries
the sensor's advertised Y8/luma or RAW16/RAW10/RAW12 streams and converts the real
monochrome plane with its strides and black/white levels. It never substitutes
the regular selfie camera or fabricates infrared images.

Opening may still be refused by a particular vendor HAL, or a secure-only sensor
may provide no accessible stream. In that case the page reports unavailability.
This needs on-phone validation; source discovery cannot prove streaming works.
The viewer closes camera/session/image resources on leaving or locking the
screen, and late open callbacks immediately close their devices. It does not
modify the IR emitter, Face HAL, calibration, firmware or enrollment. It is a
near-infrared viewer, not a thermal camera.

## Verification

```bash
bash tests/run_host_tests.sh
python3 tools/prepare.py --rom-root /path/to/android --check
```

Host checks exercise reversible patching, reject unfamiliar inputs before writes,
verify package/UID/signature authorization, and decode packed/padded raw test
planes, game/popup visibility, native-authentication exclusion, cached-app cleanup
protections and FPS sample validation. CI compiles the app against checksum-locked
Android **system SDK**/Lottie dependencies and links its Android resources. Two
compile-only task-listener declarations cover APIs omitted from the system SDK;
preparation checks their real framework declarations. These test declarations
are not packaged in the Android app. These are source checks; a full ROM build and
hardware verification are still required.

Phone checks after flashing:

1. Open Settings → Hecker Suite; check light/dark mode and animations.
2. Enter App Lock with the enrolled 3D face and with the device credential.
3. Protect a test app, cancel its unlock, then authenticate successfully.
4. Repeat from a notification, deep link, Recents and split screen; verify that
   protected content is not shown before authentication.
5. Lock the phone, reopen the test app, reboot, and repeat with PIN fallback.
6. Disable App Lock through the native authenticated flow.
7. Grant camera access, try both available IR sensors, save a photo, leave the
   viewer, and test Face Unlock immediately afterward. Also lock the screen while
   viewing IR, and test permission revocation and concurrent-camera handling.
8. Enable Gaming mode, open a detected game from another launcher, and check the
   edge handle, inward swipe, live FPS, graph, battery readings and Wi-Fi panel.
   Add an untagged game, remove one, and verify the detection settings persist.
9. Add a chat/gallery shortcut; open it over a game, drag/resize/close the native
   window, and confirm the game remains underneath. Repeat with a shortcut
   protected by App Lock: authentication must run and gaming overlays must hide.
   Verify an incompatible app reports failure without a full-screen fallback.
10. Test volume, temporary brightness, Focus and cached-app cleanup while music
    or another foreground service is running. Check normal brightness and Suite's
    DND rule are restored on leaving or locking the phone.
11. Save a screenshot, start/cancel/stop recording, rotate, and leave/lock the game.
    Confirm valid saved files, native capture indicators and secure-content
    redaction. Verify the Turn off notification action also ends recording.
12. Test touch lock/release, both dashboard edges, movable FPS, opacity/accent
    changes, mode disable/enable, reboot and overlay/notification permission changes.

Useful diagnostics:

```bash
adb shell dumpsys package org.hecker.suite
adb shell dumpsys face
adb shell dumpsys media.camera
adb logcat -b all -d > hecker-suite-logcat.txt
```


Additional coral checks: compare all three approved Settings pages at normal and large font scales, in light/dark mode and work profiles; verify search/category badges; test both QS layouts, pagination/editor and volume limits; switch both launchers and validate Recents after reboot; check all theme categories, lyric pause/seek/song switching and AOD; verify boot intro-to-loop transition and early exit.

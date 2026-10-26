# Device feedback — dashboard, ambient glow, keybox and launcher

These are source changes on top of the previous dashboard-drag/ambient-charging
revision. A full ROM build and a newly flashed Coral test remain required.

## Game Dashboard

Android 17 `SafeActivityOptions` rejects `taskAlwaysOnTop` for callers other than
system/SystemUI. Suite is a separate UID, so its previous request rejected the
entire popup launch. The restricted flag is removed; native freeform task mode,
bounds, separate task and display selection remain. The ROM already installs the
freeform feature and Android 17's WM Shell creates its freeform components from
that feature. No root window manager or activity embedding is needed.

Both orientations now use three action tiles per row. Brightness and volume sit
side by side in the second control row, immediately after Game mode/Screenshot/
Record. Content scrolls above the fixed app dock; drag and resize handles remain
separate.

## Motion Sense ambient glow

The pinned Oslo APK's genuine animation callbacks now request a native doze pulse
when the phone is locked and dozing. This lets the display refresh for the
plugin's actual gesture feedback. The bridge preserves the original callback,
does not inject gestures or falsify dozing/pulsing state, and excludes idle,
disengage and doze-fade transitions to prevent pulse loops. Native eligibility,
proximity and power suppression remain in control. Transition retries are bounded;
callbacks are restored when a feedback view is removed or the plugin disconnects.
The earlier stable GL host, initial state replay and display/tick refresh remain.

The callback contract was checked against the actual pinned Oslo APK:
`5f6c2e0c908e4d726eb62543c8d7f8492ade99b1d9043baa415873093598ac54`.
Persistent glow on a fully suspended display is not asserted by host checks.

## Hecker Launcher

The HOME hotseat creates its own Hecker Search surface even when a replacement
QSB provider supplies no widget. The native measurement/inset path reserves a
48dp search row when the profile provides no size, keeps it above navigation and
places it below dock icons rather than in an inline QSB slot. It has a cyan
caracal mark, teal/purple colours and an independent **Hecker search below dock**
toggle in Launcher settings > Universal search. Tap opens the local search;
long press opens its settings. Native landscape vertical docks retain their
existing layout. Pixel Launcher remains the Recents provider.

## TrickyStore and PIF

PIF follows the current Evolution X Android 17 service and its Play Store options.
The overlapping PixelOS certified-property loader, attestation-blocking entry and
legacy `KeyboxImitationHooks` generation/read paths are removed. Photos and the
existing scoped native store remain independent.

The importer accepts ordinary matching EC/RSA keyboxes from manual imports; it
has no provider/repository restriction. JCA key construction, BOM and wrapped
Base64 normalization, and certificate-supplied SEC1 curve parameters address
format/provider compatibility. Invalid pairs are still rejected atomically with
a specific, fully readable error. Fetch still checks real certificate status;
these changes do not guarantee an integrity verdict or a usable public keybox.

## Network Lab

Connected-Wi-Fi discovery and capability-gated controls for the phone's own
hotspot remain available. No NetCut-style interception or disruptive disconnect
attack is added. Router-client blocking and bandwidth control require an
authorized router management backend and the router/firmware details.

## Device acceptance

Open a dock app over a game, move/resize/close its freeform window and verify game
focus returns. Check both dashboard orientations and large text. Toggle dock
search off/on, rotate, reboot, and confirm existing home icons remain.

With Motion Sense enabled, test actual reach/gesture feedback on lockscreen,
Always-on and brief ambient, including covered proximity and SystemUI restart.
Repeat without media playback and with an active supported media app, since
Oslo subscriber policy still applies.

Test a known matching manual keybox and an intentionally malformed file; only
the matching file should replace the previous configuration. Check full fetch
failure details. Restart Google services after PIF changes, then measure the
actual device verdict separately from the certificate status.

## Host validation

The complete host suite (101 tests) and integration suite (77 tests) pass. Android
Java/resource/AIDL checks, native launcher measurement/inset adapters and native
doze eligibility checks pass. The importer/crypto checks use synthetic keys;
provider key material is not downloaded or bundled by these tests. The native
property bridge is checked with address/undefined-behavior sanitizers; leak
checking is unavailable under this environment's process tracing. Full Soong,
SELinux, visual and hardware acceptance remain pending.

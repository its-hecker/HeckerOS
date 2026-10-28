# QS slider percentages and display diagnostics

Both Hecker QS and the One UI reference QS now show a localized percentage in
the brightness and media-volume tracks. The percentage badge and icon remain
upright inside rotated vertical controls; wider controls accommodate large
font settings. A high-contrast badge keeps the value readable on both sides
of the moving fill.

Brightness percentages use the native gamma slider position, not raw backlight
values or nits. The label takes the current native value even while the track
animation is settling. Existing drag/commit callbacks, adaptive brightness,
policy restrictions, haptics and brightness mirroring remain native. Adding a
label does not write brightness or change panel/refresh settings.

Volume percentages use the actual media-stream index divided by its maximum.
Mute displays 0%; a nonzero allowed minimum is not falsely shown as mute. Both
QS layouts refresh after native volume/mute broadcasts and read back the
framework result after adjustments. Fixed-volume and native safe-volume rules
are retained. TalkBack receives the percentage through the slider state; the
decorative badge is not a separate accessibility stop.

`HeckerSliderValues` clamps invalid/degenerate inputs, handles half-percent
float rounding and formats through the current locale. Host checks cover the
full 0–65535 native gamma range, volume ranges 1–100, nonzero minima, five
locales and font scaling. The Compose check also compiles the exact native
brightness-track call against the shared renderer. Previously prepared QS
copies are recognized by hash, and the old native track adapter upgrades
without disturbing its callbacks; an actual pinned-source upgrade/idempotence/
revert check restores the original native file exactly.

## Low-brightness flicker

The report was clarified as phone-wide Pixel 4/4 XL low-brightness flicker during
dynamic refresh-rate switching, also observed on stock firmware. The practical
workaround is **Settings → Display → Smooth Display → off**, using fixed 60 Hz
instead of switching between 60 and 90 Hz. This trades smoother 90 Hz rendering
for avoiding those refresh transitions. Google documents the Smooth Display
control in its [Pixel display settings](https://support.google.com/pixelphone/answer/6111557?hl=en).

The experimental display branch now adds brightness-only low-zone protection
and exit hysteresis; see [Floral refresh protection](floral-display-flicker.md)
for the implementation, pinned inputs and required phone checks. It retains
the panel-specific entry brightness and protected refresh rate. Android's
display configuration explains why refresh-rate gamma changes can matter at
low brightness:
[AOSP DisplayDeviceConfig](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/DisplayDeviceConfig.java).

On an adb-connected host, run the read-only helper from this repository while
reproducing the problem:

```bash
bash tools/capture_display_state.sh hecker-display-capture
```

Use a new output directory. The helper captures brightness mode/level, refresh
preferences, Extra Dim state, 20 seconds of native display snapshots and
display-specific logs. It does not change settings or request root. Report
whether fixed 60 Hz stops the phone-wide symptom; include an external video
if available. Full ROM build, visual layout and
physical low-brightness validation still require the Coral phone.

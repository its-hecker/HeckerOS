# Floral low-brightness refresh protection

The experimental `test/floral-low-brightness-flicker-20261029` branch builds on
the combined Settings/depth/Game Space branch. Its matching local manifest pins
the changed framework and common device tree. Existing build branches are not
updated. The Flame manifest is not advanced before Coral phone validation.

The inherited Coral low-zone policy fixes the panel at 90 Hz only when display
brightness is at most 74 **and** ambient light is at most 63 lux. That permits
60/90 Hz switching at dim display settings in a brighter room, and lets ambient
noise release the protection without any display-brightness change. The new
Coral overlay sets the paired lux threshold to -1, the native display-only
sentinel. It retains the panel's existing entry threshold and 90 Hz protected
rate. Flame's existing display-only entry threshold of 16 and protected 60 Hz
rate are retained in the common tree.

`DisplayModeDirector.BrightnessObserver` also gains opt-in low-zone exit
hysteresis. The framework default is zero, retaining the existing policy for
other devices. Coral and Flame opt into four legacy brightness steps. Once
entered, Coral's vote remains until brightness exceeds 78; Flame's remains
until it exceeds 20. Re-entry still requires the original 74/16 thresholds.
These are native legacy brightness units, not slider percentages or measured
nits. The observer continues to read `BrightnessInfo.adjustedBrightness`.

The helper changes zone membership only. The existing native observer still
creates the physical-rate and disable-switching votes, obeys Smooth Display,
clears its votes in Battery Saver and retains native thermal voting. Policy
reload, fixed-rate settings, invalid brightness reads and display power-state
changes reset the hysteresis latch. No settings writer, polling service,
kernel change or new panel gamma table is introduced.

The implementation uses the framework's documented paired-threshold semantics:
[DisplayDeviceConfig](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/display/DisplayDeviceConfig.java).
The native refresh-selection and Battery Saver policy is described in
[AOSP multiple refresh rates](https://source.android.com/docs/core/graphics/multiple-refresh-rate).

## Validation

`tests/display/source-lock.json` locks the exact production inputs by commit and
SHA-256. Run:

```bash
python3 tools/check_display_flicker.py --aapt2 generated/sdk/aapt2
```

This compiles the actual production helper and extracts nine actual native
observer methods verbatim into a host fixture. Android service/sensor and vote
storage objects are fixtures; zone membership, voting, refresh-setting, Battery
Saver and display-state behavior are production code. The tests reproduce the
inherited bright-room protection gap, then cover both panels, entry/exit
boundaries, brightness/lux noise, fixed 60 Hz, forced 90 Hz, Battery Saver,
sleep/doze/wake, invalid reads and the unopted native path. AAPT2 compiles the
framework resource, internal symbol declaration and both device overlays.
This does not compile or run the complete system server or physical display.

## Coral phone acceptance

A new complete ROM build and flash of this experimental manifest are required.
Use Smooth Display on and the developer forced-peak setting off. Reproduce the
original dark-content symptom while alternating scroll and idle at low
brightness, in both dark and brighter rooms. Check native refresh votes and
an external video of the panel; screen recording cannot establish panel flicker.
Use `tools/capture_display_state.sh` to collect the display state.

Verify that brightness stays stable through scroll/idle and room-light changes,
that adaptive switching resumes above the exit boundary, that fixed 60 Hz and
Battery Saver are respected, and that AOD/sleep/wake still work. Record a
before/after result for the same handset. Physical flicker reduction and a full
ROM build remain unverified until those checks pass. If needed during testing,
Smooth Display off remains the manual fixed-60-Hz fallback.

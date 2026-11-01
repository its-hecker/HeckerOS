# Game pill alignment and manual proximity recovery

## Implemented changes

The GameSpace metric pill now gives all five metrics equal, padded columns.
Each label and value uses explicit centered text alignment and gravity; font
padding is removed, baseline coupling is disabled, and the rounded ends have
more inset. The existing large-font two-row layout, value auto-sizing, position
controls and real telemetry are retained. No screenshot-after or on-phone
rendering claim is made.

Hecker Suite now has **Sensor recovery**, also available as a Quick Settings
tile. It shows the actual default proximity reading and counts observed
near/far transitions while its page is visible. Its manual action requests
root and restarts only a supported, currently running Sensors HAL service.
The tile opens this page after unlock; it does not silently reset on click,
listening, boot, a constant reading or a timer. Recovery is not a calibration
and does not bypass the proximity check used by Face Unlock.

## Evidence and limits

The supplied before-reboot report contains 30 recent TMD3702V wake-up proximity
samples, all 0.00 cm (near). The after-reboot report contains seven recent
samples, all 5.00 cm (far). Both advertise 37 hardware sensors with the same
proximity handle. The videos show the stuck 0.0 cm/Face Unlock obstruction
behavior and normal behavior in the other capture. Other sensor activity
continues during the faulty capture. Tap-to-wake failure is not required to
identify this symptom.

These facts support a stuck-near proximity path. They do not prove a particular
kernel, DSP firmware, calibration or physical hardware defect. Occurrence
across ROMs is useful history, not a conclusive layer diagnosis. The supplied
PixelOS build is not the newly reviewed HeckerOS source. No recovery command
was executed on the affected phone here.

The device configuration uses Qualcomm SSC (sensors.ssc.so), and the capture
shows android.hardware.sensors@2.0-service.multihal. The AOSP init definition
names its service vendor.sensors-hal-2-0-multihal. A generic nanohub sysfs write
is not appropriate for this evidence. Restarting the HAL reconnects clients;
it is not a demonstrated physical sensor power-cycle or SLPI/DSP reset. If
cover/uncover remains stuck after it, the workaround has not recovered this
failure and deeper recovery still needs investigation on the actual phone.

AOSP HAL 2.x requires a stable sensor list across restarts. A vendor HAL that
violates that contract can cause Android runtime restart. The tool itself
never requests a phone/runtime reboot. Save work before the first device test.

Primary references:
- https://source.android.com/docs/core/interaction/sensors/sensors-hal2
- https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/sensors/2.0/multihal/android.hardware.sensors@2.0-service-multihal.rc
- Pinned HeckerOS device config: configs/sensors/sensors.hals.conf at device commit 4829092f7b7b988fc5fb2efe57c481f266364c71.

## Try on the existing rooted PixelOS build

Download app/assets/proximity-recover.sh from this source branch. The helper
supports coral/flame and an exact allowlist of Sensors HAL 2.x init names. It
refuses an unknown/stopped service and missing/ambiguous PIDs. It performs no
persist, calibration, firmware, sensor-list or SELinux-mode changes.

Windows CMD:

```bat
curl.exe -fL "https://raw.githubusercontent.com/its-hecker/HeckerOS/fix/device-ui-feedback-20261101/app/assets/proximity-recover.sh" -o proximity-recover.sh
adb push proximity-recover.sh /data/local/tmp/proximity-recover.sh
adb shell sh /data/local/tmp/proximity-recover.sh --status
adb shell "su -c 'sh /data/local/tmp/proximity-recover.sh --recover'"
```

End any call, unlock the phone and keep the top uncovered before recovery.
Root Manager must allow the Shell for the ADB command, or Hecker Suite for
the built-in page. The helper checks for a running service with a changed PID
within 20 seconds. RESULT=service_restarted confirms only that restart. Then
physically uncover, cover and uncover; verify the live near/far changes and
retry Face Unlock. A denied request, missing PID, timeout or unchanged PID is
an explicit failure, not a success toast.

The app runs the packaged script through a fixed su command on a worker, with
a 45-second process timeout and a 30-second request cooldown. It blocks its
action while locked or while AudioManager reports a call/communication mode.
The shell helper has an atomic cross-caller lock. It is a manual root tool;
it is not a service exposed to arbitrary apps. The Activity is private, its
Settings router is signature-protected, and the tile requires the platform
Quick Settings binding permission. Sensor listeners and UI polling stop when
the page pauses.

## Validation

- Complete Suite Java/resources/AIDL and independent native Settings dashboard compilation passed against locked SDK inputs.
- 158 Python host tests passed, including 12 production shell-helper tests for successful restart/PID verification, non-root status, root denial, unsupported devices/services, concurrency, property failure, PID fallback, missing PID, timeout and argument handling. Mock commands send no hardware requests.
- Resource/permission checks, Chinese translations/format arguments, shell syntax and git diff --check passed.
- Full ROM build, physical UI alignment and sensor recovery on the affected device remain pending.

Only this sanitized finding summary is published. The supplied bugreports and
videos remain outside the public source repository.

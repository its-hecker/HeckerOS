# Game pill alignment and manual proximity recovery

## Implemented changes

The GameSpace metric pill now gives all five metrics equal, padded columns.
Each label and value uses explicit centered text alignment and gravity; font
padding is removed, baseline coupling is disabled, and the rounded ends have
more inset. The existing large-font two-row layout, value auto-sizing, position
controls and real telemetry are retained. No screenshot-after or on-phone
rendering claim is made.

Hecker Suite has **Sensor recovery**, also available as a Quick Settings tile.
The action is now root-free: a signed-UID-checked Binder service in system_server
requests one of three fixed init restart actions. The app never launches su or
executes a shell script. The service authenticates package UID, platform signature
and foreground user on every app call; recovery additionally requires an awake,
unlocked phone with no active call. Both the acceptance check and worker check
run before the request. There is a global cooldown and one bounded operation.

Only init reads the protected raw debug-PID properties. It publishes a private
request-token receipt and before/after PID values. system_server reads that
receipt, checks a changed PID and a running HAL, then reports service restart.
The upstream init_svc_debug_prop read restriction is retained. The app has no
property-write capability and receives a status rather than raw PID access.

The page shows the default proximity reading and observed near/far transitions
while visible. Sensor recovery is not calibration or a Face Unlock proximity
bypass. Adding/listening to the tile, boot, a constant reading and timers never
request a recovery. The tile opens the page after unlock for a manual action.

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

## Availability and use

Root-free recovery needs a newly built/flashed HeckerOS containing the framework
service, its AIDL, init actions and SELinux policy. Updating the Suite APK alone
cannot add these platform components. An older PixelOS or stock image does not
provide this service; no root fallback is attempted.

Open Hecker Suite → Sensor recovery, or add the Sensor recovery tile. End any
call, unlock/wake the phone, leave the top uncovered and choose Restart sensor
service. After a verified restart, uncover/cover/uncover and confirm live near/far
changes, then retry Face Unlock. No root-manager grant is needed, including with
a non-KernelSU boot image of the matching ROM build.

An authorized ADB shell can use the same root-free broker:

```bat
adb shell cmd hecker_sensor_recovery recover
adb shell cmd hecker_sensor_recovery status
```

The included app/assets/proximity-recover.sh is now only a root-free command
client with bounded polling. Run it with sh after pushing it to the device;
there is no su command. It requires the updated ROM service. Shell requests are
restricted to the native shell/root UIDs and share the same foreground/unlock/
call/cooldown checks. Recovery is asynchronous; RESULT=running means accepted,
not yet verified. RESULT=service_restarted verifies the service restart only.

The private Activity uses an authenticated Binder client on a worker and polls
for at most 25 seconds. Its sensor listener and UI polling stop while paused.
The broker verifies results for up to 20 seconds. It rejects duplicate, stale,
missing/same-PID and unsupported-service cases without claiming recovery.
Calibration, firmware and persistent sensor settings are not changed.

## Validation

- Production broker state machine and access checks passed 452 host assertions: signed UID/foreground ownership, shell restriction, locked/call readiness, one request, cooldown, request receipts, stale/same/missing PID, bounded timeout and failures.
- The native broker and real generated AIDL compile against locked SDK inputs; hidden shell/Binder hooks are checked against two hash-locked platform source files.
- Complete Suite SDK Java/resources/AIDL and independent Settings dashboard compilation passed.
- 158 Python host tests passed, including the root-free shell client and Russian coverage tests. Mock tests send no hardware requests.
- 127 locked native input files apply, upgrade, repeat and revert; the two registered Java Binder services pass the actual native fuzzer-bindings checker. Root-free startup is checked before system-services-ready.
- SELinux/property/RC source constraints are checked, including no system_server read grant for raw debug-PID properties. Full platform SELinux/Soong compilation and physical phone testing remain pending.

Only this sanitized finding summary is published. The supplied bugreports and
videos remain outside the public source repository.

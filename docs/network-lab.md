# HeckerNetLab — implementation and review

Hecker Suite → **Network Lab** opens a native Android 17 dashboard with an original offline network-hub Lottie. The optional Network Lab QS tile opens it after unlock; it never changes connectivity by itself. English and Simplified Chinese strings are included.

This is a source implementation targeting coral. A full ROM/SELinux build and tests on the phone have **not** been performed. Publication was approved on 25 October 2026. The base source tree matches the previously published Suite commit `25f828c3c9cd7c9c84d3d43f71ec3a6e26d13fe2`.

## Implemented behavior

| Feature | Source behavior and actual limit |
| --- | --- |
| Devices | Native hotspot clients from Wi-Fi callbacks, DHCP addresses from TetheringManager, and an explicit LAN scan. Search, online/trusted filters, details and multiselect are available. |
| LAN discovery | Requests Android 17 local-network access. One TCP/80 reachability probe per target, bound to the selected Wi-Fi Network, with eight workers and 350 ms per connection. Only on-link RFC1918 IPv4 prefixes /16–/30 are accepted. A larger segment is capped to the phone's /24 window, at most 255 peer addresses. No public sweep, DNS lookup, packet capture or background scan. |
| Discovery accuracy | Refused TCP connections count as a responding address. Filtered/silent hosts may be absent; an IP is not a stable physical-device identity. Results age out of the active count after 30 seconds. IPv6-only LAN discovery is unavailable; native tethering may still report IPv6 client addresses. |
| Jail / pause | Primary-user Suite can request the separate controller to add a currently connected phone-hotspot client to its native block list. Requires the real HAL client-force-disconnect capability. This disconnects Wi-Fi association, including local access; it is not an internet-only firewall. |
| Status | `applying` means native configuration was accepted but disconnect has not been confirmed. `enforced` requires block-list readback and a disconnected-client observation. An unconfirmed disconnect is rolled back after 15 seconds. Neither state substitutes for a hardware traffic test. |
| Unjail / emergency restore | Remove only journaled NetLab MAC entries. Merge changes into a fresh native configuration, retaining unrelated entries and all other hotspot options. A restore-only notification action also works without the Suite UI. External allow-list policies are not overridden. |
| Duration and recovery | Session, 30-second, 5-minute, 1-hour and custom 1–86,400-second pauses. Timed actions require exact-alarm access and an idle exemption; diagnostics links to native settings if unavailable. Wall and elapsed deadlines are checked. Startup, hotspot shutdown and controller restart restore rules early. Rollback failures keep their ownership record and retry. |
| Trusted devices / names | Explicit metadata in Suite's private credential-encrypted app storage. Trust is a label, not authentication. A randomized MAC or a reused IP can refer to another device. |
| History | Opt-in, off by default; at most 128 records for seven days. Clear history retains explicitly chosen names/trust; clearing all metadata removes those too. Hotspot policy ownership is separate and is not erased by a metadata clear. |
| Defender | Passive default-gateway/route change observations. A read-only attempt to observe the gateway neighbour from `/proc/net/arp` may be denied by Android; that case explicitly says ARP visibility/protection is unavailable. Changes are informational/unverified, never an assertion that an attacker was detected. No ARP manipulation. |
| Traffic | Live receive/transmit deltas and an on-screen chart for this phone's Wi-Fi interface. Counter resets and unavailable counters do not become fabricated rates. No peer-wide traffic accounting is claimed. |
| Profiles | A bulk timed pause of current managed clients. It does not deny future hotspot arrivals. |
| Speed limiter | Download/upload numeric values, slider and Low/Balanced/Unlimited presets can be saved. Apply is disabled: **there is no validated per-device shaping backend in this version**. No rate-cap success is reported. |
| Router integration / NetLock / recurring schedules | **Unavailable in this version.** No router credentials are requested or stored. A router adapter, continuous allow-only lockdown, quotas and recurring schedules remain future work. |

## Privilege and lifecycle boundaries

Settings retains the existing Suite dashboard module. The feature Activity runs in `org.hecker.suite`; only it requests local-network runtime access and creates discovery sockets. Its new normal `INTERNET` permission is needed for those sockets; the previous no-Internet packaging guard is updated explicitly, not silently removed.

`HeckerNetLabController` is a **separate platform-signed privileged APK**, package `org.hecker.netlab`, with its own non-root UID. It receives only `NETWORK_SETTINGS` and `TETHER_PRIVILEGED` as new privileged grants. It has no Internet permission, shell-command endpoint, raw packet API, shared system UID, native `su` dependency or permissive SELinux change. It uses Android's existing Wi-Fi/Tethering Binder APIs.

The bound AIDL service exposes state, pause, restore and restore-all only. Every method enforces a signature permission, the exact Suite package, a matching signature, the primary user, and a non-shared UID. Another platform-signed application is insufficient. Secondary users can observe LANs through their own UI permission but cannot operate the device-global hotspot controller.

The persistent controller Application initializes only for user 0. Rule mutations are serialized off Binder/main threads. An AtomicFile ownership journal in private device-encrypted storage is written **before** native configuration changes. On process/reboot recovery, ownership is used to restore early, rather than resurrect restrictions. If the journal is unreadable, new controls and a false “restored all” result are refused; the UI directs the user to native hotspot settings. App-data clearing is disabled for the controller so it cannot discard ownership while leaving block-list entries installed.

Alarm deadlines are not continuously pushed back by dashboard polling. Timed rules fail open when reliable scheduling becomes unavailable. Boot-time service registration is retried with a bounded delay, and rule recovery remains pending until native readback verifies it. Notifications contain counts and a restore action, not device addresses. The controller audit ring is limited to 32 in-memory operation codes/timestamps.

Wi-Fi's configuration API has no cross-application compare-and-swap or ownership token. NetLab merges deltas into a fresh configuration, but another administrator simultaneously editing the **same MAC** still has ambiguous ownership. Do not edit the same client policy through multiple interfaces during a test. Test concurrent unrelated changes and configuration preservation on the phone.

## Build and verification

The product includes `HeckerNetLabController`; the Suite depends on its AIDL/common library. No new external source checkout, kernel patch or downloaded legacy APK is required. The controller and its two permission XMLs are mandatory post-build output checks. The companion local manifest mirrors the updated `integration/tools/check_built_output.py` and its regression fixture with the Suite source pin.

The controller links the explicit `framework-wifi.stubs.system`, `framework-tethering.stubs.system` and `framework-connectivity.stubs.system` API artifacts. A platform-API app cannot depend on the bare networking `java_sdk_library` names; their implementation libraries also restrict visibility. `TetheringManager` and `TetheredClient` belong to `framework-tethering`, not `framework-connectivity-t`. Suite's presentation code has no Wi-Fi library dependency. This corrects the 25 October Soong graph failure recorded in `error_log_its_hecker_coral_20261025-095336.txt`.

```bash
bash tests/run_host_tests.sh
python3 tools/compile_sdk.py --cache /path/to/locked/sdk/cache
python3 tools/check_netlab.py
python3 integration/tools/validate.py
python3 -m unittest discover -s integration/tests -p 'test_*.py'
```

The SDK check compiles the actual generated NetLab Binder API, both apps' Java/resources and the independent Settings UI module. Production policy tests exercise ownership, caller isolation, external rules, write/journal failures, ambiguous native responses, rollback, expiry, early restart restoration and private bounded discovery. The preview GIF/PNG were rendered from the shipped JSON with rlottie; animation is also subject to Suite's existing reduced-motion/lifecycle behavior.

Validation on 25 October 2026: **81 existing host tests**, **60 NetLab production policy/access/discovery checks**, **77 integration tests**, the complete app/controller/resource/AIDL compilation, independent Settings compilation, manifest validation, packaging/translation audit and rendered-animation checks passed. These are host/source checks, not a ROM build or a phone result.

The pinned Floral kernel defconfig (`heckeros-floral-kernel@48d6881fb8537f62811209927022e467d37e3a98`) enables `NET_SCHED`, `NET_SCH_HTB`, `NET_SCH_TBF`, `NET_SCH_INGRESS`, `NET_CLS_U32` and `NET_CLS_BPF`, plus IPA/RMNET. It contains no explicit `CONFIG_IFB=y` line. That is an audit of the input defconfig, not the final merged kernel configuration or proof that a tethering packet path can be shaped. The pinned device Wi-Fi overlay (`heckeros-floral-devicetree@8e19009ad632f66473ddcdb10c00595fefdb79b4`) advertises 32 hotspot clients but has no client-force-disconnect capability override. NetLab therefore uses the runtime HAL capability instead of asserting compatibility from these files.

## Required phone acceptance

1. Build with SELinux enforcing. Confirm the separate controller APK, grants and boot recovery; check that no privilege allow-list error occurs. Default operation must not depend on Magisk/KernelSU.
2. Open Network Lab from Settings and its QS tile in light/dark themes, large text and reduced motion. Confirm the first card stays below the toolbar and animations stop when hidden.
3. Grant/deny local-network access. Scan the user's own test LAN, change Wi-Fi/VPN during a scan, cancel it, and verify old results never migrate to the new connection. Test a silent host and an IPv6-only/client-isolated network.
4. Connect two owned test devices to the hotspot. Confirm actual HAL support before enabling controls. Pause one, verify disconnect/reconnection rejection and native block-list state; the second device must remain usable.
5. Test 30-second/custom expiry while the UI stays open, with the screen off/Doze, after a wall-clock change, after force-killing the UI, and after a controller crash/reboot. Early restore is expected across a controller/hotspot restart. Measure, do not infer deadlines from a slider.
6. Start with an unrelated native block-list entry and an allow-list policy. Confirm single/bulk/emergency restore leaves them intact. Exercise rejected writes, native service unavailability, hotspot restart and concurrent settings changes. A failed restore must remain visible and retry.
7. Check the notification's restore-only action, exact-alarm/idle-exemption denial or revocation, primary/secondary-user isolation and calls from an unrelated signed/unsigned test app. Test corrupt journal recovery on a disposable development image; it must never clear the entire native block list.
8. Verify local traffic against actual interface counters, absence of fabricated peer charts, opt-in history retention/deletion and honest Defender fallback when ARP access is denied.
9. Confirm speed Apply, unsupported router operations, allow-only lockdown and recurring schedules never report success. Per-device shaping needs a separately tested packet path/offload design before it can become available.

## Reference APK and primary API references

The supplied modified NetCut APK was inspected statically, never installed or run. Its fresh metadata/symbol audit is [network-lab-apk-audit.json](network-lab-apk-audit.json). The package is `com.lrcai.ravens`, version 1.7.7 (177), minimum API 14, target API 27; packaged native libraries are armeabi-v7a only. DEX metadata contains Jail/scheduling UI and speed/NetLock/Defender command names plus driver socket threads. App Cloner/SandHook instrumentation and an opaque native asset are present. These facts do not establish runtime implementation, safety or licensing. No supplied code, binary or artwork is embedded in HeckerOS; NetCut is feature inspiration only.

- [Android 17 local-network runtime permission](https://developer.android.com/about/versions/17/behavior-changes-17)
- [AOSP Soft AP capability and allow/block-list requirements](https://source.android.com/docs/core/connect/wifi-softap)
- [AOSP SoftApConfiguration](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/framework/java/android/net/wifi/SoftApConfiguration.java)
- [AOSP SoftApManager native client handling](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/SoftApManager.java)

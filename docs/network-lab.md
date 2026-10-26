# HeckerNetLab — implementation and review

Hecker Suite → **Network Lab** opens **NetLab**, a black-and-charcoal device list with **CUT**, **JAIL** and **OPTION** tabs. The header popup reads `NetLab` / `We are Hecker`; Report and Pro tabs are omitted. Each card exposes a timer/action menu, a pause/restore Wi-Fi button, IP/MAC copy and an expandable orange speed control. A dedicated top button starts/stops scans; the fixed footer contains monitoring, restore-all, master Wi-Fi pause/restore, refresh and search, with an orange group speed bar above it. See [netlab-ui.md](netlab-ui.md) for exact interaction and status semantics.

The source targets coral / Android 17. Host checks and SDK compilation are separate from a complete ROM/Soong build, SELinux validation and tests on the phone. The rooted backend has not been exercised on a live LAN here. The existing dashboard, launcher, ambient and integrity changes are retained.

## Connected Wi-Fi controls

1. Grant Suite local-network access. Press **Scan Wi-Fi** on the device list. **More → Advanced root controls** retains the separate root check and scan actions. Grant KernelSU access to **Hecker NetLab controller**. Merely opening the dashboard or polling state never invokes `su`.
2. Root scan resolves the gateway and probes the private IPv4 /24 window containing the phone, within its actual /16–/30 on-link prefix. It sends at most 25 ordinary ARP probes per second, examines at most 255 peer addresses, and stores only IP/MAC observations in memory.
3. Tap **Manage** beside a scanned device and choose **Pause internet (IPv4)** or **Speed limit**. For 1–32 devices, use **More → Control multiple devices**. Choose with independent upload/download caps and a 1–3,600-second duration. Rates are kbps per direction, per device. Zero leaves one direction unlimited; a limit requires at least one nonzero direction.
4. The helper re-resolves and pins the gateway and every selected IP/MAC before modifying ARP entries. A stale or changed device is rejected. The phone, gateway, network/broadcast addresses, public addresses and off-link targets cannot be selected.
5. Pause drops selected IPv4 packets addressed to the relay. Limit relays them in both directions with independent token buckets and bounded queues. IPv4 payloads and checksums are preserved; only Ethernet source/destination addresses change. Caps count Ethernet bytes, allow a short packet burst, and can be lower than the requested rate under load. No peer-total traffic accounting is inferred.
6. **Stop and restore**, individual Restore, Emergency restore and the persistent controller notification stop or release owned controls. The helper sends true ARP mappings repeatedly and briefly relays without caps while caches recover. Restoration is best effort: a lost interface, changed gateway, power loss or SIGKILL can require remote cache expiry/reconnection.

This controls **IPv4 traffic routed through the gateway**. Devices stay associated with Wi-Fi. IPv6 and direct local peer traffic remain outside this backend. AP/client isolation, ARP inspection, static ARP entries, driver restrictions and other network protections can prevent control. A started session is shown as unverified until packets from a selected device actually reach the relay; no universal internet-disconnection claim is made.

The phone must remain connected and awake. A bounded partial wake lock covers the explicit session. Native duration and an eight-second controller-heartbeat watchdog use boot-time monotonic time. EOF, controller loss, SIGTERM, SIGINT and SIGHUP initiate cleanup; no root session is saved or resumed on boot. The UI can close while the notification retains Stop. Active Wi-Fi identity changes or local-network permission revocation stop the session. On a different network the controller sends an abort that suppresses repairs; native cleanup also rechecks interface/address/route and the pinned gateway MAC.

Kernel IPv4 forwarding may already be enabled by Android. Before any relay announcements, the helper now installs a unique `HKNETLAB_<pid>` filter chain, with source/destination drops for the selected peer addresses only. Two first-position FORWARD jumps restrict the guard to ingress/egress on the selected Wi-Fi interface. The userspace Ethernet relay bypasses FORWARD, so kernel forwarding cannot duplicate or bypass its caps. Existing FORWARD policy, other chains, NAT, routing, offload and forwarding sysctls remain unchanged. Every two seconds it verifies both jump precedence and the exact owned rules; a changed or missing guard ends the session. A separate guardian removes the rules on normal stop, relay death (including SIGKILL), or the bounded session deadline. Failed setup rolls back, an existing same-name chain is never claimed, and failed cleanup is reported instead of claiming restoration. Firewall calls use fixed `/system/bin/iptables` argument arrays, bounded output and command deadlines, without a shell. SELinux enforcement and KernelSU policy are not modified. Unsupported raw sockets/packet filters, a failed transmit or a changed link terminates the request/session rather than reporting a verified cap. VPN conflicts must be resolved before starting.

## Native hotspot and dashboard behavior

| Feature | Behavior and actual limit |
| --- | --- |
| Phone-hotspot pause | Uses Android Wi-Fi callbacks and the native SoftAP block list. Requires real HAL client-force-disconnect support; disconnects Wi-Fi association, including local access. It does not require end-user root. |
| Hotspot status | `applying` means configuration accepted; `enforced` also requires block-list readback and a disconnected-client observation. Unconfirmed disconnect rolls back after 15 seconds. |
| Hotspot restore | Removes only journaled NetLab MACs from a fresh configuration, retaining unrelated entries/options. External allow-list policies are preserved. |
| Hotspot timers | Session, 30-second, five-minute, one-hour and custom 1–86,400-second actions. Timed hotspot rules require exact alarms and an idle exemption. Startup, hotspot shutdown and controller restart restore early. Failed restoration retains ownership and retries. |
| Root status | Waiting for selected IPv4 traffic, packets dropped, or packets relayed with caps. Per-session forwarded/dropped counters reflect only this helper. Individual release and full-session restoration remain best effort. |
| Discovery | Existing network-bound TCP/80 probes and bounded mDNS remain available without root. Root scan adds ARP IP/MAC resolution. Silent and isolated devices may still be absent; results age after 30 seconds. Names, models and OS hints remain unverified peer advertisements/DHCP data. |
| Saved speed presets | Retained separately; they do not apply native hotspot caps. Connected-Wi-Fi caps are applied from a device’s Manage menu, with the last values saved as defaults. Native hotspot shaping remains unavailable. |
| Defender / traffic | Passive gateway/route observations and this phone Wi-Fi interface counters. Defender itself never starts an ARP session and does not claim attack detection. |
| Metadata / history | Explicit names/trust are labels, not authentication. History is opt-in, off by default, bounded to 128 records/seven days. Clearing metadata never silently leaves/recreates a root session or erases hotspot ownership. |
| Router APIs / lockdown | No router credentials, router adapter, IPv6 control, future-client lockdown, quotas or recurring schedules. Root controls affect explicitly selected peers only. |

Wi-Fi configuration has no cross-application compare-and-swap ownership token. Another administrator editing the same hotspot MAC concurrently remains ambiguous; test concurrent unrelated changes and configuration preservation on the phone.

## Privilege and process boundaries

The presentation runs in `org.hecker.suite`. It owns discovery sockets and requests Android 17 `ACCESS_LOCAL_NETWORK`. `HeckerNetLabController` (`org.hecker.netlab`) remains a separate platform-signed APK with its own non-root UID. Existing minimal Wi-Fi/tethering/exact-alarm grants are retained; only normal `WAKE_LOCK` is added. The controller has no Internet permission, shared system UID, setuid executable, exported control broadcast or generic shell endpoint.

Every Binder operation enforces the signature permission, exact non-shared Suite UID/package, matching signature and owner user. New root Check/Scan/Start operations additionally enforce the calling Suite local-network grant. Stop and Restore remain available after that permission is revoked. A different platform-signed package is insufficient.

Only an explicit root action launches the fixed command `su -c 'exec /system_ext/bin/hecker-netlab-lan'`. The command contains no user input. A bounded stdin protocol carries validated interface/address/policy records; output is bounded and parsed strictly. Errors and notifications never include payloads or captured content. The native helper requires effective UID 0, validates the current interface itself, and queries IPv4 default routes across all kernel routing tables, including Android policy tables.

The helper uses a non-promiscuous AF_PACKET socket bound to the selected Wi-Fi interface. A classic BPF socket filter accepts ARP and only IPv4 Ethernet frames addressed to this phone; outgoing loopback copies are ignored. Only selected, pinned peers/gateway enter the relay. Queues are bounded per direction and scheduled fairly across peers. No packets are saved, TLS intercepted, credentials collected or arbitrary destinations scanned.

Hotspot mutations remain serialized with an AtomicFile ownership journal in device-encrypted storage, written before configuration changes. Root sessions deliberately use transient ownership and native watchdog cleanup instead of replaying restrictions after a crash/reboot. Each backend has a restore-only notification; Emergency restore stops the root backend even if the hotspot journal is unreadable. Root Binder returns acknowledge queuing, not proof of enforcement; the UI polls progress and measured counters.

## Build and verification

The native module opts into C++ exceptions with a module-local `-fexceptions`:
validation failures unwind its RAII descriptors and existing session cleanup.
Other Android modules keep their normal exception settings. The route reader
checks its signed remaining-byte count before passing an unsigned length to
Bionic's `NLMSG_OK`, including an unpadded final record.

The native host checker starts with Android-style `-fno-exceptions -fno-rtti`,
then reads the module's actual `cppflags` override from `Android.bp`. It also
uses Bionic's `NLMSG_OK` macro on the host to catch the signedness error that
glibc headers hide. The production route-reader regression wraps every socket,
receive, send, poll, close and clock call; it covers truncated/malformed records,
negative remaining counts, sequence filtering and Android policy-table routes
without network I/O. Both address and undefined-behavior sanitizers are enabled.

`HeckerNetLabController` requires the source-built C++17 `hecker-netlab-lan` module, installed in `system_ext/bin`. Post-build checks require its executable output, the controller APK and exact permission XMLs. The companion manifest mirrors the integration checker and regression fixture along with the Suite source pin. No downloaded legacy binary, external checkout or kernel patch is added.

The controller continues to link explicit Wi-Fi/tethering/connectivity system API stubs. SDK verification compiles the real generated AIDL plus Suite/controller Java, both resource sets and the independent Settings UI. The host suite includes the real native relay core under AddressSanitizer/UndefinedBehaviorSanitizer, bounded actual-helper preflight tests, and strict Java protocol tests. The host environment cannot perform LeakSanitizer process walks, so only that exit-time leak walk is disabled; address and undefined-behavior checks remain active. Tests never send ARP packets to a live network.

```bash
bash tests/run_host_tests.sh
python3 tools/compile_sdk.py --cache /path/to/locked/sdk/cache
python3 tools/check_netlab.py
python3 integration/tools/validate.py
python3 -m unittest discover -s integration/tests -p 'test_*.py'
```

Native coverage includes exact poison/repair mappings, unchanged IPv4 payloads, uplink/downlink MAC gating, unselected traffic, malformed frames, queue limits, independent measured cap budgets, fairness across 32 peers, release, duration/heartbeat expiry and strict configuration parsing. Entry-point tests use INFO or invalid link records that exit before raw-socket access. Non-root entry rejection substitutes only `geteuid()` in a separate host test executable; it does not depend on host UID-changing privileges.

Validation on 26 October 2026: 101 Python host tests, the complete existing Java host suite, 221 instrumented native core checks, 34 Java protocol checks, actual-helper build/preflight checks, 77 integration tests, the mirrored manifest checks, and complete Java/AIDL/resource SDK compilation passed. These results establish source and simulated packet behavior; they do not establish phone networking compatibility.

## Required phone acceptance

1. Perform a complete Android 17 Soong/ROM build with SELinux enforcing. Verify installed executable, controller permissions, KernelSU grant/denial and runtime local-network grant/denial. A denied capability must not appear as working control.
2. On an owned test LAN with two owned peers, root-scan and verify actual IP/MAC resolution. Select one peer, pause IPv4, and measure its IPv4 connectivity while the other remains usable. Association and IPv6 are expected to remain available.
3. Measure upload and download caps separately with sustained TCP and UDP, unequal rates and an unlimited direction. Check startup bursts, dropped/forwarded counters, queue saturation, throughput/CPU, MTU/GRO/driver behavior and multiple selected peers. Confirm direct local traffic is not described as blocked.
4. Stop/release one peer and stop the full session using both UI and notification. Observe actual gateway/peer ARP caches and traffic recovery; allow for remote-cache delays. Test duration expiry, screen off/Doze, closing the UI, loss of heartbeat, controller death, native SIGTERM and abrupt process/device loss.
5. Change Wi-Fi, reuse a subnet on another network, change a peer/gateway MAC, remove IPv4 and revoke local access during a session. Ensure old controls stop and repairs are suppressed on the new network. No automatic resume is expected.
6. Test client isolation, static ARP/ARP inspection, IPv6 preference, VPN conflict, raw socket/filter denial, transmit failure, kernel forwarding and enabling tethering during control. Status must remain unverified or stop where the path cannot be enforced.
7. Retest native hotspot capability, unrelated block-list preservation, timers, controller restart recovery, journal write/read failures and secondary/unrelated Binder callers. Root operations must not alter hotspot/NAT rules or add new controller privileges. The owned peer filter chain must disappear after Stop, expiry, relay SIGKILL and guard tampering.
8. Review light/dark themes, large text, Chinese resources, runtime permission flow, live statuses, counter labels, history opt-in/deletion and the restore action with the Suite UI closed.

## Reference APK and primary API references

The supplied modified NetCut APK was inspected statically, never installed or run. Its fresh metadata/symbol audit is [network-lab-apk-audit.json](network-lab-apk-audit.json). The package is `com.lrcai.ravens`, version 1.7.7 (177), minimum API 14, target API 27; packaged native libraries are armeabi-v7a only. DEX metadata contains Jail/scheduling UI and speed/NetLock/Defender command names plus driver socket threads. App Cloner/SandHook instrumentation and an opaque native asset are present. These facts do not establish runtime implementation, safety or licensing. No supplied code, binary or artwork is embedded in HeckerOS; NetCut is feature inspiration only.

- [Android 17 local-network runtime permission](https://developer.android.com/about/versions/17/behavior-changes-17)
- [AOSP Soft AP capability and allow/block-list requirements](https://source.android.com/docs/core/connect/wifi-softap)
- [AOSP SoftApConfiguration](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/framework/java/android/net/wifi/SoftApConfiguration.java)
- [AOSP SoftApManager native client handling](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/SoftApManager.java)
- [Linux packet sockets](https://man7.org/linux/man-pages/man7/packet.7.html)
- [Linux socket filters](https://docs.kernel.org/networking/filter.html)

## Device-feedback follow-up: forwarding guard

The guard regression uses a simulated firewall: it exercises exact selected-peer rules, preservation of pre-existing hotspot rules, precedence loss, partial setup rollback, refusal to take over an existing chain, normal stop and SIGKILL cleanup. It never changes the host firewall. The real native helper still compiles and runs its bounded preflight cases without sending LAN packets. End-user root is still required by this implementation: platform signing supplies Android API permissions, not Linux raw-packet or firewall capabilities. A root-free user experience would require a separately implemented, authenticated native ROM service and SELinux policy; this patch does not install such a service.

## NetLab reference UI

The current native presentation follows the supplied device-card reference, with black/charcoal surfaces, white text, an underlined Cut/Jail/Option tab row and orange controls. The header and temporary popup use NetLab branding. Option retains search/filter, multi-device controls, traffic, network information, history/options, diagnostics and advanced root controls. The separate top Scan/Stop scan button controls discovery. The fixed bottom Wi-Fi button and orange group bar control the current Wi-Fi peers together; gateway/self/offline/other-network entries are excluded and the existing 32-peer cap is enforced before confirmation. Refresh, search and restore remain separate routes.

Cards show known names or Untitled, IP/MAC copy, actual local trust/control state, expiry and manufacturer/device hints. A pinned, attributed offline MA-L/MA-M/MA-S table populates manufacturer assignments, combined with advertised model/vendor hints; private MACs never produce a guessed manufacturer. Density-scaled icons show known device types, manufacturer monograms or an unknown question mark, rather than assigning all peers a monitor icon. The timer opens the action/duration menu. The Wi-Fi icon pauses/restores, and the expanded speed bar opens the existing confirmed upload/download/duration flow. Router/offline protections, stale-scan checks, busy-session handling and hotspot/native routing are retained. Jail always exposes controlled devices independently of Cut filters. Monitor does not claim active defense; unknown state is not represented as verified protection or attack detection.

Opening or polling the page does not request root. Explicit Scan or an advanced control does. The backend privilege and IPv4/IPv6/association limits are unchanged. Java/AIDL/resource compilation and source/UI regressions pass; a full ROM build and phone visual/accessibility testing remain required. Detailed usage is in [netlab-ui.md](netlab-ui.md).

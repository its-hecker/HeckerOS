# HeckerVPN root TCP fallback — 8 November 2026

The ONIC capture showed WireGuard handshake packets leaving the correct physical
network, without matching replies during that capture. Official WireGuard also
failed on ONIC in both backends, while the same profile worked on other networks.
This does not establish that ONIC blocks all UDP. The working WhiteVPN screenshot
used an HTTP outbound from its public catalog, which is a different transport.

HeckerVPN now offers an explicit HTTP/HTTPS CONNECT fallback using root-owned TUN
and the existing app routing model. It does not register an Android `VpnService`,
request Android VPN consent, or create the Android VPN key indicator. The ordinary
HeckerVPN ongoing notification and disconnect control remain visible. The TCP
engine runs in userspace; the existing WireGuard backend still encrypts in kernel.

## Free configurations

Open **HeckerVPN → Free VPN → Refresh free TCP servers → Choose TCP server and
connect**. The selected entry becomes a saved profile, so the QS tile and **My
Profiles → App routing** work with it. Selecting another public server preserves
that public profile's app routing. Other saved profiles are retained.

- Public source: https://github.com/iampedii/whitedns-sub
- Downloaded catalog: https://raw.githubusercontent.com/iampedii/whitedns-sub/main/mihomo.yaml
- Native engine release/source provenance: `prebuilts/mihomo/NOTICE.md`

The current catalog produced three supported plain HTTP entries during validation.
Its TLS entries requested skipping certificate verification and were excluded.
Availability, credentials, countries and counts can change. Refresh if a saved
public server stops working; public services have no availability guarantee.

**Import HTTP/HTTPS proxy URL** also accepts a user's own proxy, for example
`https://user:password@proxy.example:443`. Percent-encode special characters in
credentials. HTTPS proxy certificates are always verified against system CAs.
This example is a placeholder, not a working server or included account.

Public HTTP proxies do not encrypt the phone-to-proxy link. HTTPS application
connections still validate their own TLS certificates. The UI labels HTTP versus
verified HTTPS and explains third-party visibility before server selection.

This fallback supports TCP. DNS from the configured tunnel resolver goes over
HTTPS through the selected proxy. Ordinary UDP is explicitly rejected, including
UDP used by some calling/gaming apps. There is no DIRECT fallback rule for routed
traffic. Kernel WireGuard remains available for full IP tunneling where reachable.
The app does not silently switch a private WireGuard profile to public proxy mode.

## Native integration

- `heckervpnd` authenticates the owner-user platform controller as before. It owns
  the private worker socket, routing journal, teardown and daemon-lifetime lease.
- `hecker-tcp-core` is a separate restricted SELinux domain. It opens a
  nonpersistent `hkvpn0` TUN, uses the checksum-locked arm64 FlClash/Mihomo C ABI,
  and accepts only validated HTTP outbound fields. It has no public listener or
  Binder access. Remote subscriptions cannot supply rules, scripts or filenames.
- Every outer socket receives `0xf0000 | physicalNetId`. This includes explicit
  physical selection, VPN bypass and system network permission bits. Failure to
  set the mark terminates the worker. Underlay changes recreate the worker/routes.
- The existing fixed netutils helper sets the IPv4/IPv6 routes, DNS and UID ranges.
  Root TCP setup does not call WireGuard configuration/statistics operations.
- Before Active, the native worker tests an HTTPS destination through the proxy
  and a DNS request through netd → TUN → proxy → DNS-over-HTTPS. It also checks
  that the TUN remains present, and periodically retests connectivity. No fake
  WireGuard handshake timestamp is created for a proxy connection.
- Saved profiles and the fetched catalog stay in the existing Keystore-encrypted
  store. A live root-only core configuration is removed during teardown. Neither
  diagnostics nor the Suite API expose account/proxy credentials.
- The build driver prepares the pinned core/JAR before Soong. Source, licenses,
  release/APK/ELF hashes and the native ABI are recorded. No release APK is installed.

## Validation

### 8 November device startup trace

The enforcing-phone log shows a root worker starting, then an error within about
50 ms. It reports denied reads of `/proc/net/tcp` and `/proc/stat`; the pinned
FlClash proc-table initializer treats the first read as optional, so those events
alone do not identify the fatal step. No root grant is missing.

The subsequent syscall trace shows successful executable/library loading and a
successful read of the private generated configuration. It does not reach the
TUN creation ioctl. Under tracing, the supervisor kills the still-initializing
worker at its 25-second deadline. Tracing itself adds substantial latency; this
does not prove the original untraced failure is a timeout or carrier filtering.

The follow-up gives the worker an explicit private HOME/XDG_CONFIG_HOME before
Go package initialization, a 45-second startup allowance (normal operation stays
at 25 seconds), and interrupt-tolerant frame polling. Each startup phase and
bounded error category/errno is retained in a root-only `startup-status` file.
The app error identifies initialization, configuration parsing/application,
library ABI, or TUN open/attach/start failure. Raw native parser messages are
discarded because they can quote credentials. SELinux remains enforcing and no
additional proc/private-data permission is granted based on these optional reads.

This is a startup robustness/diagnostics update. The original on-phone startup
failure and ONIC traffic still require a new device run; host checks do not
establish that TCP connectivity is fixed.

```sh
adb shell su -c 'cat /data/misc/heckervpn/tcp/startup-status'
```

The three integers are phase, failure category and errno. The same values are
translated to fixed, credential-free text on the HeckerVPN error card. The marker
survives failed teardown and is replaced on the next connection attempt.

Host checks cover the production config boundary and native bridge, asynchronous
callback ownership, UTF-8 authentication, failed socket marking, safe catalog
loading, app policy, actual Android Java/AIDL/resource compilation, and the existing
QS/Binder regressions. The generated configuration is also accepted by the pinned
upstream Mihomo 1.19.30 Linux parser without geodata files. Standalone platform
SELinux checks run for user/userdebug with all neverallows enabled and positive/
negative access queries for the new worker.

These checks do not replace a complete Floral ROM build or an enforcing-device
test. ONIC connectivity has not been validated on a phone from this workspace.

After installing, verify on ONIC with Wi-Fi off: pick a public TCP entry, wait for
Active, test the selected app, then test app inclusion/exclusion, QS connect/
disconnect, airplane mode and Wi-Fi/mobile handoffs. Check that no VPN permission
dialog/key appears and the ongoing routing notification remains usable.

For a failure, keep the HeckerVPN screen open and collect sanitized diagnostics:

```sh
adb shell dumpsys activity service org.hecker.vpn/.VpnService > heckervpn-state.txt
adb logcat -b all -d -s HeckerVPN > heckervpn-logcat.txt
adb shell su -c 'ip -d link show hkvpn0' > heckervpn-interface.txt
adb logcat -b all -d | rg 'avc: denied.*hecker_vpn_proxy|avc: denied.*hecker_vpnd' > heckervpn-policy.txt
```

Do not share live core configuration files or full HTTP packet payloads: those can
contain proxy authentication. Include the displayed error and whether another
public TCP entry works on the same network.

# HeckerVPN — native HeckerLab integration

Feature branch: `feat/heckervpn-20261104`, based on the Coral Launcher fix `3323772`.

## User experience

**HeckerLab → HeckerVPN** offers two sources for the same built-in connection engine:

| Source | Setup |
| --- | --- |
| Countries | Refresh Mullvad's active WireGuard catalog, add an existing personal Mullvad account, choose a country and city/server, then connect. |
| My Profiles | Import a compatible WireGuard `.conf` from a provider or existing connection, name it, select it and connect. |

Both sources share per-profile **All apps**, **Only selected apps**, and **All except
selected apps** policies, a searchable app picker with icons, traffic counters,
handshake age and a Quick Settings tile. No separate WireGuard app or launcher
icon is required. No server hosting, shared account, embedded private key,
subscription purchase or free endpoint service is supplied by HeckerOS.

The provider list is fetched on request, not fabricated or hard-coded. It requires
each user's own valid provider access. Manual profiles remain independent of
the provider adapter. Removing an account requests removal of this registered
device and erases the generated provider profile and local credentials, while
preserving manual profiles. Registration retries reuse the same saved key and
look up an existing device before creating another.

Connection state distinguishes disconnected, connecting, waiting for network,
configured but waiting for peer contact, active with recent contact, idle with
older contact, and errors. An interface alone does not produce an Active label.
An old handshake alone is not treated as an outage. The notification provides
status and Disconnect even when the page is closed.

## Implementation

| Component | Role |
| --- | --- |
| `app/src/org/hecker/suite/HeckerVpnActivity.java` | HeckerLab page, import and app picker. |
| `HeckerVpnClient.java`, `HeckerVpnTileService.java` | Asynchronous Binder client and QS toggle. |
| `vpn/aidl/…/IHeckerVpnService.aidl` | Signature-only structured control requests. |
| `vpn/src/…/VpnService.java`, `VpnEngine.java` | Separate persistent controller UID, caller checks and serialized lifecycle. |
| `VpnStore.java` | Private credential-encrypted storage and AES-GCM using an AndroidKeyStore wrapping key. |
| `MullvadProvider.java` | Optional catalog, account/device provisioning and generated profiles. |
| `vpn/native/daemon.cpp`, `protocol.cpp` | Init-owned socket controller, validation, leased sessions and cleanup. |
| `third_party/wireguard-tools` | Pinned, source-built GPL-2.0 tools named `hecker-wg` and `hecker-wg-quick`. |
| `rom/sepolicy/private/hecker_vpn.te` | Controller/daemon domains and fixed-helper transition into Android's existing netutils domain. |

The socket verifies owner-user UID and `SO_PEERSEC`. Binder calls require the
signature permission, owner user, HeckerSuite package identity and platform
signature. Settings gains no direct networking privilege. The native protocol
accepts only STATUS, KEYS, UP and DOWN: no caller-selected command, executable,
interface or path. Hooks and unsafe config tokens are rejected. Persistent
profiles and private keys are omitted from state snapshots, notifications and
dumps. The active helper config has mode 0600 and is removed on teardown.

Init supplies privilege; no `su`, KSU or Magisk grant is requested. Android's
existing netutils domain performs netd/DnsResolver operations. No platform
neverallow is relaxed. The controller owns only `hkvpn0`. A netid journal supports
cleanup after interrupted setup. Socket EOF, setup failure, controller death and
daemon restart enter cleanup. Network changes trigger reconnect; app policies
are resolved again, with explicit errors when selected packages are missing.

## Compatibility inspection

Pinned Floral kernel `5303303fd0138a719c0077570975fdce6ace661f` explicitly has
`CONFIG_WIREGUARD=y` in `arch/arm64/configs/floral_defconfig`, a sourced
`net/wireguard/Kconfig`, and `obj-$(CONFIG_WIREGUARD) += wireguard/` in `net/Makefile`.
Runtime support and successful setup are still checked. Source presence is not
a booted-device result. KSU is not a prerequisite of this integration.

Provider protocol reference: [Mullvad's published WireGuard tooling](https://github.com/mullvad/wg-tools/blob/main/wg-mullvad.py).
Tunnel tools: [WireGuard/wireguard-tools](https://github.com/WireGuard/wireguard-tools)
at `a998407747005ea7e4e0258d96f105c97241e1d3`. The dependency includes its
original file hashes, license, and a record of local adaptations.

## Initial scope

- Main Android user and one HeckerVPN tunnel. Apps sharing a UID share their route.
  Work profiles, secondary users, isolated-process coverage and tethering are not advertised.
- A default framework-managed Android VPN must be disconnected before connecting.
- Starts disconnected after reboot. No auto-start, kill switch or Android lockdown
  integration is exposed. Disconnect/reconnect can restore normal direct routing.
- Kernel backend only; no silent fallback, QR/archive import, editor or multi-hop.
- Imported DNS must use numeric IPs. Hooks, `Table`, `SaveConfig`, custom fwmarks,
  and nonstandard protocol extensions are rejected explicitly.
- Mullvad is the first optional adapter. Provider API/access/device limits can
  affect it; manual imports remain independent.

## Validation and release acceptance

```bash
python3 tools/check_vpn.py --sdk-cache generated/sdk
python3 tools/check_vpn_policy.py
python3 tools/compile_sdk.py --cache generated/sdk
bash tests/run_host_tests.sh
```

Checks compile the actual generated AIDL, controller, UI, tile, resources, native
daemon and WireGuard tools; execute real key generation; test configuration and
authorization boundaries, app inclusion/exclusion and handshake freshness; verify
vendored hashes; and exercise provider HTTP fixtures for catalog filtering,
authentication, retry-safe registration, deletion, redirects and key privacy.
They do not emulate a phone kernel or prove a working VPN connection.

The app, native Settings dashboard and VPN-specific Android compilation passed,
as did the 188 existing Python host tests. Standalone policy smoke checks compile
the VPN rules with the pinned platform policy and enforce neverallows; they use
an enabled feature-flag type set and do not replace actual device build flags.
Live Mullvad API access from this
development environment returned an unavailable page. No real account or tunnel
was provisioned, and no performance or leak-prevention results are claimed.

Before release: build the full Coral ROM with RBE/default jobs, keep SELinux
enforcing, and test real handshake/traffic, IPv4/IPv6/DNS routing, app policies,
shared UIDs, Wi-Fi/mobile changes, peer outage versus idle, disconnect, crash
recovery, reboot and system networking regressions. Repeat for every advertised
device/kernel variant and measure performance before making speed/battery claims.

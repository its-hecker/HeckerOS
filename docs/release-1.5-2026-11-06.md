# HeckerOS 1.5 — 6 November 2026

Build track: `fix/device-feedback-20261105` in both HeckerOS and
HeckerOs-localmanifest. Use the matching manifest's immutable Suite revision;
the build preflight and image revision properties identify the selected sources.

## Media and Quick Settings

A tap on the media card opens the app that owns the displayed session. SystemUI's
native ActivityStarter dismisses the shade and handles keyguard/provisioning.
If the session omits an activity PendingIntent, its own launcher activity is
resolved in the same Android user. A stale session, missing app, non-activity
PendingIntent or user switch cannot launch another user's activity. Transport,
seek, output and overflow controls retain their own click handlers. The Motion
Island dialog dismisses after scheduling an app launch.

The previous full-QS player used `ElementWithValues`, which can promote its
recording outside the native QS host into a shared scene-transition overlay.
That copy could outlive the host's immediate alpha updates during a fast
full-QS → quick-QS → Home gesture. The player now renders locally in each QS
subtree, with immediate outer-shade teardown and draw/alpha guards. Native panel
motion remains; media no longer uses the shared geometry overlay. Quick-QS Hide
still affects only quick QS and resumes automatically on a new playback event.

## Launcher and Hecker Lab

- Remove the ineffective dock-bounce setting and its custom motion adapters;
  dock motion follows the native launcher timing. The saved obsolete preference
  is ignored, without resetting launcher data.
- Fit GameSpace foreground and monochrome art inside the adaptive safe zone,
  provide circular round and legacy fallback artwork, and bump the Suite APK to version code 150 /
  version name 1.5. Launcher icon databases can invalidate the old package-version
  rows during a dirty-flash upgrade without clearing home layouts.
- Remove the GameSpace early return that bypassed the selected icon pack.
  Hecker glass icons use a transparent controller glyph and the user's icon tint.
  The normal adaptive icon still follows the launcher's chosen mask; round-icon
  launchers receive a circle by default.
- Use the shared dashboard tile layout for Sensor Recovery and App Compatibility.
  Keep their existing device availability and destinations.
- Set `ro.heckeros.version=1.5`. About shows the version, maintainer/build type,
  device, Android and security patch, followed by community/source links. The
  original donation details and copy actions are available on expansion.

## HeckerVPN

Replace the Proton entry with [VPNBook's free WireGuard generator](https://www.vpnbook.com/freevpn/wireguard-vpn).
As checked on 6 November, it requires no account or payment; configurations are
for one device and expire after **seven days**, after which another free profile
can be generated. The provider records connection IP addresses and times; see
its [privacy policy](https://www.vpnbook.com/contact). Generation happens in the
user's browser; download/paste import stays in Hecker Lab. Existing saved profiles
are retained. No credentials or configurations are bundled.

A compiled-policy test reproduced a missing permission for `netutils_wrapper`
to connect to Android's DNS proxy. WireGuard's `getaddrinfo()` endpoint resolution
uses this socket separately from the DnsResolver Binder path. Add the narrow
`unix_socket_connect(netutils_wrapper, dnsproxyd, netd)` rule; both user and
userdebug policy checks enforce neverallows and now grant the required accesses.
This corrects a verified policy gap that can fail hostname-based profiles, but
does not establish that it was the only cause of the reported phone failure.

The native helper now records only a fixed stage (interface, peer, Android
network, address, DNS, routes, MTU or app routing). Failure messages identify that
stage; arbitrary helper output, endpoints, configuration contents and private
keys are not returned. Existing tunnel cleanup and retry behavior remain.

## Validation and remaining device checks

Passed locally:

- 194 Suite host tests and 86 integration tests, including 25 media-launch
  session/user/keyguard/fallback/error assertions against production Java.
- Locked Android SDK/AIDL/resource compilation for Suite and native Settings;
  SystemUI Compose compilation against the pinned real ActivityStarter interface.
- 138 pinned native inputs: fresh apply, previous prepared-source upgrade,
  idempotence, exact revert, native launcher compile and framework bridge checks.
- Native VPN/controller/tool compilation and protocol/setup-message checks;
  compiled user/userdebug SELinux policies with neverallows and DNS access probes.

These checks do not replace a complete Android/Soong/kernel build or physical
Coral tests. Confirm the installed revision properties, media app taps, rapid
QS collapse/reopen, both launchers' cached icons after dirty flashing, icon-pack
switching, and a real WireGuard handshake plus routed traffic. Do not clear
launcher data as part of this upgrade.

The user reports banking apps, UBL and Wallet working on the prior matched
detector build. This release leaves the compatibility, attestation, framework,
Settings, kernel and KernelSU backend revisions unchanged. That phone report is
not a guarantee for other apps or a new hardware attestation claim.

The intermittent navigation-gesture freeze is **not confirmed fixed**. Its
Settings workaround is observed; frozen input/SystemUI/Recents diagnostics are
still needed before attributing or patching its cause.

# Coral feedback fixes — 6 November 2026

Use `fix/device-feedback-20261105` in HeckerOS and HeckerOs-localmanifest.
The active local manifest pins Suite, framework and Settings by commit. Before
preparation, the build helper refuses mismatched heads and edited Suite sources.
The generated system_ext build.prop includes `ro.heckeros.suite_revision` and
`ro.heckeros.manifest_revision`; the final output check rejects stale stamps.
Read them on the phone with `adb shell getprop ro.heckeros.suite_revision` and
`adb shell getprop ro.heckeros.manifest_revision`.

## Corrected paths

* WireGuard: the real controller label is `u:r:hecker_vpn_app:s0:c512,c768`,
  while the init daemon is `s0`. The old compiled-policy tests used only `s0`
  for the app, missing Android's MLS connectto constraint. Make only the
  authenticated native daemon an MLS trusted subject. Keep the app categorized,
  signature permission, TE socket restriction, owner UID and peer-context checks.
  Expanded binary-policy queries reproduce the old denial and pass the new
  user and userdebug policies with neverallows enforced. Transport failures now
  name the built-in service boundary without disclosing configurations or keys.
* VPN page: remove the paid Mullvad account/catalog UI. The Free VPN tab opens
  Proton's official configuration instructions and offers file/paste import.
  A Proton account and a downloaded configuration are still required; the
  provider is not automatically provisioned and no shared credentials ship.
  Existing saved WireGuard profiles remain usable.
* QS: feed the actual notification panel fraction to the media state and set
  its Android host's RenderNode alpha immediately. A stale inner QS scene callback
  cannot make the host visible after the outer panel is closed. Existing element
  teardown/draw guards remain. A compact vector overflow button and a rounded,
  dark menu replace the text glyph/default menu. Hide still affects quick QS only.
* Dock: clamping an oscillating interpolator to 0..1 did not remove reversals
  inside that range. Use a monotonic settle curve for both scale and translation
  when bounce is disabled, and retain native spring motion when it is enabled.
* GameSpace: render the supplied purple controller design directly in Ortus
  before glass extraction/themed bitmap reuse. Remove the opaque adaptive
  foreground and match its background color. Explicit per-app icon choices remain.
* Bluetooth connection card: show the window before applying optional blur;
  a blur failure retains an opaque, visible connection card.
* Add missing Russian/Chinese labels from the prior paste/width changes.

## Banking and attestation evidence

The supplied public certificate has `deviceLocked=false` and
`verifiedBootState=Unverified (2)` in the StrongBox-enforced root of trust.
The App Compatibility switch changes software properties, not this certificate;
its label now says Bootloader software properties. A checker showing Strong
Integrity does not establish Wallet or UBL compatibility.

The previous UBL evidence was package `app.com.brd`, version 3.52.2 (1660), with
an obfuscated Thread-2 NullPointerException. It does not identify a particular
root/ROM/bootloader probe as the cause. Neither banking app is claimed fixed.
`tools/collect-device-feedback.cmd`, run in Windows platform-tools, records the
installed revision, service labels/status and a new UBL crash/exit report. It
opens UBL without logging in and does not clear app data or device logs. Review
captured logs before sharing. No supplied certificates or VPN keys are committed.

## Validation boundary

Passed 194 Suite host tests, 86 integration tests, Android SDK Java/resource/Compose
compilation, binary SELinux policy for user/userdebug with neverallows, and
138 pinned native inputs with upgrade from the prior published Suite, idempotence
and exact revert. The matching local manifest passes 81 tests. The real panel
state regression covers stale inner-QS callbacks and native host alpha. These
checks do not substitute for a complete ServerHive build or the
physical tests: fast QS close/reopen, dock toggle after resume, GameSpace artwork,
Bluetooth with glass on/off, WireGuard handshake/traffic/DNS and the banking apps.

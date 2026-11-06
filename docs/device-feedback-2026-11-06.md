# Coral feedback fixes — 6 November 2026

Use `fix/device-feedback-20261105` in HeckerOS and HeckerOs-localmanifest.
The active local manifest pins Suite, framework, Settings and kernel by commit, with a KernelSU gitlink. Before
preparation, the build helper refuses mismatched heads, an outdated KernelSU submodule and edited Suite sources.
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
The software bootloader switch cannot alter that hardware state. Its selected app can now use the existing enabled TrickyStore backend with a valid imported keybox; it does not rewrite the supplied public certificate or lock the bootloader. A checker showing Strong
Integrity does not establish Wallet or UBL compatibility.

The previous UBL evidence was package `app.com.brd`, version 3.52.2 (1660), with
an obfuscated Thread-2 NullPointerException. It does not identify a particular
root/ROM/bootloader probe as the cause. Neither banking app is claimed fixed.
`tools/collect-device-feedback.cmd`, run in Windows platform-tools, records the
installed revision, service labels/status and a new UBL crash/exit report. It
opens UBL without logging in and does not clear app data or device logs. Review
captured logs before sharing. No supplied certificates or VPN keys are committed.

## Detector report follow-up

* Root privacy recognizes the official KernelSU Next signer through Android's
  APK-verified `SigningDetails`, including verified signing rotation. It covers
  randomized package names without trusting labels/library names. Added supported
  forks and Termux to the selected-app tool privacy catalog. Unknown signers do
  not become hidden merely by copying a label or package prefix.
* Debugging privacy now reaches libc property access/iteration, adbd directory
  listings and native socket-file views. The matched kernel carries a self-only
  per-app debugging bit and returns absent adbd stat / refused exact abstract
  JDWP connect probes. Other apps and real device debugging remain enabled.
* Native ROM views cover the reported LineageBlackTheme path, build flavor and
  partition fingerprint/type/tags, using the main device profile rather than
  invented SDK or patch levels. The existing filtered read-only CIL/context
  views remain; raw syscall policy reads and native symbol inspection are limits.
* The existing KSU SELinux-hide view now covers the app-zygote procattr path as
  well as selinuxfs. Keep original permission-denial ordering; bounded copies
  avoid reading beyond write size. Manager, root-granted apps and core UIDs keep
  actual policy access. Real enforcement and administrative policy stay intact.
* The primary-user per-app bootloader option now authorizes scoped keybox access
  through system_server. Explicit Skip takes priority. Selected ordinary
  attestation can recover from the reported vendor failure and specific unavailable
  KeyMint errors. Missing/disabled keyboxes, unrelated/input/auth errors and
  protected key requests remain native. No keybox is bundled.

The report's empty attestation chain is not evidence that certificate signatures
were checked and failed. Its detector commit is not the installed ROM revision.
The earlier public certificate and this separate failed request are different
observations. Neither proves the cause of UBL's crash or Wallet's rejection.

## Validation boundary

Passed 194 Suite host tests, 86 integration tests, Android SDK Java/resource/Compose
compilation, binary SELinux policy for user/userdebug with neverallows, and
138 pinned native inputs with upgrade from the prior published Suite, idempotence
and exact revert. The matching local manifest passes 81 tests. The real panel
state regression covers stale inner-QS callbacks and native host alpha. These
checks do not substitute for a complete ServerHive build or the
physical tests: fast QS close/reopen, dock toggle after resume, GameSpace artwork,
Bluetooth with glass on/off, WireGuard handshake/traffic/DNS and the banking apps.

The detector follow-up additionally passes the real Android 17 package/signing
and keystore API compile checks, 114 configuration/crypto checks, 16 PIF lifecycle
checks, 2658 app/installer assertions, ASan/UBSan kernel-helper checks, and ARM plus
ARM64 Bionic platform/APEX compiles at O2/Os with the 2 KiB frame budget enforced.
Fresh native apply, upgrade from the prior published Suite, repeat and exact
revert are checked. These results do not establish a complete kernel/Soong build
or a successful banking login, payment or physical detector rescan.

# VPN startup and fast QS close — 5 November 2026

Branch: `fix/device-feedback-20261105`, following source `5bbf87d`.

## VPN controller

The screenshot's unavailable-controller message has a reproducible policy defect:
`hecker_vpn_app` was an app domain but did not belong to `platform_app_all`. On the
pinned Android 17 policy, a query of the compiled binary denies even
`activity_service:service_manager find`. The controller needs ordinary platform
app service lookup before it can initialize and bind; compiling the policy and
checking neverallows alone did not verify this access.

Apply `platform_app_domain(hecker_vpn_app)` while retaining the separate UID,
signature permission, Binder caller checks and authenticated private daemon
socket. The new compiled-policy probe checks activity, package, connectivity
and notification lookup, Binder access, and that ordinary apps still cannot
connect to the daemon. Both user and userdebug policies retain enforced
neverallows. The Suite APK also explicitly requires the controller module.

Failed binds, timeouts, null bindings and controller death can now recover on a
later request without reopening the page. Connection failures retain known
account/profile metadata and show a retry action. They never replay a mutating
request automatically.

Mullvad input accepts formatted 16-digit numbers without truncating pasted
spaces/dashes. Setup errors keep the dialog open. A partially registered account
can retry using its saved identity/key; successful setup refreshes countries.
An already configured account also loads locations when reopening the page.

Configuration import treats display names as optional, retains document read
access when offered, and waits for the picker return lifecycle. It accepts
UTF-8 and BOM-marked UTF-16 text, keeps the 64 KiB limit and strict config
validation, reports read/parse errors separately, and keeps failed save dialogs
open for retry. Unsupported hooks/extensions remain rejected.

The ROM output checker now requires the controller APK, three executable tunnel
tools, the exact init script and default-permission XML. A partial installation
cannot pass the build helper's final checks.

## QS close

The previous guard lived in the Compose media/scene tree. The Android host view
only checked its broader `qsVisible` flag, which could remain true while an inner
QQS/full-QS transition settled. Feed the real outer `panelExpansionFraction`
into that view and invalidate it directly. Its `dispatchDraw` skips the complete
Compose subtree at zero/invalid expansion; `hideImmediately` closes it too.
Reopening uses the next real panel callbacks, with no delay or cleanup timer.
The shared QQS/full-QS media transition and quick-QS-only dismissal are retained.
Previously prepared source checkouts upgrade in place.

## Validation

- `python3 tools/check_vpn.py --sdk-cache generated/sdk`: generated AIDL,
  controller/UI/resource compilation, native tools and key generation, import
  formats/limits, provider fixtures and pinned platform contracts.
- `python3 tools/check_vpn_policy.py`: compiled-policy runtime access decisions
  and enforced neverallows, for user and userdebug. The new probe fails against
  the previous binary policy and passes after the fix.
- Production Binder client lifecycle regression: failed bind, reconnect, timeout,
  null binding, death, denied access, no duplicate mutation and picker closure.
- Pinned native QS callbacks and actual container `dispatchDraw` regression:
  zero/NaN expansion, 120 attempted stale draws, immediate hide, reopen and the
  shared-media transition. Native patches apply idempotently, upgrade from the
  previous adapters and revert exactly.
- Existing media geometry/session and customization regressions; installed-output
  checks including missing/stale controller runtime files.

These are source, compilation and host checks. No full ROM was built here and
no physical phone or real Mullvad account/tunnel was tested. Rebuild Coral from
the matching updated manifest, then verify real registration/import, handshake,
traffic, routing/DNS and fast full-QS → quick-QS → Home gestures with SELinux
enforcing.

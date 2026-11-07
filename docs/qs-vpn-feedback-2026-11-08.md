# Quick QS mobile label and VPN tile feedback — 8 November 2026

Base: published Suite `effd6421ec377093fd9ef93a799c407573c7b60f`, branch `fix/vault-vpn-review-20261107`.

## Mobile network label

The pinned Compose mobile icon drew the network-type drawable with `FillHeight` inside a height-only box. Give LTE/4G/5G labels an explicit width derived from the drawable's intrinsic aspect ratio and the existing font-scaled height. Fit the drawable inside that measured slot and clip the slot so it cannot draw into the adjacent signal bars. Keep native signal spacing, roaming/activity indicators, themed signal rendering and Row layout direction.

The adapter applies to the manifest-pinned Android 17 `MobileIcon.kt`. Fresh and already prepared signal-theme source both upgrade idempotently, and reverting restores the original source exactly.

## VPN Quick Settings switch

Previously a tap made a state request before sending connect/disconnect, painted only after the controller returned, and had no listener for later handshake changes. A configured tunnel waiting for a peer also looked switched off even though the next tap would disconnect it.

Paint the requested enabled state and Connecting/Disconnecting subtitle immediately. Submit one explicit, idempotent `set_enabled` request without a state preflight. Color represents enabled routing; the controller's subtitle distinguishes peer wait, peer timeout, Active, Idle and errors. No handshake, endpoint or transport behavior is changed.

Publish small state-change events to Suite only, with sender/receiver signature-permission checks. Monotonic state versions reject stale reads and broadcasts. Rapid taps retain the newest target; closing QS retains an accepted pending request until its completion. Hidden/destroyed tiles release their receiver and binding. Uncertain transport replies preserve known tunnel ownership, and failed cleanup still shows attention instead of claiming the tunnel is off. English, Russian and Chinese Disconnecting labels are included.

## ONIC comparison

The supplied cellular capture has the correct ONIC route and outgoing UDP attempts without a matching inbound reply during that interval. It does not identify which network/provider component caused the loss. Official WireGuard failed in both kernel and userspace modes.

The new WhiteVPN screenshots identify the successful connection as **HTTP outbound**, with TCP capability, the Public fallback subscription, VPN mode selected, LTE active, transferred bytes and Fragments off. The UDP badge describes the selected profile's capability; it is not proof that ONIC blocks all UDP. The screenshots do not establish whether that HTTP proxy's upstream connection uses TLS.

Supporting that working profile in HeckerVPN requires a compatible proxy transport/backend and server configuration. A matching backend alone does not grant access to another provider's private servers. This source change fixes the two UI issues; it does not add a Mihomo/HTTP backend or claim to fix ONIC WireGuard connectivity.

## Validation

- Production tile regression: immediate feedback before Binder replies; delayed initial reads; live handshake events; stale events; rapid opposite taps; pending work after QS closes; uncertain replies; cleanup failures; receiver/binding cleanup and accessibility descriptions. The regression fails against the previous tile and passes with this source.
- Existing Binder-client lifecycle and atomic customization adapter regressions.
- `python3 tools/check_vpn.py --sdk-cache generated/sdk`: controller, real AIDL, Suite page/tile, Android resources, provider/config tests and native tool/platform contracts.
- `python3 tools/check_mobile_icon.py --sdk-cache generated/sdk`: hash-locked native source, prepared-source upgrade/revert and patched native Kotlin compilation using real Compose APIs.
- Source and whitespace checks.

No complete ROM/Soong build or on-device visual/transition acceptance was performed here. After rebuilding, verify LTE/4G/5G with one/two SIMs and enlarged font/display settings in quick QS, then verify VPN enable/disable feedback, rapid taps, peer wait, handshake arrival and cleanup failure while QS stays open.

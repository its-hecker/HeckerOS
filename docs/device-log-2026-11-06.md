# Device log feedback — 6 November 2026

The `fix/device-log-20261106` branch includes the current HeckerVault feature
(saved groups and archive search included) plus fixes from the supplied device
feedback. It is based on Suite `1ac62cb6998a892c54e56ba28cb236723f6cb057`.
A matching Coral local-manifest branch selects the resulting Suite commit.

## Evidence and limits

Reviewed all 21,504 lines of `06_11-13-00-11_722.log` and all six accompanying
screenshots. The log spans approximately 177 seconds, with device activity
between 13:00:11 and 13:03:09. The raw log and screenshots are private evidence
and are not committed here.

| Area | Evidence | Interpretation |
| --- | --- | --- |
| Default GameSpace icon | Both normal drawer and normal home screenshots show a white disc; Hecker themed mode shows the controller. | The normal asset/cache path is faulty on this installed build; the themed path works. The log does not reveal the installed resource contents or prove which cached bitmap was used. |
| Launcher glass | 2,250 `Invalid resource ID` messages use IDs such as `0x00000006`, `0x00000011`. | The glass scanner calls `getResourceEntryName()` on every view ID. `generateViewId()` IDs have no resource-package byte and must be skipped. |
| Motion Sense | Three detected flicks: WEST twice and EAST once; two detected taps. Oslo sends Spotify next/previous commands and records pause by tap. | Sensor detection and native media command dispatch are functioning in this capture. |
| Creature | Companion surfaces are created immediately following the gesture events. No Motion Island exceptions are recorded. | The companion is being mounted for these events. A log cannot establish that its visual choreography looks smooth or natural. |
| VPN | `hkvpn0` is created, brought up and removed in two attempts. The screenshot reports 0 B received / 888 B sent and waiting for peer. | Interface setup succeeds; no successful peer exchange is shown. Neither the provider, endpoint reachability, current key registration nor carrier UDP behavior can be established from this capture. |
| VPNBook | Screenshot reports `DNS_PROBE_FINISHED_NXDOMAIN` for the `www` host. | This is a name-resolution failure, not a confirmed VPN tunnel connection. The provider's primary and official static mirror pages were retrievable during this review; reachability on the phone's network remains unverified. |
| QS/blur | 95 `BlurChoreographer: Multiple blur values emitted in the same frame` warnings. | Multiple blur producers are active. These warnings do not prove the cause of the visible player transition regression. The explicitly requested shared-media revert is applied. |
| Runtime stability | No `FATAL EXCEPTION`, `ANR in` or `avc: denied` entries in the supplied capture. | No such failures are captured; this is not proof that no crash, denial or hang occurs outside the capture. |
| Compatibility | Four `Software build fields unavailable` warnings; reflection cannot assign `Build.TYPE`. | This is a real separate warning, not a crash. The existing banking/Wallet implementation is retained because the user reports it working. |

## Changes

### QQS and full QS

Restore `HeckerMediaScene.kt` to its state immediately before `1315d8c` removed
the scene-shared media element. QQS and full QS again share the same element key
and interpolate square/row layout weights. Keep the existing app-opening fix and
Quick-QS-only dismissal behavior. Do not introduce another transition suppression
workaround in this revision. A physical fast-collapse test is still required;
this revert can expose the original platform overlay timing issue again.

### GameSpace and launcher

Use a fresh explicitly colored circular vector for both alias icon and round
icon. Increase the Suite package version code from 150 to 151 (ROM release name
remains 1.5), so package/icon caches can observe an update. The Hecker launcher
also resolves its bundled colored GameSpace fallback before applying the user's
chosen icon pack or glass artwork, preventing a retained old bitmap from being
the normal fallback. Pack selection continues to take precedence. No launcher
workspace database or user layout is deleted.

The glass layout scanner skips generated view IDs before resource-name lookup.
That eliminates the identified invalid-ID exception/log path rather than hiding
its log output. Prepared-source history records the previous copied adapters for
reversible source updates.

### HeckerVPN

- Add a session-only default `PersistentKeepalive=25` when the imported peer has
  no explicit value. This initiates peer contact even when only selected apps are
  routed and those apps have not generated traffic. Explicit `0` or other values
  remain unchanged, and the stored import is not rewritten.
- Poll first contact every three seconds; use monotonic elapsed time to report
  `No response from VPN server` after 45 seconds without any handshake.
- Provide a retry action that tears down and recreates the selected tunnel.
  A timeout leaves the tunnel and routes in place while WireGuard retries; it
  does not silently restore unprotected routing. Late peer contact recovers the
  normal state automatically.
- Log state changes and aggregate counters only. Do not log keys, account numbers,
  endpoint addresses, profile names, app selections or raw exception messages.
- Offer the official `https://static.vpnbook.com/freevpn/wireguard-vpn` mirror
  beside the original provider link. Preserve file/paste imports. Explain profile
  expiry and the provider's UDP-port troubleshooting; no WireGuard app is needed.

The screenshot's already-sent bytes mean idle-contact keepalive alone cannot be
claimed as the cause or cure of that specific failure. Invalid/expired profiles,
provider unavailability or blocked UDP still need on-device investigation.
References: [VPNBook generator and troubleshooting](https://static.vpnbook.com/freevpn/wireguard-vpn),
[WireGuard keepalive](https://www.wireguard.com/quickstart/#nat-and-firewall-traversal-persistence).

### Motion Sense and creatures

Retain the tested gesture/character lifetime logic. Add one acceptance diagnostic
per accepted gesture with numeric action and creature-enabled state. This makes
future captures distinguish sensor detection, command delivery and SystemUI
acceptance without recording media titles or session identifiers.

## Validation

Passed locally:

- 195 Suite tests and the complete host runner; 87 integration tests.
- Production media geometry: 72,072 continuous forward/reverse layouts.
- Suite Java/resource/AIDL compilation and independent native Settings dashboard.
- Quick Settings Kotlin/Java/Compose/resource compilation.
- Owned Launcher Java classes and resource linking.
- Motion Island/headphone/sandbox/face Java and resources against the system SDK.
- WireGuard native tools/daemon/protocol, real AIDL and controller/UI compilation;
  config tests cover default/explicit keepalive, profile immutability, app routing,
  timeout boundaries, clock changes and late-handshake recovery.
- HeckerVault's 41 archive/crypto and 50 native-transport assertions and complete
  controller Java/resources/permission/transport wiring checks.
- Exact vector-path render: purple circle, visible white controller and transparent
  corners. Linked manifest has package version 151 and both alias icon attributes
  resolve to the new colored drawable, not the monochrome layer.

The build manifest is validated after selecting the published Suite commit.
Full ServerHive ROM compilation, phone boot, both launcher icon paths,
QQS/full-QS animation and a real VPN handshake/traffic test remain necessary.
Swift Backup import remains deferred until physical HeckerVault acceptance
succeeds.

# 26 October device feedback: launcher, charging indicator, fetch and NetLab

The observed build has a search/dock collision, no visible One UI charging
animation, a generic invalid-chain error from public keybox fetch, and a rooted
Wi-Fi session rejected because IPv4 forwarding is enabled. Local keybox import
works. The maintainer no longer wants Motion Sense glow on ambient display.

## Source changes

- Launcher3 receives a search-slot height before sizing its workspace. Hotseat
  bottom padding reserves the actual 48 dp search pill, navigation offset and
  12 dp icon gap. The pill uses muted cyan/slate/lavender stops and a subtle
  border; Glass Studio continues sampling the frosted wallpaper behind it.
- Both legacy BatteryMeterView and Compose start/stop the battery drawable.
  The legacy path previously never started it. A soft shimmer complements the
  level-preserving liquid edge, including while the native charging flag remains
  true at 100%. Three optional right-side bolts are Classic, Split and Spark.
  Appearance switches update live. Hidden, off/dozing, detached, power-saving,
  disabled-animation and native fallback states stop the frame loop.
- Motion Sense remains enabled on normal display surfaces, with its existing
  sensor/plugin callbacks. Its feedback host is hidden while off/dozing, never
  requests an ambient pulse, and ignores ambient ticks. Charging/AOD scenes are
  independent and retain their existing behavior.
- Settings decodes downloaded certificate text to DER, verifies chain issuers,
  signatures, dates and Google trust, and treats unreadable revocation responses
  as Unknown after successful cryptographic validation. A missing public root
  must verify against an independently retrieved anchor. A revoked/suspended
  member takes priority over unknown revocation entries. Failure messages name
  the validation stage without including key/certificate payloads; replacements
  remain atomic and local imports are retained.
- NetLab installs unique filter-chain drops for selected peer addresses and
  Wi-Fi ingress/egress before ARP announcements. The userspace raw relay is not
  in the kernel FORWARD path. Verification catches missing/reordered/changed
  rules and ends the session. Partial setup rolls back. A bounded independent
  guardian removes owned rules on stop, expiry and relay death; failed removal
  is reported. No global forwarding/NAT/hotspot state is changed.

## Privilege answer

Network administration permission is authorization to use the feature. Linux
raw-packet and firewall operations independently require kernel privileges.
The current ROM controller uses a fixed `su` helper, so end-user root is still
required. Platform signing alone does not supply those capabilities. A separate,
authenticated native ROM service and its SELinux policy would be needed to remove
that requirement; this review does not add such a service or a generic root API.

## Verification and remaining device checks

Host, SDK/Compose, synthetic certificate and native adapter checks are recorded
in the review bundle. No full Android/Soong build or live-LAN control test was run
in this workspace. The current public provider's **certificate-only** chain was
checked with Android's XML parser; no private provider key was retained. A host
certificate result is neither an on-device fetch result nor an integrity verdict.

On Coral, verify dock/home-grid separation, rotation and search click; charging
animation and all bolt styles at low/mid/100%, unplug, saver, lockscreen and screen
off; no ambient glow and unchanged normal Motion Sense gestures; public fetch
and retained local keybox after a rejected replacement; selected/unselected IPv4
traffic, asymmetric rates, forwarding already enabled, hotspot coexistence,
normal/notification stop, expiry, relay death and restoration. IPv6 and direct
local traffic remain outside the connected-Wi-Fi relay.

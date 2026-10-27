# Pakistan IMS and status-bar indicators

This source change enables Android VoLTE/Wi-Fi Calling availability, initial
defaults and a dedicated IMS APN for Jazz (41001), Jazz/Warid (41007), Ufone
(41003), Zong (41004), and Telenor (41006). The SIM's home identity is used;
the visited country or currently registered network does not select the policy.

**Validation status: source/API checks only. Working VoLTE and VoWiFi on these
five SIM identities have not been established on HeckerOS or either phone.**

## Source integration

Both Floral builds use the Google CarrierSettings app and protobuf assets from
their vendor tree. Changing only AOSP CarrierConfig XML or displaying switches
would miss the selected configuration provider. `tools/pakistan_ims.py` updates
the matching records in the existing `others.pb` during Suite preparation. It
validates the SIM mappings from `carrier_list.pb`, keeps every unrelated carrier
record and existing APN byte-for-byte, and increments effective carrier/file
versions. Missing IMS APNs get a separate `ims`-only entry; internet and MMS APNs
are retained. Existing IMS APNs and their authentication/protocol are retained.

`ims/pakistan.json` is the reviewed, narrow policy. The added APNs are initial
test configurations, not carrier-verified modem profiles. Jazz uses IPv4 based
on published field reports; the other initial candidates use IPv4/IPv6. These
must be checked on the respective SIMs before release. Carrier provisioning,
entitlement, emergency routing and roaming policies are retained. Previously
saved user choices override initial defaults.

The normal preparation entry point includes these changes:

```bash
python3 vendor/hecker/floral/suite/tools/prepare.py --rom-root "$PWD" --check
python3 vendor/hecker/floral/suite/tools/prepare.py --rom-root "$PWD" --apply
python3 vendor/hecker/floral/suite/tools/pakistan_ims.py --rom-root "$PWD" --verify
```

The vendor checkout's pristine Git files are the baseline. Preparation refuses
unfamiliar local edits and is idempotent. The normal Suite `--revert` also
restores the pristine carrier asset. It does not erase persistent modem data.

## Modem support still to validate

The pinned stock Coral/Flame vendor MBN list has no Pakistan-specific profiles.
These changes do not claim to supply the missing carrier-compatible modem
configuration. No foreign MBN is forced globally and no modem NV/EFS data is
modified. If the installed generic profile cannot establish IMS, the next step
requires a compatible, tested profile for that operator and the Floral modem.
Placing an arbitrary MBN in a directory is insufficient to prove activation.

Profile activation and both services must be validated on the real phones.
A successful VoLTE call does not establish Wi-Fi Calling compatibility. Network
account provisioning also remains necessary; Ufone documents an additional
Wi-Fi Calling activation step on its service page. Google CarrierSettings may
also receive newer carrier data independently; recheck effective configuration
after any such update.

## Indicators

Hecker Suite > Status bar > Calling indicators has separate **Show VoLTE icon**
and **Show VoWiFi icon** switches, both initially enabled. Each active normal
SIM gets its own native SystemUI icon slot. Registration and MMTEL voice
capability must both be present. The registered bearer determines the icon;
being connected to Wi-Fi, having LTE data or enabling a toggle is insufficient.
Cross-SIM registration is not mislabeled as Wi-Fi calling. Valid Wi-Fi calling
can remain visible in airplane mode; old LTE evidence cannot. SIM removal,
service loss and deregistration clear indicators. Settings changes apply live.

## Device verification

Use a test build with these sources applied. Test every operator on each phone
that will be listed as supported. Confirm the carrier has activated both
services on the line. In SIM settings, review VoLTE and Wi-Fi Calling choices;
an existing saved Off value is intentionally retained.

1. With Wi-Fi off, inspect `*#*#4636#*#*` > Phone information > IMS service
   status. Confirm registration and voice over LTE availability, then verify
   incoming/outgoing calls, two-way audio, SMS and data during a call.
2. Enable Wi-Fi Calling, use Wi-Fi preferred for testing, and confirm real
   registration over IWLAN. Verify incoming/outgoing calls and two-way audio.
   If the carrier permits it, repeat with airplane mode on and Wi-Fi enabled.
3. Test Wi-Fi/cellular handover, reboot, SIM replacement and dual-SIM operation.
   Check that each status icon disappears when that SIM loses voice service.
4. Verify normal fallback and emergency-call configuration through an
   appropriate operator/lab procedure; do not place a real emergency test call.

Useful Windows ADB captures after a failed attempt:

```bat
adb shell getprop gsm.sim.operator.numeric > ims-sim-operators.txt
adb shell dumpsys activity service com.android.systemui/.SystemUIService HeckerImsIcons > ims-icons.txt
adb shell dumpsys carrier_config > ims-carrier-config.txt
adb shell dumpsys phone > ims-phone.txt
adb logcat -b radio -d > ims-radio.txt
```

Phone/radio dumps can include subscriber identifiers; review before sharing.
Record the carrier, SIM slot, phone, network mode, and whether the failure was
LTE registration, Wi-Fi registration, call setup or audio.

## References

- [Google CarrierSettings protobuf schema](https://android.googlesource.com/platform/tools/carrier_settings/+/refs/heads/master/proto/carrier_settings.proto)
- [Android IMS implementation](https://source.android.com/docs/core/connect/ims)
- [Android IMS entitlement](https://source.android.com/docs/core/connect/ims-service-entitlement)
- [JazzFi service](https://jazz.com.pk/jazzfi)
- [Zong Wi-Fi Calling](https://www.zong.com.pk/vas/vo-wifi)
- [Ufone Wi-Fi Calling and activation](https://www.ufone.com/ufone-vowifi/)
- [Pixel 4/4 XL MBN patch reference](https://github.com/hyx0329/VoLTE-Patch-for-Pixel4)
- [Thai Pixel 3/3a/4 profile integration reference](https://github.com/thongtech/voenabler-th-p3)

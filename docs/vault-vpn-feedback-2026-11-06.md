# Vault and VPN feedback, 6 November 2026

Based on the supplied Swift Backup UI references and the private
`06_11-20-07-15_416.txt` capture (38,521 lines). The raw capture and VPN
configuration are not included in the repository.

## HeckerVault

Replaced the default rectangular Android buttons and unstyled list with a
native dark dashboard matching the reference's colors, joined rounded cards,
compact icon toolbar, blue pills, app cards and bottom options sheet. Kept
HeckerVault branding and four local tabs. No cloud/account controls or Swift
Backup archive import were added.

Quick actions perform app-scope selection (all, pending, updated, existing),
verified-restore preselection (all, missing, newer), and separate message/call
backup and restore. History comes from completed or verified snapshots in the
selected folder; it is not a substitute for verifying an archive. Missing and
deleted archives invalidate the corresponding UI history. The version is 1.1,
with v1 archive compatibility.

The options sheet always includes APKs and offers native app data. External
data, OBB and media controls explain that those parts are not independently
supported; they cannot enable a fictitious backup path. Existing personal-data
categories, saved groups, schedules, retention and restore confirmation remain.

APKs no longer pass through default-level compression. They use a single-pass
DEFLATE level-zero entry, avoiding a precomputed size/CRC pass required by ZIP
STORED. Data defaults to BEST_SPEED, with Balanced/Smallest alternatives.
Increased the ZIP output buffer to 64 KiB and encrypted provider I/O buffering
to 256 KiB. Export now reports bytes, percentage and throughput. AES-GCM chunks,
310,000 PBKDF2 rounds, authentication, hashes and importer limits are unchanged.

The capture shows repeated failed attempts to load a missing
`LineageBlackTheme.apk` during comparator label lookups. Package enumeration now
sorts on package names; the UI loads each label and icon once on its worker,
sorts the cached labels and reuses row views. It does not repair stale overlay
state in the phone's package manager.

CPU-only host benchmark, 32 MiB incompressible APK-shaped bytes plus about
7.7 MiB repetitive app data, output counted without USB/provider latency:

| Exporter | Warm run 1 | Warm run 2 | Encrypted size |
| --- | ---: | ---: | ---: |
| Previous | 2514.9 ms | 1846.0 ms | 33,592,998 bytes |
| Fastest | 1064.5 ms | 1060.9 ms | 33,614,199 bytes |

This measures export CPU work, not Pixel 4 XL end-to-end performance. Native
backup-agent work, APK staging, password derivation and destination speed still
contribute to total time.

## VPN

The new capture shows `connecting` then `waiting_peer` at lines 7218/7284:
the interface is configured, tx=296, rx=0 and no successful peer contact. It
is disconnected at line 19082, approximately 37 seconds after setup. No VPN
crash or SELinux denial is present. The log does not prove that the provider
received the packets or that its current peer configuration is valid.

Resolve hostname endpoints using the selected physical Android Network before
installing VPN routing. Keep that numeric address in the connection copy only.
Pass the network ID through a separately validated numeric protocol field to
the fixed native helper. Mark encrypted UDP with the physical netId plus
AOSP's explicitlySelected/protectedFromVpn bits before peer setup/keepalive.
The old mark contained only protectedFromVpn and used implicit default routing.
The new code also notices a network switch during DNS and rechecks it while
polling. Numeric endpoints bypass DNS. Profiles and provider keys are unchanged.

The wire helper still runs through the existing netutils policy boundary.
No SELinux grant, shell hook or arbitrary executable was added. Protocol/argv
validation rejects invalid network IDs and command fragments. Added safe
15-second state/counter diagnostics without logging endpoints or keys.

This addresses underlay selection and DNS/setup ordering; it is not evidence of
a successful live handshake. Test with a current provider profile on the phone,
including Wi-Fi/mobile switching. The working free-provider website is retained.

## Motion Sense

The new capture records two WEST swipes (lines 3083/4104) and an EAST swipe
(5024), with corresponding Spotify next/previous commands (3085/4107/5026).
Island/creature windows appear. No tap gesture, Motion Sense crash, ANR or
SELinux denial occurs in this capture. Retained its current implementation.

## Validation

Production archive adversarial/round-trip/progress checks: 100. Native transport
checks: 50. Quick-action selection checks: 17. Full Vault Java and resources
compile against the locked Android platform SDK, with transport and privilege
wiring audits. Previous exporter → new importer and new exporter → previous
importer also round-trip successfully.

WireGuard config/provider/protocol checks, actual C/C++ helper/daemon builds,
Android controller/AIDL/UI compilation and the pinned AOSP Fwmark layout audit
pass. The complete host runner, including 195 Python tests and production native/Java suites, also passes. Full ROM/Soong build, physical backup/clean-flash/restore, UI comparison on
the phone and live VPN handshake/traffic acceptance remain pending.

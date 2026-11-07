# Vault and VPN device review — 7 November 2026

Base: `cf15976493367c00318010040c418450ccfdd7ce` on `fix/vault-vpn-storage-20261107`.
Implementation branch: `fix/vault-vpn-review-20261107`. The matching Coral local manifest uses the same branch name and pins the Suite revision.

## Changes

- Vault home now has an original looping, colourful vector Lottie, a gradient header, distinct category icons and coloured storage/action accents. Reduced-motion system preferences disable the loop.
- Verified snapshots expose creation time, device/API level, app/data/category counts, and Wi-Fi network count. Wi-Fi details read **the archived Wi-Fi payload**, not the current phone configuration. Each saved network shows its SSID, security and hidden-network state, with an independent password reveal control. Passwords hide on leaving Vault; the details dialog blocks screenshots and clears retained character arrays on dismissal. Passwords and SSIDs are not added to the dashboard index.
- The Wi-Fi detail parser accepts Android backup XML, including PSK and WEP configurations. It rejects DTDs/external entities, excessive nesting, oversized inputs and excess records. A newer detail format does not prevent Android from restoring the original Wi-Fi payload.
- Local app-data snapshots advertise encrypted device-transfer capabilities so app-defined local transfer rules apply instead of cloud-only exclusions. Each snapshot records this distinction. Archives without the new marker retain the old flags on restore. Explicit app backup opt-outs and Android signing/restore checks still apply.
- The MediaProvider adapter checks Vault at the private-directory lookup gate as well as the file/readdir bypass. Access is restricted to installed-package folders under the owner profile’s Android/data, Android/obb and Android/media, verified Vault UID, platform signature, BACKUP and ACCESS_MTP. Internal private data paths, arbitrary shared storage roots, other users, traversal and forged package claims remain excluded. ACCESS_MTP already supplies Android-writable mounts for this platform-signed app; this change also covers the separate FUSE checks.
- External snapshot metadata records saved file counts, empty folders, app policy exclusions and actual failures per selected folder. File attribute reads preserve access errors instead of treating them as absence. Empty Android/data or Android/obb folders are legitimate, including for many messaging apps.
- The native WireGuard socket mark now includes Android’s system permission bits in addition to the physical network ID, explicit-selection and VPN-protection bits. Kernel-created sockets do not receive these from the ordinary app socket path. The mark is installed before peer keepalives, and still pins encrypted transport to the selected physical network.
- VPN notifications show routing policy: all apps, selected app names, or all apps except selected names. The first selected/excluded app supplies the large icon; lists longer than three use a remainder count. Status remains visible in the title and lockscreen content is private.
- Routing selection opens immediately. App names preload off the main thread; the virtualised list loads and caches only visible icons, filters without re-enumerating packages and invalidates on package changes. Resource-only overlays are omitted.
- New user-facing strings have English, Russian and Simplified Chinese translations.

## Validation

Passed locally:

- `python3 tools/check_vault.py`: archive/encryption, external-folder round trips, saved Wi-Fi parser, native backup/restore transport, complete Vault Java compilation, Android resource linking, pinned platform permission checks and apply/idempotence/revert on the real MediaProvider source.
- `python3 -m unittest discover -s tests -p 'test_vault_*.py'`: credential migration/reset, transport handoff, private-folder caller/path boundaries and adapter integration.
- `python3 tools/check_vpn.py --sdk-cache generated/sdk`: native protocol, compiled WireGuard tools, physical-network mark boundary, generated AIDL, controller, routing picker, notifications, Android resources and pinned platform wiring.
- `python3 tools/check_source.py` and `git diff --check`.

This is source verification, not a full Android ROM build or physical phone acceptance. The supplied log shows successful Wi-Fi handshakes and zero received bytes on a validated Ufone LTE network. The revised socket mark fixes a client routing omission, but the log alone cannot prove whether the carrier/provider also drops that endpoint’s UDP traffic. No server port is silently changed: a different port requires a provider-issued profile that actually supports it.

## Rebuild Coral

The matching `HeckerOs-localmanifest` branch pins the Suite to the published source commit.

```bash
cd ~/heckeros-coral &&
python3 vendor/hecker/floral/suite/tools/prepare.py --rom-root "$PWD" --revert &&
repo init -u https://github.com/its-hecker/HeckerOS.git -b fix/vault-vpn-review-20261107 --git-lfs &&
git -C .repo/local_manifests fetch origin fix/vault-vpn-review-20261107 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c -j8 --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

## Phone acceptance

1. Make a fresh snapshot of a test app with actual internal data and populated external data/OBB/media folders. Inspect the saved counts. A failed folder must say failed, and a missing/empty folder must not be presented as saved.
2. Restore the test snapshot and inspect actual app state/files; also inspect an older archive to exercise its original transport flags. App exclusions and hardware-bound credentials may require app setup again.
3. Back up Wi-Fi, inspect the archive, open Saved Wi-Fi networks, reveal one password and leave Vault. Verify the password is hidden on return. Test an older Wi-Fi archive as well.
4. Connect the same valid VPN profile directly on LTE, then test Wi-Fi → LTE → Wi-Fi. Require a fresh handshake and increasing received traffic before calling LTE fixed. If there is still no peer response, compare with the official WireGuard client on the same SIM/profile and collect the new log; use a provider-issued alternative port if that endpoint is filtered.
5. Check all-app/include/exclude notifications, app icon and expanded multi-app text. Open routing before and after catalog preload, scroll, search and cancel, and verify the selection remains correct after changing packages.

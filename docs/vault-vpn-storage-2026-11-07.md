# HeckerVault 1.3 and HeckerVPN network recovery — 7 November 2026

This branch starts from `fix/device-feedback-20261107` / Suite `969bd280c26c2ed48b8420ce05144c7fa2096868`.
It carries the already working Motion Sense implementation. The new matching Coral
manifest retains its Motion Sense, OsloFeedback, device, kernel, framework and
Settings selections. No Motion Sense source was changed for this follow-up.

## Evidence and resulting changes

The supplied `07_11-19-12-05_034.txt` contains 27,976 lines. It is not published,
and no VPN configuration, private key or personal backup is included in this commit.

### VPN cleanup

At lines 21577–21592 the old network is already absent (`no such netId`) and the
controller reports `KernelChannel$Failure`. That cycle repeats. The screenshot
shows cleanup pending and zero transfer counters.

Android ndc reports operation failures with code **400**; **500** is a command
syntax error. The previous missing-network parser incorrectly accepted 500.
The helper now accepts 400 only for the expected network-destroy missing-object
errors. Permission, syntax and other errors still retain the ownership journal.
Normal ndc destruction clears both the network and its DNS cache. If the network
is already absent, the helper also retires its orphan resolver cache before
removing the journal. STATUS retries a pending cleanup, so reboot-era stale
ownership cannot permanently block a fresh connection.

### Wi-Fi and mobile underlay

Connections retain the physical Android network mark and resolve endpoints on
that network. Endpoint selection filters unavailable IP families. Numeric IPv4
profiles can use the carrier NAT64 prefix on an IPv6-only network, with bounded
CLAT/alternate-address retries when no handshake arrives. RFC6052 prefix lengths
32, 40, 48, 56, 64 and 96 are supported. At most two alternate addresses are tried;
a missing peer still becomes a visible timeout.

The default MTU is derived conservatively from the physical link; explicit profile
MTUs are retained. Changes to the active network's addresses, NAT64 prefix or MTU
trigger a fresh connection. Saved profile keys, hostname and preferences are not
rewritten. These changes do not establish that a current provider accepts traffic;
real Wi-Fi/mobile handshake and routed traffic remain device acceptance items.

### Native app data

At lines 10383–10399, Android skips package-manager metadata, then successfully
runs the WhatsApp full backup. Vault requires that metadata to call a standalone
snapshot restorable, so it discarded the otherwise completed native payload.
Each non-incremental request now explicitly includes **@pm@**, including a selection
containing only full-backup apps. Repeated snapshots get fresh metadata too.
The previous Android backup provider is recovered after success or failure.

App-agent exclusions and backup opt-outs still apply. Hardware-backed keys and
existing login sessions are not made portable. A previously exported APK-only
archive cannot gain app data retroactively: create a new snapshot with this build.

### External data, expansion files and media

The existing selected-package Android/data and Android/obb copier is retained,
including the native writable Android mount, no-link checks, signer/version
validation and opt-out behavior. Empty folders are reported as having no files.

The screenshot's Android/media `AccessDeniedException` is addressed in the
MediaProvider FUSE path. A guarded ROM-preparation adapter permits the exact
owner-user, platform-signed Vault UID to access installed package directories under
`/storage/emulated/0/Android/media`. It requires both BACKUP and ACCESS_MTP.
Other callers, other users, private /data paths, removable volumes, traversal and
non-installed package names do not receive this access. Vault continues to stage
only the packages/parts explicitly selected for an operation. No Internet,
MANAGE_EXTERNAL_STORAGE, system shared UID or private-app-file SELinux grant was
added. The adapter is applied by the normal Suite preparation step and reverted
with `tools/prepare.py --revert`.

### One-time setup, reset and dashboard

On first opening, Vault asks for one encryption password. A configured phone lock
is confirmed when entering Vault. Backup, archive verification and restore reuse
the Keystore-wrapped password without additional credential prompts during that
visit; ordinary restore confirmations remain. Locking the screen ends that visit.
Schedules share the same saved password and disabling scheduling does not erase it.
Older scheduler credentials migrate in place.

**Settings → Encryption & compression → Reset encryption password** confirms the
phone PIN/pattern/password and sets the password for future snapshots. Previous
passwords stay wrapped locally so earlier snapshots still open on this phone.
A wipe removes that local recovery state: older exported archives still require
their original passwords afterwards. A previously unknown archive password can be
entered once and is remembered only after full archive verification. There is a
32-password history limit with no automatic key eviction.

The Home tab now begins with used/free/total device storage, a usage bar, snapshot
count and encrypted archive size for the chosen folder. Dedicated saved Wi-Fi
backup and restore cards are visible on Home, in addition to the personal-data
category selector. New setup, reset, dashboard and Wi-Fi strings include Russian
and Simplified Chinese translations.

## Validation

- Vault production encryption/archive adversarial checks: 100 passed.
- Vault native transport checks: 50 passed; shared-folder checks: 24 passed;
  quick-action selection checks: 17 passed.
- Complete Vault Java/resources and exact permission/transport wiring compile
  against the pinned Android SDK. The media helper compiles against that SDK too.
- MediaProvider adapter applies, reapplies without changes and reverses against the
  actual pinned PixelOS MediaProvider source. Host tests reject hostile paths,
  forged package identities, wrong users/signatures and missing permissions.
- Production credential code is tested with real AES-GCM wrapping and an in-memory
  Keystore boundary: password rotation preserves decryption of an earlier archive,
  migration/import/deduplication work, corrupt envelopes fail and failed preference
  commits preserve the previous password. Device hardware Keystore remains untested.
- Production native requests are tested for repeated full-only snapshots and
  provider recovery on failure.
- WireGuard protocol, C/C++ helpers, controller/AIDL/UI/resources, endpoint
  family/NAT64 selection and pinned platform/policy-boundary audits pass.
- Suite Python regressions: 199 passed. Integration regressions: 87 passed.
  Source/resource boundaries and whitespace checks also pass.

Full ServerHive Soong/ROM compilation, first-run UI, hardware Keystore, physical
backup/clean-flash/restore and live VPN traffic have not run in this environment.
For the phone check, create a fresh snapshot containing a test app, existing
Android/media/OBB files and saved Wi-Fi. Verify the restore contents and counts.
Reset the password and reopen the old snapshot; backup and restore should not
request credentials after Vault entry. Verify received traffic, DNS and the selected
app-routing policy on Wi-Fi, mobile data, and while switching between them.

## Build on the existing Coral checkout

Use `fix/vault-vpn-storage-20261107` in both repositories. The matching Coral
local manifest pins the exact Suite commit for this change.

```bash
cd ~/heckeros-coral &&
python3 vendor/hecker/floral/suite/tools/prepare.py --rom-root "$PWD" --revert &&
repo init -u https://github.com/its-hecker/HeckerOS.git -b fix/vault-vpn-storage-20261107 --git-lfs &&
git -C .repo/local_manifests fetch origin fix/vault-vpn-storage-20261107 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

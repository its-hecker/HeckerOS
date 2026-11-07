# HeckerVault

Hecker Lab → HeckerVault. An original, offline Android 17 backup controller and
native BackupTransport, built and platform-signed with HeckerOS. No root grant,
Shizuku session or separate backup app is needed.

The local dashboard uses dark joined cards, blue pill actions, a searchable
app-card list, a backup-parts bottom sheet and Home / Apps / Archives / Settings
icon navigation. There are no cloud controls or accounts.

Apps quick actions select all, pending, updated or previously backed-up apps.
The private index is updated only after a completed export or verified archive
inspection, is scoped to the chosen folder, and is reconciled with its files.
After a clean flash, inspect copied snapshots to rebuild their app history.
Restore quick actions preselect all, missing or newer apps in the verified
preview; the preview still permits individual selection before restoration.

## Use

1. On first opening, confirm the phone lock if configured, then set one encryption
   password of at least 12 characters. The Home dashboard shows device storage,
   free space, snapshot count and the space used by the selected backup folder.
   Choose a folder in Documents or on USB and select apps and personal-data categories.
2. In the app parts sheet, select optional shared storage folders for this backup.
   Backup, archive inspection and restore reuse the saved password without another
   credential prompt during this visit. Wi-Fi backup/restore has dedicated Home actions.
   The result lists APK, native app data and shared storage outcomes for each app.
3. Copy the whole folder to PC/USB **before** formatting data. Keep the password
   separately. Internal storage is erased by a clean flash.
4. After flashing a HeckerVault-enabled ROM, copy the folder back and select it.
5. Use the original password during setup to reopen those snapshots automatically.
   An archive using another password can be unlocked once; that password is remembered
   only after complete verification. HeckerVault decrypts and verifies the archive in
   private staging before showing a restore preview. Choose apps, categories and
   APK + data / APK-only / data-only. Select any shared storage parts to restore.

Data-only requires an already installed matching-signer app at the backed-up
version or newer. The installer refuses signing mismatches and downgrades.
App data may replace selected apps' current data. Personal data is merged.
Android's previous backup provider is restored after the operation, if there
was an available previous provider. Interrupted handoffs are recovered on next
launch, boot or package update.

## Coverage

| Item | Behavior |
| --- | --- |
| APKs | Base APK and all installed splits; native PackageInstaller restores them |
| App data | Android full/key-value backup agents and native signing/ownership/permission handling |
| Apps refusing Android backup | APK and explicitly chosen expansion or app media where present. Native and external app data are skipped |
| External app data | Selected app's Android/data folder on internal shared storage, excluding cache and honoring the app's backup opt-out |
| Expansion files | Selected app's Android/obb folder on internal shared storage |
| App media | Selected app's Android/media folder on internal shared storage |
| Wi-Fi | Platform Wi-Fi backup/restore API; platform exclusions, including hardware credentials, apply |
| Contacts | Local on-device raw contacts, fields and photos; synced accounts resync separately |
| Messages | SMS and MMS including binary attachments; no RCS/third-party encrypted chat guarantee |
| Call history | Provider-backed call records; not voicemail recordings |
| Wallpapers | Static Home/lock images; live wallpaper packages/settings are not exported |
| Hecker settings | `hecker_` Secure/System settings; credential/key/secret settings excluded |
| Launcher | Hecker Launcher's native agent, only when it returns a successful data backup |
| App groups/search | Saved app + personal-data selections; app/package and archive-filename search |
| Scheduling | Daily/weekly, optional charging requirement, current selections or all user apps |
| Retention | Latest 1/3/5/10 scheduled snapshots; only files created by this installation are pruned |

Private app data follows Android's native backup rules. Version 1.3 retains optional
per-app shared storage folders through the platform-signed Vault's writable
Android storage mount. Those parts are selected per manual backup. Restore checks
the installed package, signer and version, then replaces matching files one at a
time, preserving files absent from the snapshot. The encrypted format stays at
version 1. Earlier Vault builds ignore the optional shared storage parts.
Cache, notification channels, SSAID, hardware Keystore keys and device-bound
sessions remain outside the portable snapshot.
The controller supports the owner profile. Swift Backup import is intentionally
deferred until physical HeckerVault backup/clean-flash/restore acceptance succeeds.

## Storage and encryption

A snapshot is one `.hvault` file. Plaintext exists only in the controller's private,
credential-encrypted staging directory during work; the public chosen folder gets
only the encrypted archive. Enough free internal storage is required for staging.
USB/file systems must support the resulting file size.

Fastest compression is the default. Balanced and Smallest size are available in
App backups → Encryption & compression. APKs are already ZIP containers, so all
three modes stream them with DEFLATE level zero instead of recompressing them.
Other payloads use the selected compression level. Export/import buffers are
256 KiB, and export shows plaintext bytes processed, percentage and speed.
These options do not change v1 encryption, password derivation or integrity
checks. Old and new v1 archives remain readable by either implementation.

The format begins with `HVLT` and version 1, followed by a 16-byte random PBKDF2
salt, an 8-byte random nonce prefix, the fixed 310000 SHA-256 derivation count and
256 KiB chunk size. PBKDF2-HMAC-SHA256 derives a 256-bit AES key from the password.
Each frame contains a plaintext length and AES-256-GCM ciphertext/tag. The nonce
is the prefix plus a strictly increasing 32-bit counter; header, counter and
length are AAD. A final authenticated zero-length frame and mandatory EOF reject
truncation, extra data and frame substitution/reordering. A ZIP inside the frames
contains `snapshot.json`, APKs, native payloads, selected personal data and a
SHA-256/length index for every payload.

Importer limits: 25000 payload files, 128 GiB total, available-space reserve,
4 MiB metadata/index, bounded personal-data rows/records, canonical paths and no
links or duplicate paths. Inspection validates archive authentication, every file
hash, package metadata, base-APK signer/version, native package-manager metadata
and selected system-data schemas before a restore can start.

The encryption password is always wrapped using Android Keystore in private app
storage and is available after device unlock. Schedules reuse the same password;
disabling a schedule does not delete it. Previous scheduler-only credentials migrate
in place. No password is sent through Intent extras or saved UI state.

Settings → Encryption & compression → Reset encryption password confirms the phone
PIN, pattern or password. New snapshots use the replacement password. Earlier
passwords remain wrapped locally so older snapshots still open on this phone. The
local key history is lost on a wipe: an older exported snapshot still needs its
original password afterwards. A screen lock cannot recover keys erased by a clean
flash. Up to 32 prior/imported passwords can be remembered; this limit never evicts
an old key automatically.

## Build and tests

`HeckerVault` is selected by `suite.mk`, alongside the existing Suite. Its
system_ext permission/default-permission/sysconfig modules are required by Soong.
The Hecker Lab tile opens the signature-protected controller; only the system
backup service can bind its transport. The ROM allowlists the existing platform
Android storage permission for the Vault process. A guarded preparation adapter also
allows this exact, platform-signed owner-user process to access installed packages'
Android/media folders through MediaProvider FUSE. Package UID, platform signature,
BACKUP and ACCESS_MTP are all checked. It does not grant private /data access,
MANAGE_EXTERNAL_STORAGE, a shared system UID or Internet permission. Folder
operations are confined to selected packages and parts on the primary shared volume.
Each non-incremental native snapshot explicitly requests @pm@ package metadata;
without it, otherwise successful full backups are not independently restorable.

```bash
python3 tools/check_vault.py --sdk-cache generated/sdk
```

This runs production archive/transport tests, complete locked Android SDK/resource
compilation, exact platform source/API and permission/transport wiring audits.
A full Android/Soong build and physical device acceptance are separate requirements.

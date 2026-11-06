# HeckerVault

Hecker Lab → HeckerVault. An original, offline Android 17 backup controller and
native BackupTransport, built and platform-signed with HeckerOS. No root grant,
Shizuku session or separate backup app is needed.

## Use

1. Choose a folder in Documents or on USB. Select apps and personal-data categories.
2. Back up with a password of at least 12 characters. Read the result: each app
   says whether its archive includes data or only APKs.
3. Copy the whole folder to PC/USB **before** formatting data. Keep the password
   separately. Internal storage is erased by a clean flash.
4. After flashing a HeckerVault-enabled ROM, copy the folder back and select it.
5. Inspect an archive with its password. HeckerVault decrypts and verifies it in
   private staging before showing a restore preview. Choose apps, categories and
   APK + data / APK-only / data-only, then restore.

Data-only requires an already installed matching-signer app at the backed-up
version or newer. The installer refuses signing mismatches and downgrades.
App data may replace selected apps' current data. Personal data is merged.
Android's previous backup provider is restored after the operation, if there
was an available previous provider. Interrupted handoffs are recovered on next
launch, boot or package update.

## Coverage

| Item | Version 1 behavior |
| --- | --- |
| APKs | Base APK and all installed splits; native PackageInstaller restores them |
| App data | Android full/key-value backup agents and native signing/ownership/permission handling |
| Apps refusing Android backup | APK-only, explicitly shown in archive results; no sandbox-policy bypass |
| Wi-Fi | Platform Wi-Fi backup/restore API; platform exclusions, including hardware credentials, apply |
| Contacts | Local on-device raw contacts, fields and photos; synced accounts resync separately |
| Messages | SMS and MMS including binary attachments; no RCS/third-party encrypted chat guarantee |
| Call history | Provider-backed call records; not voicemail recordings |
| Wallpapers | Static Home/lock images; live wallpaper packages/settings are not exported |
| Hecker settings | `hecker_` Secure/System settings; credential/key/secret settings excluded |
| Launcher | Hecker Launcher's native agent, only when it returns a successful data backup |
| Scheduling | Daily/weekly, optional charging requirement, current selections or all user apps |
| Retention | Latest 1/3/5/10 scheduled snapshots; only files created by this installation are pruned |

This is a native Android backup implementation, not unrestricted raw app-directory
copying. App-declared extraction rules still apply. External Android/data/OBB,
cache, notification channels and SSAID are not independently cloned in v1.
Hardware Keystore keys and session/device-bound logins do not become portable.
The controller supports the owner profile. Swift Backup import is intentionally
deferred until physical HeckerVault backup/clean-flash/restore acceptance succeeds.

## Storage and encryption

A snapshot is one `.hvault` file. Plaintext exists only in the controller's private,
credential-encrypted staging directory during work; the public chosen folder gets
only the encrypted archive. Enough free internal storage is required for staging.
USB/file systems must support the resulting file size.

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

For an explicitly enabled schedule, a separate Android Keystore key wraps the
password in private app storage, usable after unlock. This local wrapping is not
needed to recover an exported archive and is lost on a wipe. Forgetting the
original password still makes the archive unrecoverable.

## Build and tests

`HeckerVault` is selected by `suite.mk`, alongside the existing Suite. Its
system_ext permission/default-permission/sysconfig modules are required by Soong.
The Hecker Lab tile opens the signature-protected controller; only the system
backup service can bind its transport. There is no Internet or blanket storage
permission, shared system UID or private-app-file SELinux grant.

```bash
python3 tools/check_vault.py --sdk-cache generated/sdk
```

This runs production archive/transport tests, complete locked Android SDK/resource
compilation, exact platform source/API and permission/transport wiring audits.
A full Android/Soong build and physical device acceptance are separate requirements.

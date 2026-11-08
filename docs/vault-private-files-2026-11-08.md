# Private app-file backup — 8 November 2026

The latest device screenshots confirm the shared-folder fix: Plus saved 669
Android/data files and 3 Android/media files. Android/obb was empty. Its custom
Android backup agent still returned no private data, so that archive correctly
remained partial. Repeating the same agent request cannot capture the missing
databases/preferences.

Vault 1.4 adds an independent private-file backend for selected user apps and
Hecker Launcher. Selecting App data saves private CE/DE files even for backup
opt-outs or agents returning zero bytes. Other protected/system/shared-UID apps
continue to use Android backup agents where supported. Failure to capture the
private files remains an attention/partial result even if an agent fallback
succeeds. APKs and successful shared-folder parts remain restorable.

## Implementation

- A small Binder service in the package-manager package is registered by
  system_server. It requires BACKUP, the exact owner-user Vault UID, a system APK
  and platform signing identity. Callers can supply only package, session UUID,
  signer digests and version, not privileged paths. Target eligibility is checked
  before stopping anything and again under the install lock.
- PackageFreezer stops the selected app for the native operation. A callback or
  installd death releases the asynchronous wait; controller cancellation cannot
  unfreeze an app while its native copy is still running.
- Android 17 already exposes installd.copyAppDataPath to SYSTEM_UID. Guarded
  source adapters reuse its pinned-path traversal and recursive UID verification.
  Private capture excludes top-level cache, code_cache and managed library links,
  normalizes portable permissions, preserves mtimes and syncs copied data.
  Existing callers keep their original flag behavior. No Binder ABI is added.
- Private-file payloads use a bounded binary UTF-8 index and numbered blobs.
  Spaces/Unicode names, SQLite WAL files and empty directories are supported.
  Existing encrypted archive authentication and per-payload hashes cover both
  index and blobs. Traversal, links, duplicate/undeclared payloads and truncated
  indices are rejected before private restore starts.
- Restore fully stages CE/DE replacements first. Old entries are retained in
  fd-relative journaled rollback folders, while the app root inode and managed
  lib link stay in place. Atomic journal publication avoids truncating an active
  recovery record. Rollback is repeatable even if a previous rollback stopped
  after putting some original entries back. Target ownership and SELinux labels
  are applied by installd.
- No private app-data access is granted to the Vault process or a new daemon.
  The only new SELinux rule exposes the checked system service to platform apps.
  Runtime UID/signature checks still reject other platform callers.

## Validation

- `tools/check_vault.py`: complete controller Java/resource compilation,
  production encryption/archive/transport checks, private-file encrypted round
  trip, Unicode/spaces, database/WAL, modes/mtime, empty directories and hostile
  index/blob handling.
- `tools/check_vault_files.py`: pinned real Android 17 source/API checks,
  integration apply/idempotence/revert and upstream guard refusal. Compiles and
  runs the actual patched native verifier/copier plus journal transaction code
  with host filesystem boundaries. Tests source ownership, transient exclusion,
  no-follow links, interrupted multi-root replacement, interrupted rollback,
  committed cleanup and journal symlink refusal. The production broker is
  compiled/run against simulated platform boundaries to check caller/target,
  signers/version, recovery and asynchronous freezer lifetime.
- Existing Vault credentials, MediaProvider scope, native metadata transport and
  preparation regression tests pass.
- Pinned standalone platform SELinux compilation passes for user and userdebug
  with neverallows enabled, including allowed Vault service discovery and
  rejected untrusted-app discovery.

## Device acceptance still required

This is source/host validation, not a completed Android ROM build or physical
restore test. Flash the matching build and create a fresh Plus backup; an older
partial archive has no missing private data to recover. The result should show
private-file counts, rather than the empty-agent APK-only outcome.

On a spare profile/device, restore the fresh snapshot and check Plus account
state, local settings and messages, plus APK-only/data-only modes. Test installed
signer/version rejection and cancellation. Keep an independent copy of current
data while validating replacement behavior. Native journals recover before the
next private operation and after reported failures when installd is available;
power-loss recovery before an app is used is not guaranteed. Hardware Keystore
keys, device-bound sessions, identifiers, notification channels, caches and
symlinks/sockets remain outside portable coverage, so some apps require login.

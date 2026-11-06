# HeckerVault v1 — 6 November 2026

Feature branch: `feat/heckervault-20261106`, based on the published 1.5 feedback
source. The matching Coral manifest selects this feature's Suite revision; the
framework/Settings/kernel/KernelSU compatibility pins remain the 1.5 selection.

Hecker Lab gains HeckerVault, with Overview, Apps, Archives and Settings. The
separate platform-signed controller uses Android's native full/key-value backup
transport, APK/split install sessions, provider-based personal data and a portable
password-encrypted archive. There is no `su`, Shizuku, Swift dependency, cloud
account or server. Full format, coverage and user instructions: [vault/README](../vault/README.md).

Native transport state is scoped to the explicit foreground operation. Existing
backup providers are handed back after completion/cancellation. Recovery state is
persisted before selection; delayed registration, process death and boot recovery
retain the previous provider information. An inactive transport refuses payloads.
Only selected owner-profile packages plus native `@pm@` signing metadata are
accepted. Android remains responsible for agents, restore eligibility, signing,
UID ownership and file labels.

Archive inspection happens in private staging and verifies the complete GCM
stream, final frame/EOF, ZIP paths/index, APK signer/version metadata, native
metadata and personal-data schemas before restore. Restore previews expire after
30 minutes. Permission failures, app backup opt-outs, install failures and partial
native results are reported. Different app signers and downgrades are rejected.

Contacts use local raw-contact records and per-snapshot provider markers. SMS and
call records use merge/dedup queries. MMS uses native provider rows/attachments,
message identifiers and a private SQLite receipt journal. Queued/outbox SMS/MMS
become drafts to avoid sending old messages. The temporary SMS write AppOp is
restored, with recovery following process death.

Saved app groups keep named app/personal-data selections for reuse. Groups travel
in encrypted snapshot metadata and merge when Hecker settings are restored.
Apps can be searched by label/package; archives can be searched by filename.

Scheduled backups are opt-in. Daily/weekly charging-aware JobScheduler jobs use
a device-wrapped password after unlock. Their portable archives still use the
user's password; a factory reset does not require the old device Keystore.
Retention deletes only older files known to have been created by this installation.

## Validation

- Production encryption/archive adversarial checks: 41 assertions, including
  round trip, randomized salts/nonces, wrong password, corruption, truncation,
  missing final frame, extra tail, reordered chunks, traversal/links, space limits
  and cancellation.
- Production native-transport protocol checks: 50 assertions, including idle and
  unselected rejection, non-incremental key-value backup, full stream capture,
  atomic finish/cancel, restore tokens, selection and chunked round trip.
- Complete controller Java compilation and Android resource linking against
  checksum-locked system SDK; real BackupManager/Transport/RestoreSession APIs.
- 195 Suite Python regressions plus the complete host runner, 87 integration tests and
  82 manifest tests pass. Full Suite/native Settings SDK, AIDL and resource compilation passes.
- Android 17 platform permission audit and exact Soong partition, native transport
  allowlist and exported-component permission checks.

These are host/source checks, not a device-tested backup guarantee. The full
ServerHive ROM build and physical acceptance below remain pending.

## Device acceptance before Swift work

Use a secondary test app with known files/preferences, plus a test split APK.
Take an encrypted backup containing these apps and representative Wi-Fi, local
contacts, SMS/MMS attachments, call records and static wallpapers. Verify the
snapshot result distinguishes data and APK-only cases, then copy it off-device.
Restore first into disposable test state and verify app content, permissions,
notifications, personal-data counts/attachments, launcher state and the original
Android backup provider. Check cancellation, wrong password, unavailable USB and
schedule-after-unlock behavior. Only after an off-device archive has been checked
should a clean-flash restore be tested on a device whose data is backed up safely.

No physical clean-flash/restore success is claimed. Swift import remains deferred.
No unrestricted raw app-data or hardware-key portability is claimed.

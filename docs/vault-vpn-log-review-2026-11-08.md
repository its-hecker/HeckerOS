# Vault and mobile VPN log review — 8 November 2026

Source baseline: Suite `4da3081a8faf97c7f9a0bb4000c25923b5d542ed` on `fix/vault-vpn-review-20261107`.

## App-data result

The device run completed the package-manager metadata backup, then invoked Plus Messenger’s custom Android key/value backup agent. Android reported success with **zero bytes** for that package. The standalone app payload was absent. External Android/data capture separately saved 1,328 files. Android/obb was empty; an empty OBB folder is not an error.

The pinned KeyValueBackupTask supplies a blank agent state for a non-incremental request, so the zero-byte result is not explained by reusing an incremental state file. Device-transfer capabilities do not turn an app’s custom key/value agent into a full private-files copier. The current native backend cannot promise a complete private-data/account backup for this app. A separate full-file backup backend is needed for that capability; this follow-up does not implement one.

Vault now distinguishes an empty successful agent response from a native pass failure, missing metadata and app backup opt-out. A saved snapshot is marked partial if selected data was unavailable, rejected or failed. The Home result, archive index and preview show that status. A partial scheduled snapshot does not apply retention rotation to older archives. Successful selected parts remain restorable.

## Media access correction

The log shows MediaProvider checking READ_IMAGES and denying a package-media image even though Vault has BACKUP and ACCESS_MTP. Inspection found that our adapter used ContentProvider.getCallingPackage(), which reads Binder attribution. Filesystem/FUSE callbacks use MediaProvider’s thread-local LocalCallingIdentity instead. clearLocalCallingIdentity updates that thread-local state without setting ContentProvider Binder attribution, so the trusted caller check could receive null and refuse Vault.

Both adapter checks now use mCallingIdentity.get().getPackageName(), paired with the same FUSE UID. Package-folder scope, installed-owner check, exact Vault UID, platform signature and both permissions are retained. No broad storage permission or SELinux relaxation is added.

The regression reproduces a trusted FUSE request with null/unrelated Binder attribution and rejects a forged Binder package with an untrusted FUSE identity. It failed with the old adapter and passes with the corrected one. Physical media backup/restore still requires device acceptance after rebuilding.

## Mobile VPN finding

The supplied mark is 0xf0071: physical network 113 plus explicit-selection, VPN-protection and system-permission bits. The bugreport has the corresponding rule selecting the active cellular interface and a valid IPv4 default route. The WireGuard-marked OUTPUT accept rule counted outbound packets. The updated native mark is installed; another speculative mark change is not justified by this evidence.

Wi-Fi obtains a handshake. Mobile attempts time out without a completed handshake. The last attempt has increasing transmitted bytes and zero received bytes; an earlier failed attempt received limited bytes without a completed handshake. This does not establish successful tunnelling, and does not by itself prove whether the cause is carrier filtering, the provider endpoint, or a remaining kernel transport problem.

The required comparison is the **same profile, same SIM and mobile network** in the official WireGuard app with HeckerVPN disconnected. If that also fails, investigate the endpoint/provider/network path and try only a provider-issued alternative endpoint/port. If it succeeds, compare the official client transport and HeckerVPN before changing the ROM. No VPN transport fix is claimed in this follow-up.

## Validation and rebuild

- FUSE caller-identity regression: demonstrated red before the correction and green afterwards.
- Existing caller/path/signature/permission, adapter apply/idempotence/revert and transport/credential regressions pass.
- App-data outcome tests cover successful zero-byte agents, actual payloads, missing metadata, opt-outs and native/package failure codes.
- Complete Vault Java/resources and archive/transport tests run through tools/check_vault.py.
- Source checks and git diff --check run before publication.

Full Android/Soong build and physical backup/restore are not performed here. The matching Coral manifest on `fix/vault-vpn-review-20261107` pins the published Suite follow-up. Use the existing rebuild command in [the November 7 review](vault-vpn-review-2026-11-07.md#rebuild-coral), including reverting the old Suite adapters before syncing.

Raw device logs, bugreport contents, private file names, VPN configurations and account data are not included in the source change.

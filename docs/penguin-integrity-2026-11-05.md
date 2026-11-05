# Penguin integrity integration — 5 November 2026

Use fix/device-feedback-20261105 in HeckerOS and HeckerOs-localmanifest.
The matching manifest selects these published native sources together:

| Project | Revision | Branch |
| --- | --- | --- |
| frameworks/base | db2d4435d1d2cce17d35f6fdc0efb303becfc2c5 | feat/penguin-integrity-20261105 |
| Settings | 1cc575774ca85cf5ca5a8cf6a164a4583238c9c5 | feat/penguin-integrity-20261105 |

## What changes

HeckerLab now opens one Play Integrity page for PIF, TrickyStore and app/game
profiles. The former private Spoofing hub component redirects to this page;
saved profiles, enable switches, targets and private keys retain their storage.
Photos has an independent compatibility switch. Non-secret PIF, target and game
profiles can be exported through Android's document picker, including after an
activity recreation. Keybox export is not added.

Penguin celerity source is pinned at
947644f88d63b82179deec4d9149a1e4332584a3. Its TrickyStore atomic configuration,
transport-failure handling, consolidated GameProps, PIF parser/Photos behavior,
and ROM package visibility rules are reconciled with the existing Hecker private
store, numeric Play Store options, JNI property bridge and scoped keystore calls.
The new startup path works before Application context exists, and transient
settings failures retry while explicit disabled/empty configuration stays off.

TrickyStore includes strict calendar-date validation and resets cached targets,
patches and keys across a process UID change. GameProps rejects malformed JSON
without retaining partial rules; its JSON profiles retain priority over named
per-app profiles. No keys or private certificates are bundled.

ROM package visibility is automatic, preserving self access, trusted system and
privileged callers, shared UID allowances, and each user's default launcher.
Root-manager privacy remains an independent per-app control. The old custom-ROM
selection setting is no longer read or observed. PropImitationHooks and its
callers are removed; task permission checks are unconditional. No Obscura
implementation exists in the selected framework.

## Validation

Android 17 Java/keystore type checks and owned Settings Java/resource compilation
pass, together with 96 configuration/access/parser/game/synthetic RSA/EC checks,
16 PIF lifecycle checks, 88 Settings import/certificate checks, 30 imported
visibility checks and the existing 2,590 installer/settings policy assertions.
The full Suite host suite passes, including 192 Python tests; the Suite integration
and Coral manifest suites also pass 84 and 79 tests respectively. All 138 pinned
native inputs apply, repeat and revert exactly across 364 adapters/copies. Native property
checks pass under AddressSanitizer/UBSan with leak detection disabled because
this executor does not support LeakSanitizer. No host check establishes any
Google attestation verdict. Full ROM/Soong compilation, boot and physical Coral
acceptance remain required.

## Existing ServerHive checkout

Run the old Suite's guarded source revert BEFORE updating manifests. Preserve
out/ for an incremental build. Unfamiliar edits stop the process for review;
do not use force-sync or reset to discard them.

```bash
cd ~/heckeros-coral &&
python3 vendor/hecker/floral/suite/tools/prepare.py --rom-root "$PWD" --revert &&
repo init -u https://github.com/its-hecker/HeckerOS.git -b fix/device-feedback-20261105 --git-lfs &&
git -C .repo/local_manifests fetch origin fix/device-feedback-20261105 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

Use the README's fresh-checkout block when starting from mkdir. The build helper
uses Android's default job count with RBE; there is no forced -j8.

After flashing, verify Settings imports/exports and rotation, PIF/Photos switches
with app restarts, actual attestation results, GMS/Play Store/Photos, game
profiles, root-manager privacy and default/secondary-user launcher visibility.

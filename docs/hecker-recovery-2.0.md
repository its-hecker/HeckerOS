# Hecker Recovery 2.0 — ROM integration staging

**State:** experimental design and read-only boot checks. The ROM still uses its existing PixelOS/AOSP-based recovery. Do not substitute a standalone OrangeFox boot image for the ROM boot image.

## Development branches

- Custom recovery build: [Hecker Recovery 2.0 Coral](https://github.com/its-hecker/orangefox_device_google_coral/tree/feat/hecker-recovery-2.0).
- This HeckerOS integration staging branch is based on `cnb_latest_stable`.
- November baseline: `fix/november-asb-20261112`. This uses Android 17 / November 2026 boot compatibility metadata but builds the OrangeFox Android 12.1 recovery implementation. Matching metadata is not a claim of November security updates to recovery binaries.

## Why it must be staged

Pixel 4 XL uses `BOARD_USES_RECOVERY_AS_BOOT := true`, Android boot header v2 and a 64 MiB boot partition. The ROM needs a valid normal Android boot path and a recovery boot path. Copying an OrangeFox standalone recovery `boot.img` into the ROM or its OTA payload could break normal boot.

The tool `tools/recovery2_boot_compare.py` inspects two v2 boot images read-only. It compares kernel, DTB, ramdisk, page size, Android version and patch-month metadata. It always reports `safe_to_replace_rom_boot=false`; matching digests alone do not prove bootability or AVB validity.

Example:

```bash
python3 tools/recovery2_boot_compare.py --rom-boot /path/to/rom/boot.img --recovery-boot /path/to/HeckerRecovery-2.0-coral.img
```

## Acceptance gates before a ROM recovery switch

1. Produce and structurally validate the standalone custom image; check touch, display, PIN/decryption, ADB/MTP, file manager, backups, fastbootd, and reboot without formatting data.
2. Confirm installer, OTA/A-B, dynamic partitions and storage handling on a suitable physical device.
3. Create a separately reviewed Android 17-compatible normal/recovery dual-mode ramdisk. Audit init, SELinux labels, bootctl, and key services.
4. Match kernel, DTB, boot metadata, AVB, and recovery ramdisk requirements. Test normal Android boot and recovery boot independently.
5. Confirm OTA upgrades and rollback before changing the ROM default.

The current branch does not change the ROM's boot image, partition layout, recovery code, or OTA payload. It only prepares safe verification for future integration.

## November 13 integration preflight improvements

The dedicated ROM branch provides a **read-only** boot-image inventory and
provenance comparison. It now reports complete boot-image SHA-256 values,
kernel/ramdisk/DTB sizes, ramdisk compression, and whether an AVB footer marker
is present. An AVB footer marker is **not** verification of its signature.
Malformed patch months and oversized/truncated images fail closed.

Optional known checksums can be required:

```bash
python3 tools/recovery2_boot_compare.py \\
  --rom-boot /path/to/rom/boot.img \\
  --recovery-boot /path/to/HeckerRecovery-2.0-coral.img \\
  --rom-sha256 YOUR_64_CHARACTER_SHA256 \\
  --recovery-sha256 YOUR_64_CHARACTER_SHA256
```

The independent OrangeFox [2026-11-13 CI build](https://github.com/its-hecker/orangefox_device_google_coral/actions/runs/43417408737)
completed all kernel, recovery and branded-image validation jobs successfully,
including an experimental downloadable artifact. This validates **packaging only**;
we have not tested that artifact against an actual HeckerOS boot image, normal
Android boot, on-device decryption, both A/B slots or OTA upgrades.

No automatic boot replacement, boot signing, flash command, A/B slot changes or
shipping integration has been added to either stable device manifest.


## Real November boot-image audit (2026-11-13)

This is a **read-only** comparison of the user's November HeckerOS coral boot image
(uploaded 2026-11-12) against the GitHub Actions standalone artifact from
[Coral Recovery 2.0 run 43417408737](https://github.com/its-hecker/orangefox_device_google_coral/actions/runs/43417408737)
(device tree commit 353ac5784118f226bc6c5aee947fc5f9028a32e3).
The SHA256SUMS inside the archive matches its recovery image.

| Check | HeckerOS ROM boot | Standalone Recovery 2.0 |
| --- | --- | --- |
| Image SHA-256 | 3439a85c2eb3b4518e84c9f3327089abfa3b87e44ab6b76a66e2b3a35451e93d | 1065bcbe9740223d3a52ff51c9d174a127e985eb28553f70c8fd0d0930279708 |
| Image size | 67,108,864 bytes | 67,108,864 bytes |
| Header version / size | v2 / 1660 bytes | v2 / 1660 bytes |
| Page size | 4096 bytes | 4096 bytes |
| Android metadata | 17.0.0 / 2026-11 | 17.0.0 / 2026-11 |
| Kernel size | 21,036,194 bytes | 20,930,399 bytes |
| Kernel SHA-256 | d9de4922353d3e273957ed713e9f94c05a10f18100708917be2e25366716fc94 | f7055c524532ada097ec7bb8479ca1d3af1a6b4171b1be61c86a67a697a7cb34 |
| DTB size | 999,136 bytes | 999,136 bytes |
| DTB SHA-256 | e3866c5dc8051d0b9f33f7edd2b1ccdeb1b95777062f3121ea800aabb51eeb26 | a2b911929743858688cc38920f4df25d058ce259fcba670439197a85dc0845f4 |
| Gzip ramdisk size | 18,425,402 bytes | 31,139,092 bytes |
| Ramdisk SHA-256 | c9c543b2b3eacb928c8fd052458800fee9bb98ca462fde84ebe87aa56593e9ea | 4905cf65045bfd3d73e6ae9d42e141f6706d0ff4bd1557b556fbb1b1db69eb05 |
| AVB footer marker | Present; signature unverified | Present; signature unverified |

Both gzip ramdisks were inspected as cpio archives without executing or
installing their contents. Each contains init, system/bin/init, and
system/bin/recovery. The standalone recovery also includes OrangeFox twres
resources. This does **not** demonstrate compatibility with normal Android boot.

**Integration gate: BLOCKED.** The kernel and DTB hashes differ, and the
standalone ramdisk is not proven capable of both normal boot and recovery boot.
Identical Android patch metadata alone does not make the image ROM-compatible.

Next tasks (only on the separate integration branch):

1. Build recovery using the exact current HeckerOS kernel and DTB artifacts,
   or validate another reviewed compatible baseline, then compare hashes.
2. Review a dual-mode Android 17 ramdisk/init strategy, including fstab, SELinux,
   decryption, fastbootd and A/B slot handling.
3. Validate candidate boot structure, signatures with an actual AVB tool,
   and both normal/recovery boots on hardware.
4. Test OTA, backups, decryption and rollback before promoting anything.

Do not replace stable ROM boot.img or OTA boot payload with the standalone
recovery image. No flash, boot swap, image signing, or stable promotion was
performed here. These hashes identify the exact November samples only.

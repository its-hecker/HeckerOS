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

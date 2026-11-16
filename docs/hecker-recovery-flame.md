# Flame hybrid recovery for HeckerOS 2.0 (November 2026)

This configuration is scoped to the **Flame KSU CNB stable** build. The Coral
`cnb_latest_stable` branch and the non-KSU builds are unchanged.

## Verified input

- Source: `its-hecker/orangefox_device_google_flame`
- Source branch: `fix/november-asb-20261112`
- Successful workflow: https://github.com/its-hecker/orangefox_device_google_flame/actions/runs/43899564692
- Actions artifact: `OrangeFox-unofficial-flame-43899564692-1`
- Source commit: `50efc8dc93f6f348bafcb24ec64de35a6dae7489`
- ROM build input: [hecker-recovery2-flame.cpio.gz](https://github.com/its-hecker/orangefox_device_google_flame/releases/download/hecker-recovery2-flame-ramdisk-50efc8d/hecker-recovery2-flame.cpio.gz)
- Full Flame recovery image SHA-256: `a3ce5794ead22683e73b9d642e0394d81c64d59be7c76bdfb5d3cf7107bce5da`
- Extracted ramdisk gzip size: `31246054` bytes
- Extracted ramdisk gzip SHA-256: `7be16045dba3cce2797348efd285ab3c0f63c4470fabc4335cc0ded8f9f4a3f4`

The standalone image is **not** used as HeckerOS boot.img. Its Android 12.1
recovery userspace is embedded in the Android 17 boot ramdisk as a separate
compressed payload, preserving the HeckerOS 4.14 kernel, its DTB and KernelSU Next.

## Runtime routing

- Normal boot and offline charging: Android 17 init/ramdisk
- Interactive recovery: Hecker-branded OrangeFox recovery
- ADB sideload, fastbootd, queued install/wipe and snapshot merges:
  Android 17 HeckerOS/PixelOS-branded recovery

The build script downloads the pinned ramdisk from GitHub Releases without
authentication, verifies its size and SHA-256, and stages it as
`out/hecker-recovery2-flame.cpio.gz`. Subsequent builds reuse a verified cache.
To use the original standalone image as a local source, place it at
`out/OrangeFox-unofficial-flame.img` or select it with
`HECKER_FLAME_OFOX_IMAGE=/path/to/image`. The script verifies both the full
image and the extracted ramdisk.

## Checks

The Flame build runs OrangeFox branding and payload preflight before Android
compilation. Its Makefile adapter packages the exact generated boot image into
the A/B OTA and verifies the embedded recovery and OTA boot payload.

Before releasing, test normal boot, KernelSU Next, recovery touch UI,
PIN/decryption, sideload to Android 17 recovery, fastbootd, OTA slot switching
and data backup/restore on a physical Pixel 4. A successful source/image
build alone does not prove that these hardware paths work.

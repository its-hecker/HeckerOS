# Hecker Recovery 2.0 for Coral

The separate `feat/hecker-recovery-2.0` branches use the current Coral
`cnb_latest_stable` baseline. Running the Coral local manifest's
`build.sh` bundles Hecker Recovery 2.0 in `boot.img` and in each ROM
ZIP built by that branch.

| Boot request | Runtime |
| --- | --- |
| Android or offline charging | HeckerOS Android 17 init and ramdisk |
| Recovery from the bootloader or `adb reboot recovery` | OrangeFox-based Hecker Recovery 2.0 |
| ADB sideload, fastbootd, queued recovery commands or pending Virtual A/B merge | HeckerOS Android 17 recovery |

Coral uses header v2 recovery-as-boot and its boot partition is 64 MiB.
Android 17 init retains the existing normal-boot switch to
`/first_stage_ramdisk`. For an interactive recovery request, init checks the
bootloader message and the Virtual A/B state, then extracts OrangeFox into a
separate root under the initramfs and starts its init. This keeps its Android
12.1 libraries and SELinux policy together. It receives the fstab and boot
properties from that Android 17 build.

The OrangeFox sideload and userspace-fastboot actions reboot into the Android
17 recovery in the same image. That recovery handles ROM sideload, fastbootd,
and queued update or wipe commands. Android's existing update engine and
A/B packaging remain the installation path for OTA updates.

The source build retains Coral's CNB kernel pin, its KernelSU Next gitlink,
the native DTB generation, and the existing Verified Boot configuration.
The normal packaging step includes the generated boot image in
`target_files`, signs the OTA, and extracts its boot partition to confirm
that it matches `boot.img` byte-for-byte. The check also verifies the AVB
hash and both ramdisks.

## Build on ServerHive

Use the [Coral Recovery 2.0 local manifest](https://github.com/its-hecker/HeckerOs-localmanifest/tree/feat/hecker-recovery-2.0).
The build fetches the [pinned OrangeFox ramdisk](https://github.com/its-hecker/orangefox_device_google_coral/releases/tag/hecker-recovery2-coral-ramdisk-353ac57)
and checks its SHA-256. The release links the producing commit and the
source manifest. The [standalone recovery tree](https://github.com/its-hecker/orangefox_device_google_coral/tree/feat/hecker-recovery-2.0)
documents the underlying OrangeFox build.

Host checks cover bootloader-message routing, archive validation, source
adapter application and reversal, and extraction with Coral's Android 17
Toybox. A sizing test with the real November Coral boot image retained the
original kernel and DTB and left about 3.6 MiB of room before the AVB limit.
That test used the existing boot image and replaced its ramdisk only for
measurement.

A full Android 17 ROM build and physical Coral validation are still required.
Check normal boot, the OrangeFox UI and touch, PIN/FBE, backup and restore,
ADB sideload, fastbootd, KernelSU Next, and an A/B OTA across a slot switch.

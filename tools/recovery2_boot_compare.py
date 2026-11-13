#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Read-only header-v2 comparison for HeckerOS and stand-alone Hecker Recovery.

This does NOT generate, modify, sign, flash, or declare a boot image safe. On
coral/flame, recovery shares the boot partition; a standalone recovery ramdisk
cannot simply replace a ROM's normal-boot ramdisk.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

MAX_BOOT_SIZE = 64 * 1024 * 1024
PAGE_SIZE = 4096


def align(size, page):
    return ((size + page - 1) // page) * page


def sha(blob):
    return hashlib.sha256(blob).hexdigest()


def inspect(path):
    path = Path(path)
    data = path.read_bytes()
    if not 4096 <= len(data) <= MAX_BOOT_SIZE or data[:8] != b"ANDROID!":
        raise ValueError("Invalid coral/flame Android boot image: " + str(path))
    kernel_size, = struct.unpack_from("<I", data, 8)
    ramdisk_size, = struct.unpack_from("<I", data, 16)
    second_size, = struct.unpack_from("<I", data, 24)
    page, header_version = struct.unpack_from("<II", data, 36)
    packed_version, = struct.unpack_from("<I", data, 44)
    recovery_dtbo_size, = struct.unpack_from("<I", data, 1632)
    dtb_size, = struct.unpack_from("<I", data, 1648)
    if page != PAGE_SIZE or header_version != 2:
        raise ValueError("Expected Pixel 4 boot header v2 / 4096-byte pages")
    if not 0 < kernel_size < MAX_BOOT_SIZE or not 0 < ramdisk_size < MAX_BOOT_SIZE:
        raise ValueError("Boot image does not have a valid kernel/ramdisk")
    if not 0 < dtb_size < MAX_BOOT_SIZE:
        raise ValueError("Boot image does not contain an embedded DTB")
    kernel_start = page
    ramdisk_start = kernel_start + align(kernel_size, page)
    dtb_start = (ramdisk_start + align(ramdisk_size, page)
                 + align(second_size, page) + align(recovery_dtbo_size, page))
    end = dtb_start + dtb_size
    if end > len(data):
        raise ValueError("Truncated Android boot image")
    major = packed_version >> 25
    minor = (packed_version >> 18) & 0x7f
    patch = (packed_version >> 11) & 0x7f
    patchbits = packed_version & 2047
    os_patch = "%04d-%02d" % (2000 + (patchbits >> 4), patchbits & 15)
    return {
        "image": str(path),
        "size": len(data),
        "header_version": header_version,
        "page_size": page,
        "os_version": f"{major}.{minor}.{patch}",
        "os_patch_month": os_patch,
        "kernel_sha256": sha(data[kernel_start:kernel_start + kernel_size]),
        "ramdisk_sha256": sha(data[ramdisk_start:ramdisk_start + ramdisk_size]),
        "dtb_sha256": sha(data[dtb_start:dtb_start + dtb_size]),
        "recovery_dtbo_size": recovery_dtbo_size,
        "second_size": second_size,
    }


def compare(rom, recovery):
    a = inspect(rom)
    b = inspect(recovery)
    matches = {key: a[key] == b[key]
               for key in ("kernel_sha256", "dtb_sha256", "os_version",
                           "os_patch_month", "page_size", "header_version")}
    return {
        "rom": a, "standalone_recovery": b, "matches": matches,
        "safe_to_replace_rom_boot": False,
        "reason": (
            "Coral/flame use recovery-as-boot. Even if the kernel and DTB match, "
            "the standalone recovery ramdisk is not verified to support normal "
            "Android boot. Do not replace the ROM boot image or OTA boot payload."
        ),
        "next_checks": [
            "Compile a recovery-compatible normal/recovery dual-mode boot ramdisk",
            "Review Android 17 init, SELinux policy, partitions and boot control",
            "Verify AVB metadata with the matching external avbtool",
            "Validate normal Android boot, recovery boot, OTA and both slots",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom-boot", type=Path, required=True)
    parser.add_argument("--recovery-boot", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(compare(args.rom_boot, args.recovery_boot), indent=2))


if __name__ == "__main__":
    main()

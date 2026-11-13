#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""No boot images are written by the integration analyzer; fixtures are synthetic."""
import pathlib
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "tools"))
from recovery2_boot_compare import inspect, compare, PAGE_SIZE


def sample(path, kernel=b"KERNEL", ramdisk=b"ANDROID_INIT", dtb=b"DTB", patch_month=11):
    data = bytearray(PAGE_SIZE * 4)
    data[:8] = b"ANDROID!"
    struct.pack_into("<I", data, 8, len(kernel))
    struct.pack_into("<I", data, 16, len(ramdisk))
    struct.pack_into("<I", data, 24, 0)
    struct.pack_into("<II", data, 36, PAGE_SIZE, 2)
    # v2 packed OS is major/minor/patch plus patch YYYY-MM.
    packed = (17 << 25) | ((2026 - 2000) << 4) | patch_month
    struct.pack_into("<I", data, 44, packed)
    struct.pack_into("<I", data, 1632, 0)
    struct.pack_into("<I", data, 1648, len(dtb))
    data[PAGE_SIZE:PAGE_SIZE + len(kernel)] = kernel
    data[PAGE_SIZE * 2:PAGE_SIZE * 2 + len(ramdisk)] = ramdisk
    data[PAGE_SIZE * 3:PAGE_SIZE * 3 + len(dtb)] = dtb
    path.write_bytes(data)


class CustomRecoveryCompatibility(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.a = pathlib.Path(temporary.name) / "rom.img"
        self.b = pathlib.Path(temporary.name) / "recovery.img"

    def test_matching_kernel_dtb_still_not_automatically_swappable(self):
        sample(self.a, ramdisk=b"android")
        sample(self.b, ramdisk=b"orangefox")
        result = compare(self.a, self.b)
        self.assertTrue(result["matches"]["kernel_sha256"])
        self.assertTrue(result["matches"]["dtb_sha256"])
        self.assertNotEqual(result["rom"]["ramdisk_sha256"],
                            result["standalone_recovery"]["ramdisk_sha256"])
        self.assertFalse(result["safe_to_replace_rom_boot"])
        self.assertEqual(result["rom"]["os_version"], "17.0.0")
        self.assertEqual(result["rom"]["os_patch_month"], "2026-11")

    def test_kernel_dtb_and_patch_drift_reported(self):
        sample(self.a)
        sample(self.b, kernel=b"OTHER", dtb=b"DIFFERENT", patch_month=10)
        result = compare(self.a, self.b)
        for key in ("kernel_sha256", "dtb_sha256", "os_patch_month"):
            self.assertFalse(result["matches"][key])
        self.assertFalse(result["safe_to_replace_rom_boot"])

    def test_invalid_boot_headers_fail_closed(self):
        self.a.write_bytes(b"x" * 100)
        with self.assertRaisesRegex(ValueError, "Invalid"):
            inspect(self.a)
        sample(self.a)
        damaged = bytearray(self.a.read_bytes())
        struct.pack_into("<I", damaged, 36, 1024)
        self.a.write_bytes(damaged)
        with self.assertRaisesRegex(ValueError, "header v2"):
            inspect(self.a)

    def test_truncation_detected(self):
        sample(self.a)
        bad = bytearray(self.a.read_bytes())
        struct.pack_into("<I", bad, 1648, 2**20)
        self.a.write_bytes(bad)
        with self.assertRaisesRegex(ValueError, "Truncated"):
            inspect(self.a)


if __name__ == "__main__":
    unittest.main()

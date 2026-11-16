#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""No boot images are written by the integration analyzer; fixtures are synthetic."""
import pathlib
import subprocess
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "tools"))
from recovery2_boot_compare import inspect, compare, verify_expected_sha, PAGE_SIZE


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
        self.assertTrue(result["kernel_dtb_matched"])
        self.assertIn("ramdisk_sha256", result["component_mismatches"])
        self.assertNotEqual(result["rom"]["ramdisk_sha256"],
                            result["standalone_recovery"]["ramdisk_sha256"])
        self.assertFalse(result["safe_to_replace_rom_boot"])
        self.assertFalse(result["matches"]["ramdisk_sha256"])
        self.assertEqual(len(result["rom"]["boot_image_sha256"]), 64)
        self.assertEqual(result["rom"]["boot_payload_end"], PAGE_SIZE * 3 + 3)
        self.assertEqual(result["rom"]["ramdisk_compression"], "unknown")
        self.assertEqual(result["verification_state"]["avb_signatures"], "not_verified")
        self.assertEqual(result["rom"]["os_version"], "17.0.0")
        self.assertEqual(result["rom"]["os_patch_month"], "2026-11")

    def test_kernel_dtb_and_patch_drift_reported(self):
        sample(self.a)
        sample(self.b, kernel=b"OTHER", dtb=b"DIFFERENT", patch_month=10)
        result = compare(self.a, self.b)
        for key in ("kernel_sha256", "dtb_sha256", "os_patch_month"):
            self.assertFalse(result["matches"][key])
        self.assertFalse(result["kernel_dtb_matched"])
        self.assertIn("kernel_sha256", result["component_mismatches"])
        self.assertIn("dtb_sha256", result["component_mismatches"])
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


    def test_cli_strict_kernel_dtb_gate_is_read_only(self):
        tool = str(pathlib.Path(__file__).resolve().parents[1] / "tools/recovery2_boot_compare.py")
        sample(self.a, kernel=b"ROM_K", dtb=b"ROM_DTB")
        sample(self.b, kernel=b"REC_K", dtb=b"REC_DTB")
        first = self.a.read_bytes()
        second = self.b.read_bytes()
        blocked = subprocess.run([sys.executable, tool, "--rom-boot", str(self.a),
                                  "--recovery-boot", str(self.b),
                                  "--require-matching-kernel-dtb"],
                                 capture_output=True, text=True)
        self.assertNotEqual(blocked.returncode, 0)
        self.assertIn("integration blocked", blocked.stderr)
        report = subprocess.run([sys.executable, tool, "--rom-boot", str(self.a),
                                 "--recovery-boot", str(self.b)],
                                capture_output=True, text=True)
        self.assertEqual(report.returncode, 0, report.stderr)
        self.assertIn('"kernel_dtb_matched": false', report.stdout)
        self.assertEqual(self.a.read_bytes(), first)
        self.assertEqual(self.b.read_bytes(), second)

    def test_artifact_sha_guards(self):
        sample(self.a)
        meta = inspect(self.a)
        verify_expected_sha(meta, meta["boot_image_sha256"], "ROM")
        verify_expected_sha(meta, meta["boot_image_sha256"].upper(), "ROM")
        with self.assertRaisesRegex(ValueError, "mismatch"):
            verify_expected_sha(meta, "0" * 64, "ROM")
        with self.assertRaisesRegex(ValueError, "64 hexadecimal"):
            verify_expected_sha(meta, "not-a-sha", "ROM")

    def test_ramdisk_compression_and_avb_footer_are_reported_not_verified(self):
        sample(self.a, ramdisk=b"\x1f\x8bMOCK")
        info = inspect(self.a)
        self.assertEqual(info["ramdisk_compression"], "gzip")
        self.assertFalse(info["avb_footer_present"])
        image = bytearray(self.a.read_bytes())
        image[-64:-60] = b"AVBf"
        self.a.write_bytes(image)
        self.assertTrue(inspect(self.a)["avb_footer_present"])

    def test_reject_invalid_patch_month(self):
        sample(self.a, patch_month=0)
        with self.assertRaisesRegex(ValueError, "patch month"):
            inspect(self.a)
        sample(self.a, patch_month=15)
        with self.assertRaisesRegex(ValueError, "patch month"):
            inspect(self.a)

    def test_never_swappable_even_with_identical_ramdisks(self):
        sample(self.a)
        sample(self.b)
        report = compare(self.a, self.b)
        self.assertTrue(report["matches"]["ramdisk_sha256"])
        self.assertFalse(report["safe_to_replace_rom_boot"])


if __name__ == "__main__":
    unittest.main()

# SPDX-License-Identifier: Apache-2.0
import io
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from recovery2_rom import (BOOT_PROP, FASTBOOT, FLASH_ZIP, FOX_INIT,
                          FOX_INSTALL_PAGE, FOX_RECOVERY, FOX_SH, FOX_THEME, NATIVE_FASTBOOT,
                          NATIVE_SIDELOAD, RECOVERY_SEPOLICY, REFLASH_ITEMS, ROM_FSTAB, SIDELOAD,
                          add_entry, entries, patch_payload)


def archive(files):
    output = io.BytesIO()
    for number, (name, data) in enumerate(files.items(), 1):
        mode = stat.S_IFREG | 0o644
        fields = (number, mode, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0)
        header = b"070701" + "".join(f"{value:08x}" for value in fields).encode()
        add_entry(output, name, header, data)
    return output.getvalue()


class CoralRecoveryTests(unittest.TestCase):
    def test_recovery_ramdisk_keeps_coral_fstab_and_uses_a17_sideload(self):
        source = archive({
            FOX_INIT: b"init", FOX_RECOVERY: b"recovery", FOX_THEME: b"ui",
            FOX_SH: b"shell", RECOVERY_SEPOLICY: b"policy",
            FOX_INSTALL_PAGE: SIDELOAD + b'<action function="page">action_page</action>',
            "twres/pages/main.xml": FASTBOOT,
            "twres/pages/install.xml": REFLASH_ITEMS[0] + b"</listitem>" + FLASH_ZIP * 2,
            "twres/pages/settings.xml": REFLASH_ITEMS[0] + b"</listitem>",
            ROM_FSTAB: b"old fstab", "TRAILER!!!": b"",
        })
        output = io.BytesIO()
        patch_payload(source, output, b"A17 props", b"A17 fstab")
        files = {name: data for name, _, _, data in entries(output.getvalue())}
        self.assertEqual(files[ROM_FSTAB], b"A17 fstab")
        self.assertEqual(files[BOOT_PROP], b"A17 props")
        self.assertEqual(files[FOX_INSTALL_PAGE], NATIVE_SIDELOAD)
        self.assertEqual(files["twres/pages/main.xml"], NATIVE_FASTBOOT)
        self.assertNotIn(REFLASH_ITEMS[0], files["twres/pages/install.xml"])
        self.assertEqual(files["twres/pages/install.xml"].count(b"tw_auto_reflashtwrp=0"), 2)
        self.assertEqual(files[FOX_INIT], b"init")

    def test_rejects_truncated_or_escaping_archive(self):
        valid = archive({"system/bin/init": b"init", "TRAILER!!!": b""})
        with self.assertRaisesRegex(ValueError, "truncated"):
            list(entries(valid[:-20]))
        with self.assertRaisesRegex(ValueError, "Invalid or duplicate"):
            list(entries(archive({"../init": b"init", "TRAILER!!!": b""})))

    def test_boot_control_routing(self):
        source = r'''
#include "hecker_recovery_boot.h"
using android::init::hecker_recovery::UseTouchRecovery;
static_assert(UseTouchRecovery("", "", 0));
static_assert(UseTouchRecovery("boot-recovery", "recovery\n", 0));
static_assert(!UseTouchRecovery("boot-recovery", "recovery\n--sideload\n", 0));
static_assert(!UseTouchRecovery("boot-recovery", "recovery\n--update_package=/data/ota.zip\n", 0));
static_assert(!UseTouchRecovery("boot-recovery", "recovery\n--fastboot\n", 0));
static_assert(!UseTouchRecovery("boot-recovery", "recovery\n--wipe_data\n", 0));
static_assert(!UseTouchRecovery("bootonce-bootloader", "", 0));
static_assert(!UseTouchRecovery("boot-recovery", "", 1));
static_assert(!UseTouchRecovery("boot-recovery", "", 2));
static_assert(!UseTouchRecovery("boot-recovery", "", 3));
static_assert(!UseTouchRecovery("boot-recovery", "", 4));
int main() {}
'''
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(["c++", "-std=c++17", "-I", str(ROOT / "recovery/rom"),
                            "-x", "c++", "-o", str(Path(directory) / "mode-check"), "-"],
                           input=source, text=True, check=True)


if __name__ == "__main__":
    unittest.main()

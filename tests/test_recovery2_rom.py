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
                          BRAND_IMAGE, BRAND_SETTINGS, BRAND_SPLASH, BRAND_RESOURCES, PNG_MAGIC,
                          add_entry, entries, patch_payload)
from recovery2_port import (previous_recovery_boot_cpp, recovery_rom_plan,
                            BRANDING_SOURCE, INSTALL, DEPENDENCIES,
                            LEGACY_INSTALL_BLOCK, MAKE_ANCHOR)


def archive(files):
    output = io.BytesIO()
    for number, (name, data) in enumerate(files.items(), 1):
        mode = stat.S_IFREG | 0o644
        fields = (number, mode, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0)
        header = b"070701" + "".join(f"{value:08x}" for value in fields).encode()
        add_entry(output, name, header, data)
    return output.getvalue()


class CoralRecoveryTests(unittest.TestCase):
    def test_branding_path_is_supplied_to_packaged_host_executable(self):
        # Python host executables run from out/host/linux-x86/bin rather than
        # the source tree: __file__.parents[1] is NOT the Suite checkout.
        from recovery2_rom import install
        import inspect
        self.assertEqual(len(inspect.signature(install).parameters), 4)
        source = (ROOT / "tools/recovery2_rom.py").read_text()
        self.assertNotIn('BRANDING_ASSET = Path(__file__)', source)
        self.assertIn('stage.add_argument("brand_asset", type=Path)', source)
        self.assertIn('install(args.zstd, args.source, args.root, args.brand_asset)', source)
        self.assertIn('brand_asset.read_bytes()', source)
        self.assertEqual(
            BRANDING_SOURCE,
            "vendor/hecker/floral/suite/recovery/res-xxhdpi/images/logo_image.png")
        self.assertTrue((ROOT / "recovery/res-xxhdpi/images/logo_image.png").is_file())
        self.assertTrue(INSTALL.rstrip().endswith(BRANDING_SOURCE))
        self.assertIn(BRANDING_SOURCE, DEPENDENCIES)

    def test_packaged_hecker_branding_replaces_splash_and_about(self):
        # Synthetic OrangeFox archive: exact theme contracts, no real device access.
        logo = (PNG_MAGIC + b"fake_png_for_test" + b"\x00\x00\x00\x00"
                + b"IEND" + b"\x00\x00\x00\x00")
        files = {
            FOX_INIT: b"init", FOX_RECOVERY: b"recovery", FOX_THEME: b"ui",
            FOX_SH: b"shell", RECOVERY_SEPOLICY: b"policy",
            FOX_INSTALL_PAGE: SIDELOAD + b'<action function="page">action_page</action>',
            "twres/pages/main.xml": FASTBOOT + b'<image resource="splash_logo_o"/>',
            "twres/pages/install.xml": FLASH_ZIP * 2,
            BRAND_SETTINGS: (b'<image resource="logo_about"/>'
                             b'<text>OrangeFox Recovery</text>'),
            BRAND_RESOURCES: (b'<recovery><resources>'
                              b'<image name="splash_logo_o" filename="SVG/Logos/logo_o.svg"/>'
                              b'</resources></recovery>'),
            "twres/themes/style.xml": (b'<recovery><resources>'
                                       b'<image name="logo_about" '
                                       b'filename="SVG/Logos/logo_o.svg"/>'
                                       b'</resources></recovery>'),
            "twres/pages/fox.xml": b'<text>OrangeFox Lab (Beta)</text>',
            BRAND_SPLASH: (b'<image name="splash_logo" '
                           b'filename="SVG/Logos/logo_o.svg" retainaspect="1" scale="2.0"/>'
                           b'<image><image resource="splash_bg"/>'
                           b'<placement x="540" y="540"/></image>'
                           b'<text>OrangeFox</text>'),
            ROM_FSTAB: b"old fstab",
            "TRAILER!!!": b"",
        }
        output = io.BytesIO()
        patch_payload(archive(files), output, b"A17", b"fstab17", brand_png=logo)
        updated = {name: data for name, _, _, data in entries(output.getvalue())}
        self.assertEqual(updated[BRAND_IMAGE], logo)
        self.assertEqual(updated[BRAND_SPLASH].count(b'<text>HeckerOS</text>'), 1)
        self.assertNotIn(b'<text>OrangeFox</text>', updated[BRAND_SPLASH])
        self.assertNotIn(b'resource="splash_bg"', updated[BRAND_SPLASH])
        self.assertIn(b'filename="Hecker/logo" retainaspect="1" scale="1.0"',
                      updated[BRAND_SPLASH])
        self.assertIn(b'<text>HeckerOS Recovery</text>', updated[BRAND_SETTINGS])
        self.assertIn(b'<image resource="hecker_logo"/>', updated[BRAND_SETTINGS])
        self.assertIn(b'<image resource="hecker_logo"/>', updated["twres/pages/main.xml"])
        self.assertIn(b'name="hecker_logo"', updated[BRAND_RESOURCES])
        self.assertIn(b'name="splash_logo_o" filename="Hecker/logo"',
                      updated[BRAND_RESOURCES])
        self.assertIn(b'name="logo_about" filename="Hecker/logo"',
                      updated["twres/themes/style.xml"])
        self.assertIn(b'<text>Hecker Recovery Lab (Beta)</text>',
                      updated["twres/pages/fox.xml"])
        self.assertEqual(updated[ROM_FSTAB], b"fstab17")
        with self.assertRaisesRegex(ValueError, "logo"):
            patch_payload(archive(files), io.BytesIO(), b"A17", b"fstab17",
                          brand_png=b"not a PNG")

    def test_splash_logo_variant_is_rebranded_by_resource_name(self):
        # Pinned OrangeFox revisions may select a different logo artwork.
        from recovery2_rom import rebrand_theme, BRAND_SPLASH
        original = (b'<recovery><resources><image name="splash_logo" '
                    b'filename="SVG/Logos/logo_d.svg" retainaspect="1" scale="2.0"/>'
                    b'</resources><pages><text>OrangeFox</text></pages></recovery>')
        patched = rebrand_theme(BRAND_SPLASH, original)
        self.assertIn(b'filename="Hecker/logo" retainaspect="1" scale="1.0"', patched)
        self.assertNotIn(b'logo_d.svg', patched)
        self.assertIn(b'<text>HeckerOS</text>', patched)
        with self.assertRaisesRegex(ValueError, "splash logo resource"):
            rebrand_theme(BRAND_SPLASH, original.replace(b'name="splash_logo"',
                                                        b'name="unexpected_logo"'))

    def test_migrate_duplicate_legacy_installer_on_reused_build_tree(self):
        # The reported failure showed two install calls in one Make recipe.
        from recovery2_port import SOURCES
        with tempfile.TemporaryDirectory() as temp:
            rom = Path(temp)
            core = rom / "system/core/init"
            make = rom / "build/make/core"
            core.mkdir(parents=True)
            make.mkdir(parents=True)
            for name in ("hecker_recovery_boot.cpp", "hecker_recovery_boot.h"):
                (core / name).write_bytes((SOURCES / name).read_bytes())
            (core / "Android.bp").write_text("")
            (core / "first_stage_init.cpp").write_text("")
            makefile = make / "Makefile"
            block = "ifeq ($(TARGET_PRODUCT),hecker_coral)\\n" + INSTALL + "endif\\n"
            makefile.write_text(MAKE_ANCHOR + block + LEGACY_INSTALL_BLOCK)
            # Simulate idempotent guarded integration, not Android Make internals.
            def safe_replace(content, old, new, *_args):
                if new in content:
                    return content
                return content.replace(old, new, 1) if old in content else content
            plan = recovery_rom_plan(rom, safe_replace)
            updated = plan[makefile]
            self.assertEqual(updated.count(INSTALL), 1)
            self.assertNotIn(LEGACY_INSTALL_BLOCK, updated)
            self.assertEqual(
                len([line for line in updated.splitlines()
                     if "hecker_recovery2_rom install " in line]), 1)

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

    def test_android17_init_uses_explicit_unique_fd_descriptors(self):
        fixed = (ROOT / "recovery/rom/hecker_recovery_boot.cpp").read_bytes()
        self.assertEqual(fixed.count(b"!Unpack(input.get(), cpio.get())"), 1)
        self.assertEqual(fixed.count(b"lseek(cpio.get(), 0, SEEK_SET)"), 1)
        self.assertEqual(fixed.count(b"dup2(cpio.get(), STDIN_FILENO)"), 1)
        old = previous_recovery_boot_cpp(fixed)
        self.assertIn(b"!Unpack(input, cpio)", old)
        self.assertIn(b"lseek(cpio, 0, SEEK_SET)", old)
        self.assertIn(b"dup2(cpio, STDIN_FILENO)", old)

    def test_existing_generated_cpp_is_upgraded_but_unrelated_changes_fail_closed(self):
        fixed = (ROOT / "recovery/rom/hecker_recovery_boot.cpp").read_bytes()
        old = previous_recovery_boot_cpp(fixed)
        with tempfile.TemporaryDirectory() as temp:
            rom = Path(temp)
            core = rom / "system/core/init"
            make = rom / "build/make/core"
            core.mkdir(parents=True)
            make.mkdir(parents=True)
            (core / "Android.bp").write_text("")
            (core / "first_stage_init.cpp").write_text("")
            (make / "Makefile").write_text("")
            target = core / "hecker_recovery_boot.cpp"
            target.write_bytes(old)
            replace_noop = lambda content, *_args: content
            plan = recovery_rom_plan(rom, replace_noop)
            self.assertEqual(plan[target], fixed)
            target.write_bytes(fixed)
            self.assertNotIn(target, recovery_rom_plan(rom, replace_noop))
            target.write_bytes(fixed + b"// unknown local change\\n")
            with self.assertRaisesRegex(ValueError, "Unfamiliar Coral recovery source"):
                recovery_rom_plan(rom, replace_noop)

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

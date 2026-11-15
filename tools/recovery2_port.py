# SPDX-License-Identifier: Apache-2.0
"""Attach the Coral recovery payload to the native recovery-as-boot build."""

from pathlib import Path
import hashlib

SUITE = Path(__file__).resolve().parents[1]
SOURCES = SUITE / "recovery/rom"
HOOK = "    SetInitAvbVersionInRecovery();\n"
INCLUDE = '#include "first_stage_init.h"\n'
BOARD = "$(BOARD_RECOVERY_IMAGE_PREPARE)"
MAKE_ANCHOR = "\t" + BOARD + "\n"
# Pass the caracal bitmap from the source checkout, NOT relative to the Soong
# host executable, which is installed under out/host/.../bin/.
BRANDING_SOURCE = "vendor/hecker/floral/suite/recovery/res-xxhdpi/images/logo_image.png"
ARTWORK_DIR = "out/hecker-recovery2-brand-assets"
INSTALL = ("\t$(HOST_OUT_EXECUTABLES)/hecker_recovery2_rom install "
           "$(ZSTD) out/hecker-recovery2-coral.cpio.gz "
           "$(TARGET_RECOVERY_ROOT_OUT) " + BRANDING_SOURCE + " " + ARTWORK_DIR + "\n")
# Earlier builds generated the same action without a logo argument.  Reusing
# a dirty Makefile would run both actions, causing a second failed install.
LEGACY_INSTALL = ("\t$(HOST_OUT_EXECUTABLES)/hecker_recovery2_rom install "
                  "$(ZSTD) out/hecker-recovery2-coral.cpio.gz "
                  "$(TARGET_RECOVERY_ROOT_OUT)\n")
LEGACY_INSTALL_BLOCK = (
    "ifeq ($(TARGET_PRODUCT),hecker_coral)\n" + LEGACY_INSTALL + "endif\n"
)
DEPENDENCIES = ("$(ZSTD)", "out/hecker-recovery2-coral.cpio.gz",
                "$(HOST_OUT_EXECUTABLES)/hecker_recovery2_rom",
                BRANDING_SOURCE, ARTWORK_DIR + "/about.png",
                ARTWORK_DIR + "/icon.png")

# Only migrate the exact previously generated source. Any other local change
# still fails closed, preserving the guarded ROM preparation workflow.
LEGACY_FD_CALLS = (
    (b"!Unpack(input.get(), cpio.get())", b"!Unpack(input, cpio)"),
    (b"lseek(cpio.get(), 0, SEEK_SET)", b"lseek(cpio, 0, SEEK_SET)"),
    (b"dup2(cpio.get(), STDIN_FILENO)", b"dup2(cpio, STDIN_FILENO)"),
)


def previous_recovery_boot_cpp(expected):
    old = expected
    for corrected, legacy in LEGACY_FD_CALLS:
        if old.count(corrected) != 1:
            raise ValueError("Unrecognized Hecker Recovery 2.0 file-descriptor patch")
        old = old.replace(corrected, legacy, 1)
    return old


# Only exact previously shipped generated sources may be upgraded in place.
# Do not accept arbitrary local modifications to Android init.
LEGACY_GENERATED_GIT_BLOBS = {
    "hecker_recovery_boot.cpp": {
        "a54b17c738d553e0397919d3486715ce8e8d8474",
        "67383bf3e53d40909a918c28d30d5a9e0cf53051",
    },
    "hecker_recovery_boot.h": {
        "4cf7eaff1e8583a9f899bb2571cf919",
    },
}


def git_blob_sha(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def recovery_rom_plan(rom, replace, revert=False):
    core = rom / "system/core/init"
    make = rom / "build/make/core/Makefile"
    files = {}
    for name in ("hecker_recovery_boot.cpp", "hecker_recovery_boot.h"):
        target = core / name
        expected = (SOURCES / name).read_bytes()
        previous = (previous_recovery_boot_cpp(expected)
                    if name == "hecker_recovery_boot.cpp" else expected)
        current = target.read_bytes() if target.exists() else None
        if (current is not None and current not in (expected, previous)
                and git_blob_sha(current) not in LEGACY_GENERATED_GIT_BLOBS[name]):
            raise ValueError(f"Unfamiliar Coral recovery source: {target}")
        if revert and current is not None:
            files[target] = None
        elif not revert and current != expected:
            files[target] = expected

    init = core / "first_stage_init.cpp"
    current = init.read_text()
    updated = replace(current, INCLUDE,
                      INCLUDE + '\n#if defined(HECKER_TOUCH_RECOVERY)\n'
                      '#include "hecker_recovery_boot.h"\n#endif\n', str(init), revert)
    updated = replace(updated, HOOK, HOOK + '''
#if defined(HECKER_TOUCH_RECOVERY)
    if (boot_mode == BootMode::RECOVERY_MODE && !ForceNormalBoot(cmdline, bootconfig) &&
        IsRecoveryMode() &&
        access(hecker_recovery::kPayload, R_OK) == 0) {
        hecker_recovery::StartTouchRecovery();
    }
#endif
''', str(init), revert)
    if updated != current:
        files[init] = updated

    blueprint = core / "Android.bp"
    current = blueprint.read_text()
    anchor = '''    name: "init_second_stage.recovery",
    defaults: ["init_second_stage_defaults"],
    static_libs: ["libinit"],
    recovery: true,
    cflags: ["-DRECOVERY"],
'''
    enabled = anchor.replace('static_libs: ["libinit"]',
                             'static_libs: ["libinit", "libbootloader_message", "libzstd"]')
    enabled = enabled.replace('cflags: ["-DRECOVERY"]',
                              'cflags: ["-DRECOVERY", "-DHECKER_TOUCH_RECOVERY"]')
    end = '''    exclude_static_libs: [
        "libxml2",
    ],
    exclude_shared_libs: [
        "libbinder",
        "libutils",
    ],
    srcs: [
        "first_stage_init.cpp",
'''
    updated = replace(current, anchor + end,
                      enabled + end + '        "hecker_recovery_boot.cpp",\n',
                      str(blueprint), revert)
    if updated != current:
        files[blueprint] = updated

    current = make.read_text()
    if current.count(LEGACY_INSTALL_BLOCK) > 1:
        raise ValueError("Duplicate legacy Recovery 2.0 installation blocks")
    # Migrate previously generated Makefiles before applying the latest rule.
    clean = current.replace(LEGACY_INSTALL_BLOCK, "", 1)
    anchor = '$(INTERNAL_RECOVERY_RAMDISK_FILES_TIMESTAMP): $(MKBOOTFS) \\\n'
    deps = ('ifeq ($(TARGET_PRODUCT),hecker_coral)\n'
            '$(INTERNAL_RECOVERY_RAMDISK_FILES_TIMESTAMP): ' + ' \\\n    '.join(DEPENDENCIES) +
            '\nendif\n\n')
    updated = replace(clean, anchor, deps + anchor, str(make), revert)
    updated = replace(updated, MAKE_ANCHOR, MAKE_ANCHOR +
                      'ifeq ($(TARGET_PRODUCT),hecker_coral)\n' + INSTALL + 'endif\n',
                      str(make), revert)
    # Preserve the exact AVB-processed boot image through target_files and OTA.
    copy = ('ifneq (,$(strip $(INTERNAL_PREBUILT_BOOTIMAGE) '
            '$(filter true,$(BOARD_COPY_BOOT_IMAGE_TO_TARGET_FILES))))\n')
    updated = replace(updated, copy, copy.replace('$(INTERNAL_PREBUILT_BOOTIMAGE)',
                      '$(INTERNAL_PREBUILT_BOOTIMAGE) $(filter hecker_coral,$(TARGET_PRODUCT))'),
                      str(make), revert)
    ota = 'otapackage: $(INTERNAL_OTA_PACKAGE_TARGET)\n'
    updated = replace(updated, ota, ota + '''
ifeq ($(TARGET_PRODUCT),hecker_coral)
.PHONY: hecker-recovery-ota-check
heckeros: hecker-recovery-ota-check
hecker-recovery-ota-check: $(INTERNAL_OTA_PACKAGE_TARGET) $(INSTALLED_BOOTIMAGE_TARGET) \\
    $(ZSTD) $(AVBTOOL) $(HOST_OUT_EXECUTABLES)/ota_extractor \\
    $(HOST_OUT_EXECUTABLES)/hecker_recovery2_rom
	$(HOST_OUT_EXECUTABLES)/hecker_recovery2_rom verify \\
	    $(ZSTD) $(AVBTOOL) $(HOST_OUT_EXECUTABLES)/ota_extractor \\
	    $(PRODUCT_OUT)/boot.img $(INTERNAL_OTA_PACKAGE_TARGET)
endif
''', str(make), revert)
    install_lines = [line for line in updated.splitlines()
                     if line.lstrip().startswith("$(HOST_OUT_EXECUTABLES)/hecker_recovery2_rom install ")]
    if len(install_lines) > 1 or any(line != INSTALL.rstrip("\n") for line in install_lines):
        raise ValueError("Unrecognized or duplicate Recovery 2.0 install rule")
    if updated != current:
        files[make] = updated
    return files

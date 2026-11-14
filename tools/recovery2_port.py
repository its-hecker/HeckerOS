# SPDX-License-Identifier: Apache-2.0
"""Attach the Coral recovery payload to the native recovery-as-boot build."""

from pathlib import Path

SUITE = Path(__file__).resolve().parents[1]
SOURCES = SUITE / "recovery/rom"
SOURCE_PATH = "vendor/hecker/floral/suite"
HOOK = "    SetInitAvbVersionInRecovery();\n"
INCLUDE = '#include "first_stage_init.h"\n'
BOARD = "$(BOARD_RECOVERY_IMAGE_PREPARE)"
MAKE_ANCHOR = "\t" + BOARD + "\n"
INSTALL = ("\tpython3 " + SOURCE_PATH + "/tools/recovery2_rom.py install "
           "$(ZSTD) out/hecker-recovery2-coral.cpio.gz "
           "$(TARGET_RECOVERY_ROOT_OUT)\n")
DEPENDENCIES = ("$(ZSTD)", "out/hecker-recovery2-coral.cpio.gz",
                SOURCE_PATH + "/tools/recovery2_rom.py",
                SOURCE_PATH + "/tools/recovery2_boot_compare.py")


def recovery_rom_plan(rom, replace, revert=False):
    core = rom / "system/core/init"
    make = rom / "build/make/core/Makefile"
    files = {}
    for name in ("hecker_recovery_boot.cpp", "hecker_recovery_boot.h"):
        target = core / name
        expected = (SOURCES / name).read_bytes()
        if target.exists() and target.read_bytes() != expected:
            raise ValueError(f"Unfamiliar Coral recovery source: {target}")
        if revert and target.exists():
            files[target] = None
        elif not revert and not target.exists():
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
    anchor = '$(INTERNAL_RECOVERY_RAMDISK_FILES_TIMESTAMP): $(MKBOOTFS) \\\n'
    deps = ('ifeq ($(TARGET_PRODUCT),hecker_coral)\n'
            '$(INTERNAL_RECOVERY_RAMDISK_FILES_TIMESTAMP): ' + ' \\\n    '.join(DEPENDENCIES) +
            '\nendif\n\n')
    updated = replace(current, anchor, deps + anchor, str(make), revert)
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
    $(ZSTD) $(AVBTOOL) $(HOST_OUT_EXECUTABLES)/ota_extractor
	python3 vendor/hecker/floral/suite/tools/recovery2_rom.py verify \\
	    $(ZSTD) $(AVBTOOL) $(HOST_OUT_EXECUTABLES)/ota_extractor \\
	    $(PRODUCT_OUT)/boot.img $(INTERNAL_OTA_PACKAGE_TARGET)
endif
''', str(make), revert)
    if updated != current:
        files[make] = updated
    return files

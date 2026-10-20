# SPDX-License-Identifier: Apache-2.0
# The capture uses AOSP's existing userdebug/eng init-to-su service permission.
ifneq ($(filter eng userdebug,$(TARGET_BUILD_VARIANT)),)
PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/rom/boot_diagnostics/boot_capture.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/pixelos-boot-capture.rc \
    vendor/hecker/floral/suite/rom/boot_diagnostics/boot_capture.sh:$(TARGET_COPY_OUT_SYSTEM)/etc/pixelos/boot_capture.sh

PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    system/etc/init/pixelos-boot-capture.rc \
    system/etc/pixelos/boot_capture.sh
endif

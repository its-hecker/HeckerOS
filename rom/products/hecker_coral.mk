#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# HeckerOS inherits the pinned PixelOS Android 17 platform configuration.
# The real IR hardware provider is supplied by the Floral Face ID plugin.
TARGET_FACE_UNLOCK_SUPPORTED := false
PRODUCT_NO_CAMERA := true
TARGET_SCREEN_WIDTH := 1440
TARGET_SCREEN_HEIGHT := 3040
CUSTOM_BUILD := coral

# Retain the source-built mainline configuration used by the working Floral base.
PRODUCT_BUILD_IGNORE_APEX_CONTRIBUTION_CONTENTS := true
PRODUCT_MODULE_BUILD_FROM_SOURCE := true

$(call inherit-product, vendor/custom/config/common_full_phone.mk)

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

# Enable mainline checking
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

# Google system artifacts selected by PixelOS's bundled GMS.
PRODUCT_ARTIFACT_PATH_REQUIREMENT_ALLOWED_LIST += \
    system/apex/com.google.android.extservices.apex \
    system/app/GoogleExtShared/GoogleExtShared.apk \
    system/app/GooglePrintRecommendationService/GooglePrintRecommendationService.apk \
    system/etc/permissions/privapp-permissions-google.xml \
    system/lib/libtensorflowlite_jni.so \
    system/lib64/libtensorflowlite_jni.so \
    system/priv-app/DocumentsUIGoogle/DocumentsUIGoogle.apk \
    system/priv-app/TagGoogle/TagGoogle.apk

#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

#
# All components inherited here go to vendor image
#
# TODO(b/136525499): move *_vendor.mk into the vendor makefile later
TARGET_SUPPORTS_OMX_SERVICE := false
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_vendor.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_vendor.mk)

include device/google/coral/coral/device.mk

# Device identifier. This must come after all inclusions
PRODUCT_BRAND := google
PRODUCT_DEVICE := coral
PRODUCT_MANUFACTURER := Google
PRODUCT_MODEL := Pixel 4 XL
PRODUCT_NAME := hecker_coral

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="coral-user 13 TP1A.221005.002.B2 9382335 release-keys" \
    BuildFingerprint=google/coral/coral:13/TP1A.221005.002.B2/9382335:user/release-keys \
    DeviceProduct=coral

$(call inherit-product, vendor/google/coral/coral-vendor.mk)

# Standalone plugins own the enhanced Motion Sense and 3D Face Unlock packages.
# Keep these AFTER every base device/vendor product.
$(call inherit-product, vendor/hecker/floral/motionsense/motionsense.mk)
$(call inherit-product, vendor/hecker/floral/faceid/faceid.mk)

# Use the original signed Camera package tested on Infinity-X Coral.
$(call inherit-product, vendor/hecker/floral/suite/prebuilts/google-camera/camera.mk)
PRODUCT_PACKAGES := $(filter-out Aperture Camera2,$(PRODUCT_PACKAGES))

# Preserve bounded startup evidence on userdebug/eng builds.
include vendor/hecker/floral/suite/rom/boot_diagnostics/boot_capture.mk

$(call inherit-product, vendor/hecker/floral/suite/rom/common.mk)

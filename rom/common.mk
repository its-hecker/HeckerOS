# SPDX-License-Identifier: Apache-2.0
# Native Pixel hardware identity remains available for framework/app compatibility.
IS_OFFICIAL := true
HECKER_BUILD_TYPE ?= OFFICIAL
PRODUCT_PRODUCT_PROPERTIES += \
    pixel.oslo.allowed_override=1 \
    ro.heckeros.version=2.0 \
    ro.heckeros.build_type=$(HECKER_BUILD_TYPE) \
    ro.heckeros.maintainer=Hecker \
    ro.heckeros.device=$(PRODUCT_DEVICE)

$(call inherit-product, vendor/hecker/floral/suite/suite.mk)
# Ahead-of-time compile only core, frequently visible UI; keep ordinary apps
# profile-guided to avoid an oversized system image.
PRODUCT_DEXPREOPT_SPEED_APPS += SystemUI HeckerLauncher HeckerSuite

$(call inherit-product, vendor/hecker/floral/suite/themes/themes.mk)
$(call inherit-product, vendor/hecker/floral/suite/fonts/google-sans-cyrillic/fonts.mk)
# Ignore the removed weather service, including stale source directories.
PRODUCT_SOURCE_ROOT_DIRS += -packages/services/OmniJaws
PRODUCT_PACKAGES := $(filter-out OmniJaws,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES += HeckerSystemUIOverlay \
    HeckerStatusBarHeightCompact \
    HeckerStatusBarHeightBalanced \
    HeckerStatusBarHeightTall
# Retire the ineffective gesture-pill overlay; preserve Android's native gesture/inset geometry.
PRODUCT_PACKAGES += HeckerEuiccPermissions
# Native window blur still observes SurfaceFlinger, thermal and power-saver capability gates.
PRODUCT_PRODUCT_PROPERTIES += ro.surface_flinger.supports_background_blur?=1

include vendor/hecker/floral/suite/rom/adb/development_adb.mk

# SPDX-License-Identifier: Apache-2.0
# Native Pixel hardware identity remains available for framework/app compatibility.
IS_OFFICIAL := true
HECKER_BUILD_TYPE ?= OFFICIAL
PRODUCT_PRODUCT_PROPERTIES += \
    pixel.oslo.allowed_override=1 \
    ro.heckeros.version=1.0 \
    ro.heckeros.build_type=$(HECKER_BUILD_TYPE) \
    ro.heckeros.maintainer=Hecker \
    ro.heckeros.device=$(PRODUCT_DEVICE)

$(call inherit-product, vendor/hecker/floral/suite/suite.mk)
$(call inherit-product, vendor/hecker/floral/suite/themes/themes.mk)
# Ignore the removed weather service, including stale source directories.
PRODUCT_SOURCE_ROOT_DIRS += -packages/services/OmniJaws
PRODUCT_PACKAGES := $(filter-out OmniJaws,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES += HeckerSystemUIOverlay

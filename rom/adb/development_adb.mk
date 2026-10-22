# SPDX-License-Identifier: Apache-2.0
# Temporary development default. Set HECKER_DEV_USB_ADB=false for the final build.
HECKER_DEV_USB_ADB ?= $(if $(filter userdebug eng,$(TARGET_BUILD_VARIANT)),true,false)

# Own one product-partition assignment; init loads these after system/vendor defaults.
PRODUCT_PRODUCT_PROPERTIES := $(filter-out \
    ro.adb.secure=% ro.adb.secure?=% \
    ro.heckeros.dev_usb_adb=% ro.heckeros.dev_usb_adb?=%, \
    $(PRODUCT_PRODUCT_PROPERTIES))

ifeq ($(strip $(HECKER_DEV_USB_ADB)),true)
ifeq ($(filter userdebug eng,$(TARGET_BUILD_VARIANT)),)
$(error HECKER_DEV_USB_ADB=true requires a userdebug or eng build)
endif
PRODUCT_PRODUCT_PROPERTIES := $(filter-out \
    persist.sys.usb.config=% persist.sys.usb.config?=%, \
    $(PRODUCT_PRODUCT_PROPERTIES))
PRODUCT_PRODUCT_PROPERTIES += \
    ro.heckeros.dev_usb_adb=1 \
    ro.adb.secure=0 \
    persist.sys.usb.config=adb
else ifeq ($(strip $(HECKER_DEV_USB_ADB)),false)
# Explicitly restore authentication, including on eng builds whose base may disable it.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.heckeros.dev_usb_adb=0 \
    ro.adb.secure=1
else
$(error HECKER_DEV_USB_ADB must be true or false)
endif

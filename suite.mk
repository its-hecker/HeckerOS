# SPDX-License-Identifier: Apache-2.0
# The release-config discovery pass runs before Android defines my-dir.
HECKER_SUITE_PATH := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
PRODUCT_PACKAGES := $(filter-out HeckerLauncher HeckerLauncherRecentsOverlay Launcher3QuickStep Launcher3,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES += HeckerSuite HeckerLauncher HeckerNetLabController HeckerVpnController
PRODUCT_RELEASE_CONFIG_MAPS += $(HECKER_SUITE_PATH)/release/release_config_map.textproto
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.heckeros.suite_revision=$(shell git -C $(HECKER_SUITE_PATH) rev-parse HEAD) \
    ro.heckeros.manifest_revision=$(shell git -C .repo/local_manifests rev-parse HEAD)

# Both HeckerOS Floral products own feature inheritance and native Android 17 flags.
# Lunch: hecker_<coral|flame>-hecker_suite-userdebug

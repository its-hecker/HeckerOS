# SPDX-License-Identifier: Apache-2.0
# The release-config discovery pass runs before Android defines my-dir.
HECKER_SUITE_PATH := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
PRODUCT_PACKAGES := $(filter-out HeckerLauncher HeckerLauncherRecentsOverlay Launcher3QuickStep Launcher3,$(PRODUCT_PACKAGES))
PRODUCT_PACKAGES += HeckerSuite
PRODUCT_RELEASE_CONFIG_MAPS += $(HECKER_SUITE_PATH)/release/release_config_map.textproto

# The HeckerOS coral product owns feature inheritance and native Android 17 flags.
# Lunch: hecker_coral-hecker_suite-userdebug

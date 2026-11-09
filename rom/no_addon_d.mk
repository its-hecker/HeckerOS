# SPDX-License-Identifier: Apache-2.0
# Run after all product inheritance. Keep bundled packages and OTA tooling;
# omit the recovery add-on preservation scripts from the installed images.
hecker_addon_d_roots := $(addsuffix /addon.d,$(TARGET_COPY_OUT_SYSTEM) $(TARGET_COPY_OUT_SYSTEM_EXT) $(TARGET_COPY_OUT_PRODUCT) $(TARGET_COPY_OUT_VENDOR))
hecker_addon_d_patterns := $(hecker_addon_d_roots) $(addsuffix /%,$(hecker_addon_d_roots))
PRODUCT_COPY_FILES := $(foreach entry,$(PRODUCT_COPY_FILES),$(if $(filter $(hecker_addon_d_patterns),$(word 2,$(subst :, ,$(entry)))),,$(entry)))
# Known script modules. A new module installing here must also fail the output check.
PRODUCT_PACKAGES := $(filter-out addon.d 50-cm.sh 50-lineage.sh 50-pixel.sh 50-pixelos.sh 50-hecker.sh,$(PRODUCT_PACKAGES))

# SPDX-License-Identifier: Apache-2.0
# Both Floral products inherit the same HeckerOS features and native flags.
PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/hecker_coral.mk \
    $(LOCAL_DIR)/hecker_flame.mk
COMMON_LUNCH_CHOICES := \
    hecker_coral-hecker_suite-userdebug \
    hecker_flame-hecker_suite-userdebug

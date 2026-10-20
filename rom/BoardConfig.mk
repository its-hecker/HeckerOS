# SPDX-License-Identifier: Apache-2.0
# All ROM feature policy stays here; the device tree carries one OS include.
-include vendor/lunaris/dolby/BoardConfigDolby.mk
include vendor/hecker/floral/motionsense/BoardConfigMotionSense.mk
include vendor/hecker/floral/faceid/BoardConfigFaceID.mk
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += vendor/hecker/floral/suite/rom/sepolicy/private

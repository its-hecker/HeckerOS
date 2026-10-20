# Original Google Camera 8.7.250.494820638.44, with its signed feature splits.
# Defined in vendor/hecker/floral/suite/prebuilts/google-camera for the PixelOS Coral product.
PRODUCT_PACKAGES += \
    GoogleCameraCoral

PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/prebuilts/google-camera/default-permissions-google-camera.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/default-permissions/default-permissions-google-camera-coral.xml \
    vendor/hecker/floral/suite/prebuilts/google-camera/google-camera-coral.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/google-camera-coral.xml

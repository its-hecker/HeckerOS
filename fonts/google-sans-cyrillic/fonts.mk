# SPDX-License-Identifier: Apache-2.0
# Only Cyrillic glyphs are added; existing Pixel Latin/clock fonts retain ownership.
PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/fonts/google-sans-cyrillic/HeckerGoogleSansCyrillic-Regular.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/HeckerGoogleSansCyrillic-Regular.ttf \
    vendor/hecker/floral/suite/fonts/google-sans-cyrillic/HeckerGoogleSansCyrillic-Italic.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/HeckerGoogleSansCyrillic-Italic.ttf \
    vendor/hecker/floral/suite/fonts/google-sans-cyrillic/OFL.txt:$(TARGET_COPY_OUT_PRODUCT)/etc/HeckerGoogleSansCyrillic-OFL.txt

# SPDX-License-Identifier: Apache-2.0
# Font assets are optional and must be redistribution-cleared before release.
# The pinned Pixel OEM font configuration is never replaced.

ifneq ($(wildcard vendor/hecker/floral/suite/fonts/emoji/prebuilt/LunarisIosEmoji.ttf),)
PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/fonts/emoji/prebuilt/LunarisIosEmoji.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/LunarisIosEmoji.ttf \
    vendor/hecker/floral/suite/fonts/emoji/fonts_customization_emoji_ios.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/fonts_customization_emoji_ios.xml
endif

ifneq ($(wildcard vendor/hecker/floral/suite/fonts/emoji/prebuilt/SamsungColorEmoji.ttf),)
PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/fonts/emoji/prebuilt/SamsungColorEmoji.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/SamsungColorEmoji.ttf \
    vendor/hecker/floral/suite/fonts/emoji/fonts_customization_emoji_samsung.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/fonts_customization_emoji_samsung.xml
endif

ifneq ($(wildcard vendor/hecker/floral/suite/fonts/emoji/prebuilt/SwiftUIEmoji.ttf),)
PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/fonts/emoji/prebuilt/SwiftUIEmoji.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/SwiftUIEmoji.ttf \
    vendor/hecker/floral/suite/fonts/emoji/fonts_customization_emoji_swiftui.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/fonts_customization_emoji_swiftui.xml
endif

ifneq ($(wildcard vendor/hecker/floral/suite/fonts/emoji/prebuilt/FacebookEmoji.ttf),)
PRODUCT_COPY_FILES += \
    vendor/hecker/floral/suite/fonts/emoji/prebuilt/FacebookEmoji.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/FacebookEmoji.ttf \
    vendor/hecker/floral/suite/fonts/emoji/fonts_customization_emoji_facebook.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/fonts_customization_emoji_facebook.xml
endif

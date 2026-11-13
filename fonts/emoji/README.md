# Custom Emojis — optional font packs

HeckerLab shows Stock, iOS, Samsung, SwiftUI and Facebook emoji options. Stock
works without any extra font. Alternative options are disabled until their font
and overlay are present. The four non-stock fonts are not distributed here:
verify each font's distribution rights before including it in a published ROM.

For local test builds only, place appropriately licensed fonts in
`fonts/emoji/prebuilt/` with these filenames:

- `LunarisIosEmoji.ttf`
- `SamsungColorEmoji.ttf`
- `SwiftUIEmoji.ttf`
- `FacebookEmoji.ttf`

The `emoji.mk` recipe copies a matching font+XML pair only when the font exists.
The companion `heckeros_frameworks_base` change adds an independent font
customization layer without overwriting Pixel/Hecker's boot-critical Google
Sans font customization. A selection changes `persist.sys.ax_emoji_style` and
takes effect on reboot. Verify SELinux property write permissions on device.
Test zygote boot, missing fonts, SystemUI, Settings and keyboard previews.

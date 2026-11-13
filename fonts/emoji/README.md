# Custom Emojis — Evolution X font integration

HeckerLab already has the five-entry emoji picker and the framework parser
support. Stock Android is always available. The optional four entries have
their own previews and system-wide reboot-applied font fallback. Their matching
XML resources are included in this source; the font binaries themselves are
**not** checked into HeckerOS.

The exact four corresponding binary files exist in Evolution X's
`Evolution-X/vendor_extras` source, revision
`1a97709e05739e0d4b05b37583a01eaa4df7cac8`. No clear font-specific
redistribution grant was identified there. Public availability does not
establish a right to repackage these fonts in ROM releases. Obtain and review
the applicable rights before distributing builds containing them.

## Explicit local integration

With a synced ROM source, run from the Android source root:

```bash
python3 vendor/hecker/floral/suite/tools/import_evolution_emoji.py --fetch
python3 vendor/hecker/floral/suite/tools/import_evolution_emoji.py --check
```

Alternatively use `--source-dir /path/to/appropriately-licensed-fonts`
instead of `--fetch`. Both modes require all four files and validate each
file's exact byte size and Git blob SHA-1 against the pinned Evolution X commit.

The regular Floral build also supports opt-in fetching by setting
`HECKER_IMPORT_EVOLUTION_EMOJI=1` in the environment. Its Suite prepare
stage runs the importer before Make evaluates `emoji.mk`.

The importer stores assets only in ignored
`fonts/emoji/prebuilt/`, outside Git history. A corrupt or incomplete file is
never installed. `emoji.mk` copies each matching font and XML to
`/product/fonts` and `/product/etc` when the font exists; the framework
checks for both files and falls back to stock if absent. This does not modify
the existing Pixel/Hecker Google Sans customization.

### Files
- `LunarisIosEmoji.ttf` — iOS
- `SamsungColorEmoji.ttf` — Samsung
- `SwiftUIEmoji.ttf` — SwiftUI
- `FacebookEmoji.ttf` — Facebook

**Validation needed before a public ROM release:** check redistribution
permissions; perform a complete coral/flame build and boot; verify Settings,
SystemUI, Gboard, previews and font fallback; verify SELinux permits setting
`persist.sys.ax_emoji_style`; verify reboot persistence.

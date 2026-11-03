# Google Sans Cyrillic

Google Sans provides the requested Product Sans-style Russian typography. The
official Google Fonts files are licensed under the SIL Open Font License 1.1.
See `OFL.txt` and `ORIGIN.json` for attribution and immutable input/output hashes.

These variable-font subsets contain only Cyrillic code points
U+0400–052F, U+2DE0–2DFF and U+A640–A69F. The complete Russian alphabet, including
Ё/ё, is retained in normal and italic styles, with the original layout features.
Latin text, digits, clock designs, emoji and other scripts retain their existing
Pixel font families. These are Google Sans subsets, not redistributed Product Sans.

The preparation adapter adds a Cyrillic family before each named text family's
existing faces. Existing named clock families and aliases are preserved. It
accepts the pinned Pixel font configuration, its own exact output, or the exact
previously published output for upgrade/revert. It runs idempotently and restores
the original bytes when reverted. Unfamiliar edits are preserved and rejected.

Android 17 forbids `lang`, `variant` and `ignore` on named families and also
forbids `name` on families inside a named `family-list`. The Cyrillic subsets'
glyph coverage selects the script without a `lang` attribute. The old adapter's
`lang="und-Cyrl"` caused Zygote to fail during Typeface preload; preparation now
migrates that exact configuration automatically. Host tests run the pinned
Android font parsers with host data/XML adapters to cover this boot failure.

The final built-output check invokes the Suite's `tools/google_sans_cyrillic.py`
with `--product-out out/target/product/coral` from the Android source root to
verify the installed XML and both font payloads.

With `fonttools==4.61.1`, `python3 tools/build_cyrillic_fonts.py` reproduces both
payloads byte-for-byte. Source fonts are fetched only for this maintainer check;
normal ROM preparation uses the bundled, checksum-verified subsets.

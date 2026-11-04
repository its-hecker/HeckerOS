# Native XMorphClock styles — 4 November 2026

Hecker Suite → Lock screen → Custom clock now includes **XMorphClock styles**.
The gallery offers all 86 clock/date presets from
[Mahmud0808/XMorphClock](https://github.com/Mahmud0808/XMorphClock) at
`a88005252636f9ac89cd4b8727ee04a57dae2224`, with their original layouts, renderer
and 47 font resources. Designs include expressive, artistic, playful, analog,
elegant, classic, graphic, digital and word faces.

Choose large, small or AOD preview mode, then tap a face to apply it. The gallery
recycles its clock Views and disables preview animation/prewarming. Selection is
stored by immutable asset filename in the foreground user's Settings.Secure;
clock family 6 is reserved for XMorph, preserving existing IDs 0–5. Returning to
the parent page refreshes its current selection.

SystemUI links `HeckerXMorphClock` as a native Android library and hosts the
upstream ClockFaceView in the existing lockscreen scene. The existing doze amount
drives the face's sleep/AOD progress. The original minute-change and face morphing
remain; the host disables animation while hidden, in AOD, in Battery Saver, or
with reduced motion. The bounded font cache and cancellable prewarming prevent
unbounded work when switching faces. Clock/date data uses native time, locale and
12/24-hour settings. Static wallpaper loading and glass precomputation run on a
worker; results are scoped to their user and selected preset.

The shared clock depth modifier masks the XMorph clock View in an isolated layer.
The upstream depth overlay is not instantiated. Original clock artwork and fonts
are packaged with the ROM; running these styles needs no root, Xposed, module APK,
online provider or runtime asset download. The custom-clock switch selects this
family while the independently implemented stock/plugin depth support remains.

The scope is the 86 clock/date designs. The module's separate editor and widget,
weather and Xposed infrastructure are not part of this native clock library.

## Source and notices

The renderer, model and preset source retain upstream GPL-3.0-or-later terms and
DrDisagree's credit. Native adaptation notes are in
`third_party/xmorphclock/PORTING.md`; the pinned file hashes are in
`third_party/xmorphclock/upstream.lock.json`. GPL and font notices ship in APK assets
and Soong license metadata. The gallery credits the original author.

The source adapters register the exact preceding Ambient/clock-depth copies for
safe in-place upgrades. The new SystemUI static dependency is guarded against the
manifest-pinned native Android.bp.

## Validation

- Native Kotlin library and all 47 font resources compile with locked SDK inputs.
- Real APK packaging contains all 86 presets, fonts and the GPL notice.
- The production JSON mapper/catalog passes round trips, layer references and
  selection tests for 86 faces and 344 large/small/AOD states.
- Original preset, font and font-notice bytes match the pinned upstream hashes.
- Complete Suite Java/resource/AIDL and independent Settings-dashboard compilation
  pass, including the gallery's real Kotlin API calls.
- SystemUI Java/Compose checks compile the native host, depth modifier and renderer
  together; guarded preparation, source, Russian/Chinese and prior depth tests pass.

Full SystemUI/ROM Soong compilation, phone layout/performance and visual checks
remain pending. On Coral, check all families in both clock sizes and AOD, glass
wallpaper alignment, analog/word faces, depth overlap, disabled animations, locale,
user switches and returning to the six original clocks or stock/plugin clocks.

The existing `fix/coral-ui-compatibility-20261102` source and local-manifest
branches include this work alongside the Settings crash and native depth fixes.
Keep `out/` and rebuild incrementally using the current RBE/default-jobs command.

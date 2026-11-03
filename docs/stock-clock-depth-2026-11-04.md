# Native stock and plugin clock depth — 4 November 2026

Depth is now independent of the Hecker custom-clock switch. The shared native
clock element factory attaches the subject mask to `Clock.Small` and `Clock.Large`,
for both movable plugin elements and ordinary elements. The selected stock or
installed SystemUI clock plugin continues rendering its own face, layout and
animations. The six Hecker clock styles retain their existing Canvas path.

The mask clears subject-shaped pixels from an isolated clock layer, revealing
the aligned depth backdrop behind it. It does not draw a foreground over the
entire lockscreen. Smartspace/date-weather elements, notifications, media,
shortcuts, lock icon and other controls remain outside that layer.

Clock layout coordinates are read at draw time. A root-to-local affine transform
keeps the wallpaper subject registered while the native clock moves, scales,
rotates or receives burn-in offsets. The mask is scoped to a lockscreen depth
composition, so a standalone clock preview cannot receive a stale global mask.
No extra input surface or clock-specific animation loop is introduced.

AOD keeps the unmasked native clock and does not draw depth wallpaper. Disabled
depth, an unavailable layer pair or an invalid placement leaves native rendering
in place. The existing reduced-motion and Battery Saver policy still governs
the depth wake animation.

All three former clock-selection overrides are removed: the depth/automatic
extraction toggles, manual layer import, and current-wallpaper extraction no
longer enable `hecker_custom_lockscreen_clock`. The existing native plugin
selection and Hecker style value are preserved. Plugin discovery, trust rules
and installation are unchanged.

## Use

1. For a stock/plugin clock, turn off **Custom clock** in Hecker Suite's lockscreen
   page and select the desired native style in Wallpaper & style.
2. Enable **Lock screen depth wallpaper**, then import layers or extract the
   current static lockscreen wallpaper.
3. Position the wallpaper subject so that it intersects the chosen clock. The
   subject only hides clock pixels where their positions overlap.

Leave Custom clock enabled to continue using a Hecker clock with depth.
This supports clock providers rendered through SystemUI's native small/large
clock elements; external lockscreen replacement apps are outside this renderer.

## Validation

- Compile the production SystemUI depth helpers and exact native factory binding
  with the existing locked Android/Compose inputs.
- Compile all Suite Java, native Settings dashboard code, resources and generated
  AIDL with the existing SDK checker.
- Execute 5,184 native mask registration cases across scale, rotation, translation
  and reflection, plus invalid/singular geometry guards.
- Execute the production current-wallpaper pipeline with stock/plugin/Hecker
  selections, fresh/forced/cached extraction, and independent Home preservation.
- Exercise the real pinned native factory adapters for fresh apply, repeat and
  exact reversion. Upgrade the exact published Coral depth copies in place,
  preserve unrelated native edits and reject unknown private adapter changes.
- Retain the preparation, provider-access, layout and translation checks.

These checks pass on the host. Full SystemUI/ROM Soong compilation and physical
Coral testing remain pending. After rebuilding, check native/plugin styles with
large and small clocks, notifications and media, AOD/wake, clock changes, user
switches, reduced motion and both import routes.

The existing `fix/coral-ui-compatibility-20261102` source and local-manifest
branches carry this change together with the Settings homepage crash fix.
Keep `out/` and rebuild incrementally with the existing RBE/default-jobs command.

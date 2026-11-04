# Native HeckerOS port

Upstream: [XMorphClock by DrDisagree](https://github.com/Mahmud0808/XMorphClock),
revision `a88005252636f9ac89cd4b8727ee04a57dae2224`, GPL-3.0-or-later.
The source/asset manifest is `upstream.lock.json`. All 86 original clock presets,
47 font resources and accompanying font notices are retained.

This module builds the Android View renderer and models as `HeckerXMorphClock`.
It is statically linked into SystemUI and the existing Hecker Suite feature app.
Resource names are prefixed `xmorph_` to avoid host collisions; the original
resource package is retained. Only strings used by the renderer are imported.

Adaptations to upstream code:

- ClockFaceView resolves composition group IDs locally, avoiding the full editor
  dependency. The regex is compiled once.
- The native host controls animation activity. Previews, AOD, hidden keyguard,
  Battery Saver and disabled animations settle without frame loops. Existing
  minute-change, analog and outline renderers remain upstream implementations.
- Gallery views skip speculative font prewarming. Typeface variants use a bounded
  512-entry cache, and obsolete warm requests cancel when the selected face changes.
- Font loading holds the application context.

The native catalog uses immutable asset filenames as selection IDs. It rejects
an incomplete collection and falls back to the first packaged preset for an
unknown ID. HeckerOS stores the choice in per-user Settings.Secure and assigns
clock family 6; existing families 0–5 retain their IDs. The native AndroidView
hosts the renderer, and the shared depth mask clips only its clock surface.

The native wallpaper loader prepares a bounded static wallpaper and glass blur
on a worker thread. Async results are checked against the active user and preset.
The SystemUI host supplies native doze progress and rebuilds the view on a user
switch. The renderer handles the time/date and system 12/24-hour preference.

The scope is the upstream clock catalog and its renderer. XMorph's separate
editor, composition widgets, weather services and module infrastructure are not
built. Those features are not required by these 86 clock/date presets.

Full licenses are packaged in `assets/xmorph_license` and `assets/xmorph_licenses`;
the Soong license metadata retains the GPL and font notices. Corresponding native
source and build wiring are published in the HeckerOS repository and manifest.

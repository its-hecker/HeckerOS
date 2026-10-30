# Media glow and continuous handoffs — Coral

The supplied player reference calls for drifting blue/lavender light beneath the
controls. Artwork and transport crossfades alone did not provide that effect.
The shared SystemUI player now draws three low-cost radial gradients and a soft
bottom rim, with sharp text, artwork and hit targets above them. This renderer
serves the lock screen, Quick QS, full Hecker QS and the compact full One UI QS
row. Both expanded layouts retain their existing arrangement and sliders.

The glow advances only during playback while Living Flow motion is enabled,
the screen is awake, power saver is off, animator scale permits motion and the
native scene lifecycle is resumed. Pausing leaves a subdued static glow. Hidden
or screen-off players have no glow frame loop. This is decorative light, not an
audio visualizer; it does not capture audio or request recording permissions.
Seek and metadata updates do not restart its phase.

## Quick QS to either full player

The actual native QuickQuickSettings adapter now includes the active player in
both the scene-container and Compose-fragment configurations. This also corrects
the earlier preview, which showed a Quick QS player that the native adapter had
still omitted. Native resumption and lock-screen fallback code are retained;
the active QQS player uses the real media session rather than demonstration data.

One scene element identifies the player at both ends. Android interpolates its
measured bounds and two shared values for square/compact layout weights. The
same production `MediaCardGeometry` places artwork, titles, seek and controls
during measurement. No independent expansion timer or outgoing/incoming card
opacity switch is used. Drag cancellation samples the same geometry in reverse.
The tile trays, headers and sliders fade independently so a fading dashboard
ancestor cannot hide the shared media surface. Existing native shade backdrop
blur remains unchanged across expansion in both styles.

The full One UI row now uses the shared renderer, including artwork/title/button
transitions and glow. It keeps its compact arrangement, with play/pause and
media output. Controls that disappear during the morph are not placed at zero
alpha, so they cannot intercept taps. The playback target remains at least 44 dp.

## Lock clock to Home

The native lockscreen-to-Gone scene now supplies its own dismissal progress to
the existing live clock. Tall hour/minute groups converge toward the horizontal
Home geometry; the date fades during travel so it cannot overlap the moving
digit groups. The lock depth scene stays opaque until
the digits are near that destination, then hands over to the independent Home
wallpaper. Native scene progress also handles interrupted/reversed transitions.
A 500 ms ease-in/out curve replaces the fast-start dismissal curve for this path.
The approved AOD-to-lock reveal is unchanged.

Launcher no longer starts an independent 520 ms AOD-sized clock reveal on
`ACTION_USER_PRESENT`. Its Home clock is already settled underneath the native
unlock transition. The bounded lock renderer targets the default 160 dp Home
clock allocation; physical alignment with all grid, density, custom position,
small-clock and authentication configurations still needs device acceptance.
This does not create a cross-process Android shared element or bypass keyguard.

## Validation

- 135 host tests, including 72,072 production media layout/reversal samples at three font scales and
  35,514 bounded clock/AOD/Home/dismissal frames.
- Locked Java/Kotlin/Compose compilation, real AndroidX lifecycle dependencies,
  drawable/string compilation and the new native QS scope entry point.
- Native adapters against 113 immutable inputs, upgrade from the previous Living
  Flow release, idempotent application and exact reversion; Launcher compilation.
- Source-rendered 60 fps demonstration uses the production Java clock and media
  geometry; the host gradients and Android layout/font rendering are approximate.

Full SystemUI/ROM compilation and a Coral phone are still required to establish
frame pacing and actual window/keyguard handoff behavior. Check both QS styles,
slow drag/reversal, paused/resumed playback, seek, output routing, inactive media,
large fonts, RTL, power saver, reduced motion, notification/PIN/FaceID unlock,
AOD wake/sleep and user switching. No Flame manifests or hardware pins are changed.

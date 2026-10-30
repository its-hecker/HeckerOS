# Living Flow reference polish — Coral

This extends the published status-bar branch. The four approved reference frames
in the conversation are the visual target. The existing Hecker Suite navigation,
cards and About UI are retained. This branch targets Coral; Flame manifests,
product file, device variants and hardware pins are unchanged.

| Area | Implementation and controls |
| --- | --- |
| Lock clock | Existing tall/Living Tall styles now use the preview's bundled DejaVu faces and font-space proportions. Size, position, colour, opacity and native small-clock behavior remain available. |
| AOD ↔ lock | Every callback uses native linear doze progress. One smooth wake fraction drives the existing clock/wallpaper reveal. Subject masking now fades with progress, including reversal, rather than appearing fully opaque on the first wake frame. |
| Home depth | New **Caracal Harbor** background and transparent subject, separate from the dawn lock pair. Only an unchanged, previously owned default Home migrates automatically. Custom/live Home wallpapers and disabled depth are respected. |
| Home imports | **Hecker Suite → Lock screen → Home depth wallpaper** imports a Home-only prepared pair. Home and lock keep independent pair IDs and retain each other's active files. **Apply defaults** restores both defaults. |
| Search and icons | **Launcher Studio → Appearance → Apply Living Flow preview look** selects outline glass icon plates and the compact “Search apps” pill. Individual icon/search choices retain the older looks and Google search option. The 24 dp visual pill retains a 48 dp touch target. |
| Hecker QS | Existing Living Flow connectivity pills, round controls, upright brightness/volume sliders and percentages are retained. The media tile uses neutral translucent glass with thumbnail artwork. |
| Battery | **Hecker Suite → Themes → Battery style → Living Flow battery** adds a horizontal outlined shell, inset level fill, level colours and the existing optional charging ripple. Other battery choices remain available. |
| Media | Shared lock/Quick QS/Hecker QS/One UI QS renderer adds the reference blue/lavender glow, artwork/title/transport transitions and native shared bounds through expansion. Full One UI retains its compact row. See [media glow and handoffs](media-glow-handoff.md). |
| QS blur | The native backdrop now uses one shade-strength floor through QQS → full QS for both layouts. Brightness mirror, unlock, app launch, scrim, window-blur support and display gates remain in the native calculation. |
| Folders | Existing native clipped folder frosting and bounded settling animation remain. The compact search pill is excluded from full-height glass background replacement. |

Media motion follows Living Flow, animator-scale, screen-awake and power-save
settings. The progress timer remains at one second. Glow frames run only during
playback in a resumed visible scene; paused/hidden/screen-off players are static. Original session capability checks, native media resumption
and lock-screen visibility decisions are retained.

## Assets and licensing

The lock artwork is unchanged. New Home layers are in
`app/assets/wallpapers/caracal-harbor/`, with dimensions and source/output hashes
in `ORIGIN.json`. They were created using built-in image generation: a quiet
moonlit teal harbor/balcony background, plus an independently generated transparent
adult caracal heroine in a silver/navy closed coat holding a book. They are
normalized to the same 752 × 1600 crop; the foreground retains alpha.

`fonts/living-flow/` contains subsets of the DejaVu Sans regular/bold preview
faces. Original font notices are bundled in Launcher and SystemUI resources and
declared in the source licensing metadata. Android supplies missing-script
fallback. No OPPO artwork, font or executable is shipped.

## Validation and phone acceptance

Validated with 134 Python host tests and the existing Java/C++ regressions;
locked SDK app/Settings Java, resources and AIDL; QS Compose/Java; the new battery
resource pack; 82 integration tests; and the native source upgrade check.
The latter checks 113 immutable input files, repeat application, exact reversion,
Launcher Java/font resources, native insets and 404 blur handoffs with suppression
gates. Clock tests cover 17,496 geometry frames and 10,001 wake/reversal samples.
Home tests execute the actual installer/provider/controller with native API
facades, including per-user access and custom wallpaper ownership.

A full Soong/ROM build and Coral phone tests remain required. Check AOD minute
changes and burn-in movement, fast wake/sleep reversal, lock notifications,
PIN/FaceID unlock, separate Home/lock imports, 60/90 Hz frame pacing, both QS
styles in QQS/full/split views, media seeking/resumption, low brightness,
reduced motion and power-save. These checks do not establish pixel-perfect phone
rendering or the behavior of OPPO's proprietary engine.

The matching upstream source review is in
[coral-upstream-review-2026-10-30.md](coral-upstream-review-2026-10-30.md).

# HeckerOS device feedback — 1 November 2026

Prepared from `feat/living-flow-polish-20261030` at
`897fdc364865257fba2560b1968ef235c45e9c70`.
Working branch: `fix/device-ui-feedback-20261101`.

This is a source implementation and review package. It is not a new ROM image
or a claim that the reported issues have passed testing on the phone. The
screenshots describe the before state. A full Android/Soong build and a flashed
Coral test are the next acceptance steps. The changes are shared source, but
Flame's independently pinned source graph has not been advanced by this review.

| Reported issue or request | Implemented source change |
| --- | --- |
| Clipped Home search bar | Remove the compact background's vertical inset and the label's inherited padding. Center the label inside the full measured search row. Retain the existing navigation/dock reservation. |
| Search disable option hard to find | Add **Show home screen search bar** directly to Launcher Studio → Home screen. It remains available under Universal search; long-pressing the bar opens that section. |
| Custom icons on Home but original icons in the app menu | Add **Custom icons in app drawer** in Icon Studio. Turning it off bypasses custom packs, per-app overrides, glass and shape processing for drawer, prediction and search-result icons. Home and folder icons keep their configuration. |
| Camera icon | Replace the built-in glass camera mask with a clean outlined body, an open lens and a small indicator. External icon-pack artwork remains owned by the selected pack. |
| Launcher search UI | Rounded search field, proper back arrow, clear control, search settings shortcut, recycled result rows, separate title/subtitle typography, exact/prefix ranking and keyboard Search launching the first result. Search still uses accessible Android profiles and LauncherApps. |
| Home depth clock too high | Lower the shared Home clock target by up to 18 dp while keeping short layouts bounded. The travelling lock clock uses the same lower target. |
| GameSpace default Home icon | Seed one ordinary movable shortcut while Launcher3 creates a fresh default database. Find an empty cell without overlapping widgets. Reboots and existing layouts do not repin it. |
| Custom lockscreen clocks do not apply | Choosing a style enables the custom renderer; add **Apply selected clock** so applying the already-selected style also works. The enable switch reflects the result. |
| Lockscreen and ambient lyrics | Use explicit foreground-user media-session APIs; watch all candidate players, including initially paused ones. Support embedded synchronized lyrics and fallback title/artist metadata. Normalize common video/lyric suffixes and Topic/featured artist metadata for lookup while retaining duration checks. Hide paused lines. The existing doze presentation remains a single line. |
| Status bar lyrics | The same repaired current-user provider serves the independently enabled status bar surface, with one worker/cache and stale-result rejection. |
| Dual status bar options | Dedicated **Status bar layout and lyrics** destination exposes existing height, layout, gap, icon, lyric width/font/alignment/color/transition controls. Add first/second-row placement for network speed. |
| Misaligned network speed | Place the real NetworkTraffic view in the chosen measured row after its native parents are positioned. Reserve its horizontal area when laying out the adjacent date. |
| Living Flow battery borders | Thin the perimeter from 1.5 to 0.85 model units, reduce its opacity, and bring the fill closer to the frame. Keep the actual level and charging animation. |
| Misaligned Settings cards | Apply the final Hecker styling after Android's rounded/highlight adapter has set its row background. Keep one grouped-card background instead of overlapping native and Hecker backgrounds. Preserve embedded-layout selection highlighting. |
| Google account icon | Use a dedicated account glyph for the injected account category instead of tinting the account image into a solid disc. |
| Different backup/copy-data row | Normalize injected rows through the same layout and final adapter binding as the main categories. The injected preference previously escaped the ordinary HomepagePreference helper. |
| About phone too small | Fill at least the available list viewport, remove the post-bind enclosing-card background, and add real total memory, available/total storage, Android security patch and build data. Keep native rename, software, device and legal destinations. |
| Duplicate main Settings destinations | Remove the separate Themes, Home screen, and combined Lock screen/AOD shortcuts. The native wallpaper destination remains. Customization stays in Hecker Suite. |
| Suite redesign | Match the main Settings black/charcoal palette, grouped-card radius, headings, switch geometry, choice controls and sliders across native Suite pages. Retain the Lottie animations. |
| Auto depth wallpaper | Adapt Lunaris's local coarse-mask pipeline. A bounded JobScheduler job prepares a new lockscreen pair after a wallpaper change. Match results to the wallpaper ID, cancel stale work, and keep the old pair hidden during replacement. No phone-side model download or cloud upload is required. |
| Manual depth option | Keep the two-layer importer and add a single-photo extraction/subject preview flow. Explicit manual lockscreen application disables automatic mode so it remains the user's selected result. Home-only import leaves the lockscreen mode alone. |
| QS media appears late | Resolve the initial session before the media card's first layout, so the shared scene element has geometry immediately. Share the initial query briefly and retain current-user checks. |
| Media icon lingers on Home | Fade the actual shared media content early in the native transition to Gone, including overlay dismissal. Do not add another independent animation timer. |
| Hecker QS player too large | Reduce the square player from 178 to 154 dp at normal font size. Preserve larger-font expansion and native transport hit targets. |
| Slider percentages look heavy | Remove the dark percentage badges. Use smaller, medium-weight labels with fill-aware contrast and retain upright labels for vertical controls. |
| Motion gesture hides bar/creature | Retain music eligibility briefly during buffering/skip metadata gaps, keep the skip capsule visible through the bounded gap, and clean expired session classifications. |
| Motion bar too wide | Reduce its window from 148 to 124 dp. Keep the separate decorative window non-touchable. |
| Play companion animation | Remove the rope-length bounce and blend between registered welcome poses. Keep the pause goodbye choreography. |
| Lock-to-Home animation too fast | Increase the native clock handoff from 500 to 650 ms. Preserve reduced-motion handling, cancellation and the shared digit path. |
| App jumps when returning Home | Keep Home and fallback dismissal bounds stable, avoiding an additional shrink/oversized Home scale during the handoff. Pixel Launcher continues to own its remote Recents/Quickstep path. |

The layout fixes address concrete differences in the source: Settings had two
competing row backgrounds, the injected backup row used a different preference
path, compact search carried excess insets/padding, and selecting a clock style
did not enable the renderer. These findings are not an on-device after-test.

**Controls to test first**

- Launcher Studio → Home screen → Show home screen search bar.
- Launcher Studio → Appearance → Icon Studio → Custom icons in app drawer.
- Hecker Suite → Lock Screen → select a style → Apply selected clock.
- Hecker Suite → Lock Screen → Auto-extract wallpaper subject, or the manual importer.
- Hecker Suite → Status Bar → Status bar layout and lyrics.

**Depth implementation and dependencies**

The model is the byte-identical coarse background-removal model from
`Lunaris-AOSP/packages_apps_Singularity@0c892ebd04205889b230e986dd4194af4f4d0e24`.
Source attribution, original runtime notices, input hashes and immutable origin
details are retained in `prebuilts/depth/`. This adaptation uses the coarse mask;
it does not include Lunaris's optional deep-matting or foreground-estimation
models. Manual import remains available for unsuitable photos or imperfect edges.

Only the separate Suite app includes LiteRT 1.4.0 and its arm64 JNI library.
SystemUI reads the prepared aligned pair from the protected provider. Extraction
runs on wallpaper changes or explicit import, not in every drawing/wake frame.
Automatic mode is opt-in and does not replace the current wallpaper itself.

**Validation**

- 140 Python host tests pass, including production shared-lyrics session/user/
  cache/cancellation tests, clock geometry, media geometry, appearance ownership,
  motion lifecycle, translations and the new packaged-model/runtime checks.
- The rest of `tests/run_host_tests.sh` passes, including production Java policy
  checks and the Shell motion checks. These use host facades, not a phone.
- Complete Suite Java compilation, real resource linking and AIDL generation pass.
  The Settings dashboard module still compiles independently of backend classes.
- QS/lyrics/status-row Kotlin and Java compile against the locked Android/Compose
  inputs; About phone, Motion Island and Settings homepage adapters/resources compile.
- 127 immutable upstream files are checked. Fresh preparation, upgrade from the
  current Suite, idempotence, rejection of unfamiliar edits and exact reversion pass.
- The native final Settings rounded/highlight adapter compiles, and its patch
  registration is checked against the real build preparation map.
- The packaged model initializes and produces a finite 512×512 subject mask on
  the bundled composite wallpaper. Host inference is not a Pixel performance test.
- SHA-256 checks, arm64 ELF identity, 16 KB load-segment alignment and the actual
  Java interpreter API classes pass.
- `git diff --check` passes.

**Phone acceptance still required**

Build and flash Coral before treating this as a release. Check search at normal
and large font/display sizes; drawer/Home icon independence; all clock styles;
lyrics with a track that has synchronized or embedded lyrics; AOD/pause/seek and
player switching; every dual-row preset and network-speed position; both QS
styles while repeatedly expanding/collapsing; skip/play/pause Motion Sense; unlock
and cancelled unlock; and app-to-Home gestures. Check automatic extraction after
changing only the lock wallpaper, rapid wallpaper changes, no-subject photos,
reboot, manual override and a second Android user. Check first-boot GameSpace
placement separately from moving/deleting the shortcut and rebooting.

There is no full ROM, Soong, flashing, screenshot-after, runtime-heap or battery
acceptance result in this review package. Song coverage still depends on the
player's metadata, embedded lyrics or the LRCLIB catalog. The full model inference
and animation behavior need testing on the target hardware.

## Publication packaging

The reviewed Java/Kotlin/XML sources are preserved. Binary depth inputs are
prepared on the build machine with `tools/fetch_depth_assets.py` rather than
stored in Git. The pinned source URLs, sizes, SHA-256 hashes and extracted
output hashes reproduce the exact reviewed model and LiteRT 1.4.0 runtime.
The Coral build helper, CI and standalone Suite compile prepare these inputs
before compilation. Extraction remains offline on the phone.

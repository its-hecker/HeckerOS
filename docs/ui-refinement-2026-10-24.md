# Suite and SystemUI refinement — 24 October 2026

Based on feat/suite-polish at 1ee13dd. Apply only after a running build has finished; sync the manifest pin and Suite, then run the normal manifest build script. Keep the current Byobu/RBE environment.

## Changes

- QQS clock fades during the first 12% of expansion; scene status row exits during the first 10%. The scene and Compose-fragment paths both carry explicit compact-header transitions. Expanded header appears at 25–65%.
- Hecker QS uses connectivity and media glass modules, circular native tiles, Focus, and two equal 64 × 178 dp vertical controls, with native brightness-only padding removed in this orientation. Brightness retains native policy, gamma conversion, accessibility and gesture handling. Every selected tile remains reachable across pages. OneUI keeps its layout and gains the shared media player. Its fixed SmartThings slot now reuses Lunaris’s existing native Dolby tile: tap toggles the effect, live state/profile follows the tile, and long-press opens Lunaris controls. The tile is bound once for upgrades without duplicating an existing selection or resetting the chosen QS layout. It appears once in OneUI, in the fixed slot.
- The media player uses real current-user session artwork, title, artist, seek position, duration, previous/play/pause/next and native output routing. Artwork is blurred behind a readable glass layer. Native lock-screen/QQS visibility gates remain in charge. Native compressed media resumption remains available when no active controller exists.
- The OneUI battery gets a rounded capsule, level colors, green charging indication and contrasting percentage clipping. The Suite exposes level colors and 80–140% icon scaling; both View and Compose renderers observe changes.
- Carrier text in the keyguard header becomes a 15 dp caracal with “Stay curious.” Carrier information remains in expanded QS. System icons, privacy indicators and unlock flows remain native.
- Lyrics render above the bottom lock icon, separately from the media card. Ambient display uses one dimmer current line, slower UI updates and native burn-in movement. LRCLIB remains opt-in and current-user scoped.
- Measured Suite cards no longer use MATCH_PARENT height in a wrapping row; tabs have explicit content spacing. Back arrows use a 24 dp glyph in a 48 dp touch target. The Settings homepage no longer has a negative top margin.
- QS Lottie previews resume after asynchronous composition loading and visibility changes. GameSpace has original offline game-controller artwork, a shorter featured card and a cyan/navy palette.
- App Lock launches the native change flow for a result and reloads authoritative package state on return, including translucent prompts. Both the native App Lock prompt and the Suite authentication prompt receive animated decoration; native sensor animations and authentication callbacks are retained.
- Oslo uses its separate non-touchable window when unlocked. Its callback can no longer force the native shade open; unlocking releases a hold acquired on the lock screen. Studio's explicit preview still uses the actual Oslo surface and its base activity already ends the preview on pause. No second glow host is created.
- Game Booster is limited to 328 × 472 dp in portrait and at most 304 × 352 dp in landscape, always inside safe bounds. Landscape has compact control rows and paired sliders, with visible scrolling on smaller screens or large text.
- Games without native Performance/Battery modes can use Android's real custom game mode, where permitted. Performance requests native render scale and up to 90 FPS; Battery saver requests 30 FPS and 0.8 render scale when allowed. The saved UI profile is accepted only when the framework mode and configuration read back correctly. This cannot unlock a game's internal FPS limit or override an app's intervention opt-out.
- Charging pause retries node ownership after driver probing and reports a missing node separately from denied access. The Coral driver keeps its independent inhibitor, thermal/health ownership and six-second fail-safe lease. This is charging pause, not a claim of electrically isolating the battery.

## Source checks

Passed: Suite Java/resource linking; QS/lyrics/battery Compose and Java; native interaction and glow Java; 79 Python host tests plus Java checks; 77 integration tests; the pinned SM8150 charge-gate harness; and 71 immutable native files with 172 adapter/copy operations applying idempotently and reverting exactly. The native PIN, plugin allowlist, status-bar dispatch, boot selector, secure capture and merged service/fuzzer checks also pass. These are source/host checks, not a full ROM build.

## Device verification still required

A full Android/Soong build and Coral flash are required. Compare both QS layouts at 0/10/50/100% expansion, light/dark themes, font scaling and landscape. Test artwork/seek with several media apps, lock-screen media visibility, AOD burn-in and lyrics, biometric success/error/cancel, immediate App Lock toggles, Soli from HOME and apps, repeated Studio previews, and actual charging-node ownership/readback while wired power is connected. Measure battery current and temperature before describing the charging implementation as bypass charging. Do not assume a source check proves on-device appearance or hardware behavior.

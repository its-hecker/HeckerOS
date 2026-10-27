# Pixel 4 XL follow-up, 25 October 2026

This follow-up uses the user's `25_10-13-48-22_154` recording (coral, Android SDK 37). The user confirmed game modes, bypass charging, Motion Sense glow and Hecker Launcher now work. The new fixes below still need a new ROM build and phone regression.

| Report | Change |
| --- | --- |
| Hecker quick QS looked like One UI | Compact QS had unconditionally called `HeckerReferenceCompact`. It now follows the selected layout: Hecker uses its coloured tiles in a bordered glass module; One UI retains its circular reference row. Native tile selection, click and long-press behavior stay in control. |
| One UI full QS media was enlarged | Restore the original 70 dp minimum, single-row glass player with play/pause, title and output button. Add a 36 dp album-art thumbnail when available. The richer media card remains on lockscreen/quick QS and the existing Hecker full panel. |
| One UI battery shape | Use the selected battery overlay's original dimensions, perimeter, mask and fill direction. Remove the hardcoded 32×16 replacement silhouette. Keep level colours, percentage contrast and the 80–140% scale control. |
| Remove Memory Boost | Remove the Suite page, router entry, QS service, Binder interface, framework policy and AM/Recents hooks. Remove known generated copies during preparation and both saved custom-tile component forms on SystemUI user startup. Previous app rules have no remaining consumer. Android's normal process management resumes. |
| GameSpace stuck/crashed | The recording has a fatal main-thread `GamingOverlay.showPage` null `ScrollView` after a dashboard navigation callback. Reject closed/replaced containers and stale detached page buttons. Coalesce preference notifications and refresh the current content without tearing down its window. GameManager reads/writes run on the existing background worker, with busy gating and stale-session completion rejection. |
| Resizable in-game dashboard | Drag the bottom resize grip, or tap it to open width/height sliders. Save separate portrait/landscape dimensions. Clamp to available system-bar/cutout bounds, retain scrolling, and support keyboard/accessibility slider changes. |
| Home swipe down | Empty-workspace downward swipes open the native notification shade/quick QS. This is independently switchable from double-tap sleep. Normal workspace touch cleanup still runs; horizontal swipes, icons, widgets, long presses and multi-touch retain their native handling. |
| Launcher glass | Hecker Launcher settings now expose Off/Frosted/Liquid glass, individual dock/folder/search/widget-container switches, and independent top-left/top-right/bottom-right/bottom-left corner radii. These are Launcher3 source adapters using its real preference store and lifecycle. Wallpaper decoding and a bounded separable blur run off the UI thread; the liquid edge uses a RuntimeShader. |

## Launcher boundaries

The launcher requests normal `EXPAND_STATUS_BAR` and signature/privileged `READ_WALLPAPER_INTERNAL`, alongside the existing signature `DEVICE_POWER` permission. Hecker Launcher is already platform signed. It reads the current user's static wallpaper, not screenshots of apps or widgets. Bitmap decoding is bounded to 1024 pixels on the longest side; the frosted copy is bounded to 512. Native wallpaper scroll offsets drive sampling. Live/inaccessible wallpaper or unsupported shaders use a theme-aware translucent fill. Opaque backgrounds inside third-party widgets remain owned by those widgets.

Preference changes restore original container drawables before applying the new selection. Window detach unregisters receivers and observers, restores original backgrounds and discards late worker results. The retired Pixel reflection/service adapters remain retired. Pixel continues as the single Recents provider.

## Validation and acceptance

Validation on 25 October 2026: **85 host tests**, the pure Java regression suites including **60 NetLab checks**, **77 integration tests**, Suite/NetLab/independent Settings compilation, QS Kotlin/Compose compilation and the control receiver compilation passed. **78 immutable native inputs** and **167 adapters/copies** apply idempotently and revert exactly; launcher Java/resources compile against the locked SDK. Full ROM/SELinux and phone checks remain pending.

Host/source validation covers the production dashboard renderer with closed/replaced views; production mode callbacks with queued Binder work, busy gating and stale sessions; overlay-supplied battery geometry; resize bounds; swipe direction/cancellation; both retired tile forms; and fresh/prepared Launcher3 upgrades with unfamiliar edits preserved. Suite/NetLab/Settings Java and resources, QS Kotlin/Compose, launcher adapters/resources and pinned native source adapters are checked separately. These are not a full Android/SELinux build or hardware validation.

On the next phone build:

1. Switch QS styles and compare both compact layouts. Check the full One UI player with/without artwork, playback/output controls and large font scale. Check Hecker full QS and lockscreen media.
2. Verify the original One UI battery outline at several levels, while charging and with percentage/scale settings.
3. Confirm Memory Boost is absent from Suite and the tile editor and that an old saved tile disappears. Confirm ordinary app starts, Recents dismissal and notifications use normal Android policy.
4. In a real game, rapidly change profiles and navigate dashboard pages while switching apps/orientation. Check no Suite restart, blocked main thread, stale callback or wrong-game update. Recheck working bypass charging and exit/lock restoration.
5. Resize the dashboard to both bounds and in both orientations; reopen it, rotate, and test keyboard/TalkBack size adjustment and scrolling.
6. In Hecker Launcher, test empty-home swipe down and double tap independently, horizontal swipes, app/widget taps, drag/edit mode and multi-touch. Test all glass styles/targets/corners, light/dark modes, wallpaper/page changes and a live wallpaper fallback. Test HOME/Recents after reboot.

The separately approved [NetLab implementation](network-lab.md) is included in the same publication. Its unimplemented shaping/router/NetLock/scheduling features remain explicitly unavailable.

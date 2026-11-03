# Coral launcher and depth feedback — 3 November 2026

This source update addresses the supplied launcher, wallpaper, Quick Settings
and developer-options screenshots. It retains the preceding font boot fix.

| Report | Change |
| --- | --- |
| PixelOS spoof controls in Developer options | Remove the certified-profile, keybox and PIF preferences and their certified-profile controller registration through guarded native Settings adapters. The pinned framework already skips the old PixelOS GMS/Play Store property path; its keystore entry points already use the Hecker implementation. No conflict was established merely by the old UI being visible. Existing configuration is not erased. |
| Blank Hecker dashboard preview | Use a native vector illustration of the connectivity cards, vertical brightness/volume sliders and shortcuts. It remains visible with animations disabled. |
| Apply Caracal Dawn | Remove that action from Hecker Suite. |
| Manual layer import error | Fit different source resolutions to a common canvas, retaining alpha and aspect ratio. Add subject size and horizontal/vertical alignment controls and a combined preview. Opaque images still require automatic extraction or a transparent PNG. |
| Home depth extracted but did not render | Replace the provider manifest gate requiring STATUS_BAR, which Launcher did not request, with a dedicated signature READ_DEPTH permission. Both the generated Launcher and native SystemUI manifests request it. The provider retains its package/signature/user checks and read-only active-pair rules. |
| Home clock height | Add 112–320 dp height and 0–120 dp top spacing, bounded by the remaining app-grid space. Lowering the clock allows it to intersect the subject. |
| Launcher settings appearance | Use grouped rounded cards, colored switch tracks, slider value chips, a clock preview card, a rounded selection dialog, a header and a subtle entry fade respecting disabled animations. |
| Message and weather under the subject | Offer above/below/left/right placement, defaulting above. Information has its own rectangle and is drawn after the depth foreground, for all Hecker clock styles. |
| Search height and glass dock | Offer a 36–64 dp visible pill, retain at least 48 dp touch height, reserve its complete height/inset/icon gap, and add a 12 dp upper gutter to the glass dock. Glass redraws follow size and search-mode changes. |
| Apply Living Flow preview look | Rename to Apply Hecker Icon Pack and limit the action to the built-in icon treatment and global pack selection. |
| Ambiguous depth controls | Explicitly name Home screen depth wallpaper and Lock screen depth wallpaper. Add a direct Home importer entry from Launcher settings. Lock-only import defaults to preserving Home; the optional apply-to-Home switch remains available. |
| Only tall lock clock supports depth | Remove the explicit style 2–5 renderer gate from the shared lock-screen depth scene and foreground mask. All six built-in Hecker lock-screen clocks can use it. Depth import/extraction retains the selected style. The Hecker custom clock remains required; third-party stock-clock plugins are not covered. |
| Auto extraction of current lock wallpaper | Share one bounded extraction pipeline between wallpaper jobs and an explicit current-wallpaper action with success/failure feedback. Handle the built-in wallpaper resource when there is no saved file; explicit retry bypasses the existing-pair cache. Preserve the Home pair and reject stale results if the wallpaper changes or the job is cancelled. Live wallpapers remain unsupported. |

## Validation

Host execution covers 2,000 non-overlapping clock/information rectangles,
aspect-preserving manual registration, variable search sizes, the actual
current-wallpaper pipeline with OS/image boundaries faked, provider access and
909 home-layer crop cases. The current-wallpaper cases cover saved lock/system
sources, bounded built-in resources, cache/forced retry, selected clock
preservation, Home retention, and stale/cancel/live-wallpaper rejection.

The native source plans are tested for initial application, repeat, exact
revert and refusal of unfamiliar edits. The previous generated Launcher manifest
upgrades its permissions while retaining the existing Suite and game-library
permissions. Copy history includes the exact preceding Launcher and SystemUI
adapters so the prepared ServerHive tree can upgrade in place.

These host checks do not replace Android compilation and Coral acceptance.
Check manual and automatic imports with real images, all six lock clocks,
all ten Launcher clock styles, four information positions, search sizes and
glass-dock on/off after building. Subject extraction depends on finding a clear
subject; an extracted layer only covers clock pixels where their positions
intersect. The unchanged Google weather provider must supply weather data.

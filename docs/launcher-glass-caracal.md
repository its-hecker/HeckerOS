# Reference glass, folder reveal and Caracal Dawn

The combined update is on `test/floral-low-brightness-flicker-20261029`, after the native Settings/depth-clock work. It retains the Coral root-privacy kernel/framework selections and Pakistan IMS/carrier configuration. It needs a rebuilt ROM; it does not update the running phone.

## Folder animation fix

The supplied recording shows a large glass rectangle lingering as the icons collapse. Android 17 `Folder.getBackground()` exposes its private `GradientDrawable`; native `dispatchDraw()` renders it inside `mClipPath`. The old scanner called `View.setBackground()` on Folder, creating a second background painted before that custom clip. The second layer was never animated with the native reveal and could not be restored through the overridden getter.

Folders are no longer scanner targets. Glass draws synchronously from both native `dispatchDraw()` background sites, inside the existing reveal clip. Closing preview icons retain Android's intentional ability to protrude beyond that clip. The private gradient remains available to the native colour animator and casts. Folder previews also use sampled glass inside their existing shape/drag clipping. Animation duration, icon motion and close/cancellation callbacks stay under native Launcher control.

The shared renderer caches its bitmap shader, matrix, radii and rim gradient. Wallpaper decoding and bounded frosting run on the worker at wallpaper/preference changes, not on folder open/close. There is no new delayed animation or extra overlay window.

## Glass and app icons

The default palette is darker, wallpaper-derived smoked glass with a subtle warm rim and pale glyphs, matching the supplied style references. Search, folders and app-icon plates sample the same bounded frosted wallpaper. Frosting, opacity, tint, border and corner controls remain editable. The full-width dock panel is off by default so the individual glass icon plates remain distinct; the separate dock-glass switch can enable it.

**Launcher Studio → Appearance → Icon Studio → Glass icons** is on by default. App-provided adaptive monochrome art is preferred. Older adaptive foreground/legacy icons receive a bounded local alpha/luminance conversion on a worker. Blank or unavailable glyphs retain the native icon; animated, pending-install and explicitly filtered native drawables also retain their native treatment. Icon packs and per-app overrides take priority. Android supplies profile badges and the existing launcher supplies notification counts. The references do not identify an icon-pack package, so the ROM uses each installed app's own artwork rather than claiming identical third-party glyphs for every app/version.

Live wallpapers use the existing translucent fallback because the launcher does not capture a live scene or app/widget pixels.

## Home and search controls

- **Launcher Studio → Home screen → Add new apps to Home screen** reads native `SessionCommitReceiver.isEnabled()` and writes its actual `ADD_ICON_PREFERENCE_KEY`. Native private-profile restrictions still apply. Toggling this affects later installs; existing shortcuts are retained.
- **Launcher Studio → Universal search → Google search bar** replaces the Hecker bar in the same inset-safe slot. It defaults off. Google mode has a monochrome G, search, and available Voice/Lens actions. Hecker and Google modes use the same glass background; neither adds a second row.
- Google actions resolve installed handlers. Missing Voice/Lens handlers hide their buttons. Google search falls back to an available Google app or browser. No private Google activity names are hardcoded. Installing/enabling an app and reattaching the bar refreshes available actions.

## Default wallpaper and lockscreen

Original **Caracal Dawn** artwork ships as an opaque dawn-city scenery layer and an aligned, genuinely transparent modestly dressed adult anime Caracal-girl layer. Both sources are 860×1828. `DefaultAppearance` composites the same pair for the home/lock wallpaper on a fresh user's first boot, then stores bounded 1600px depth layers in that user's device-protected files for availability before first unlock.

Hecker Launcher now renders the same prepared scene in layers with an unlock reveal; see [Home depth and clock weather](home-depth-weather.md).

Fresh users receive the tall thin live clock at **76% opacity**, with the existing **750 ms** wake reveal. Clock opacity is adjustable under **Hecker's Suite → Lock screen**. Time/date remain live native drawing, not wallpaper pixels. The original word clock and geometric style remain selectable. AOD retains its native-safe geometric treatment; wake motion respects Battery Saver and disabled system animations.

Saved wallpapers, live wallpapers and previously chosen clock settings prevent automatic replacement. Applying this update on a dirty flash therefore retains existing choices. **Hecker's Suite → Lock screen → Apply Caracal Dawn** explicitly applies the new pair and clock to an existing user. Picking another wallpaper disables the old depth subject; importing another prepared pair records the new wallpaper identity. There is still no automatic arbitrary-photo subject segmentation.

Asset generation used the built-in Image Generation skill. Source images and prompt specifications are in [`app/assets/wallpapers/caracal-dawn-v2`](../app/assets/wallpapers/caracal-dawn-v2/README.md).

## Validation

- 122 Python Suite host tests plus the existing Java/C++ regressions; 81 integration tests.
- Executed the prepared native Folder draw method through **404** open, close, steady/cancel and native-fallback clip cases. Glass never escapes the current reveal clip; native closing-icon clipping behavior and canvas restoration are retained.
- Production default-appearance code exercised for fresh users with a positive built-in wallpaper ID, existing photos/live wallpapers/clock choices, reboot preservation, explicit restore, per-user direct-boot layers, mismatched assets and wallpaper changes.
- Production read-only depth-provider checks cover SystemUI identity/signature, active pair, read-only path boundaries, user isolation and bundled direct-boot storage.
- Complete Suite Java/resource/AIDL and controller checks, SystemUI Java/Kotlin/Compose/resource checks, and AOSP launcher helper/native-adapter SDK checks.
- Guarded ports apply to immutable Android 17 inputs, are idempotent and revert exactly. Upgrading an already prepared `2d531ce` launcher applies cleanly and reverts to the exact original inputs.

A full Launcher3/Settings/SystemUI Soong build, ROM boot, exact on-phone appearance and recorded animation performance still require Coral testing. The supplied recording diagnoses the old code; it is not an after-fix device test. This folder drawing fix does not establish a fix for the separate intermittent navigation-gesture freeze.

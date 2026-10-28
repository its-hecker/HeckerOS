# Hecker Launcher Studio

Hecker Launcher’s home settings open an illustrated Studio. The Hecker Suite launcher page opens the same Studio through the existing Launcher3 settings activity. Clock Studio appears once on the main page; the unused extra-settings button is removed.

## Clock Studio

Choose a style and palette in **Hecker Clock Studio**, then apply it to the built-in first-page
home clock. It needs no widget placement. Existing clock widgets remain compatible, support
horizontal/vertical resizing and retain their individual style selections when reconfigured.

| Reference | Style | Behavior |
| --- | --- | --- |
| C | Glass | Frosted panel, local date, available Google Smartspace weather |
| D | Minimal | Thin digits, date and next alarm when one exists |
| F | Mono | Mint monospace digits |
| G | Signature | Warm colon and Hecker OS signature |
| H | Compact | Compact digits with an amber accent |
| I | Thermal | Animated telemetry accents; battery temperature, level and RAM |
| J | Digital | Bold digits with a blue colon |
| K | Analog | Native dial and live hands |
| L | Stacked | Stacked hour/minute, animated battery arc and temperature |
| M | Caracal | Original sunglasses/ear line art with animated ears and head tilt;
  clock, temperature, available CPU load, RAM and battery |

Messages rotate locally every 15 minutes. Message rotation and clock motion have
independent switches. Time/date formatting follows the device locale and its
12/24-hour setting. Animation follows Android's animation setting and stops when
the view is detached or hidden. Telemetry samples run away from the UI thread at
most every 30 seconds. Battery broadcasts update battery readings directly.

RAM is system used/total memory from `ActivityManager.MemoryInfo`. CPU is a real
`/proc/stat` delta when Android permits reading those counters; otherwise it shows
an unavailable marker. No sample percentages are substituted. Weather uses the
already retained Google Smartspace adapter and disappears when unavailable.
There is one weather subscription shared by visible clocks of all ten styles.
**Clock Studio → Show weather** is on by default; a bounded weather row stays above
the Home depth subject. Missing Google weather leaves the clock/date usable.
See [Home depth, defaults and weather](home-depth-weather.md).

Continuous animation is a local Hecker Launcher widget-host extension. Another
launcher can display the standard time fallback, not the local animated renderer.
The built-in home clock does not modify lock-screen or AOD clocks. Existing clock widgets remain compatible.

## Glass Studio

The current experiment includes the [reference smoked-glass look, synchronized folder reveal, glass app icons, Google search toggle and default Caracal Dawn wallpaper](launcher-glass-caracal.md).

The live preview uses the same wallpaper sampler and glass drawable as the dock,
folders, search bar and widget containers. Controls include style, opacity, full
RGB tint, frosting strength, border width, per-container enable switches and the
four existing independent corner radii. Clear, Frosted, Smoked and Violet presets
set initial values, which remain editable. Wallpaper sampling and bounded blur
run off the UI thread; live wallpapers use a translucent fallback. Frost changes
are debounced. Other controls repaint immediately.

## Home layout and icons

Home and drawer have independent columns, rows and spacing. Drawer rows control
native cell-height calculation in its scrolling grid. Dock icon count is
independent. Custom metrics use Launcher3's existing nonresponsive profile path;
standard metrics keep the normal responsive profile. Grid changes trigger the
native invariant-profile/model migration and use a grid-specific target database.
Previous databases remain available to the native migration machinery.

The layout lock blocks workspace/folder/drawer drag starts and widget resizing.
Native menu actions that remove or add a home shortcut are filtered while locked.
Normal app launching and the separate privacy/customization actions remain
available.

Icon Studio supports compatible public `appfilter.xml` icon packs, a searchable
individual-icon picker with thumbnails, renamed labels, icon size and icon shape.
Home and drawer have independent label visibility and text size. Per-app choices
are keyed by component plus Android user serial, so profile entries stay separate.
The per-app System default choice overrides a global pack; Reset restores the
inherited pack and the original label. Drawer ordering/search uses renamed labels.
Pack parsing and replacement bitmap loading run on a worker.

Small mint notification-count pills use Launcher3's existing `DotInfo`, including
its notification filtering and dismissal updates. Counts above 99 display `99+`.
The existing notification-listener access is required; the settings page links to
Android's Notification access screen. No second notification listener is added.

## Privacy and local search

Long-pressing an app exposes separate **HeckerOS App Lock**, **Hide app** and
**Customize app** actions. App Lock opens the existing signature-protected Suite
router, focuses the package after authentication, and retains the native
biometric/device-credential configuration flow. The launcher never changes native
lock state itself. Cross-profile App Lock configuration must be opened within
that Android profile.

Hiding filters the drawer and both universal and fallback launcher searches. It
retains existing home shortcuts and does not change App Lock. Hidden entries can
be restored under Privacy → Manage hidden apps. Hiding is a launcher presentation
preference, not access control.

Universal search opens from home search and the drawer search field. It searches
locally indexed apps plus dynamic, manifest and this launcher's pinned shortcuts
through `LauncherApps`. Hidden apps and unavailable/quiet profiles are excluded.
The app/shortcut launch uses the native user-aware LauncherApps APIs, so Android
continues to enforce App Lock and profile access. Shortcut indexing requires
Hecker Launcher to hold HOME shortcut-host access. Universal search and shortcut
results have separate switches. The fallback native app search remains available.

Home screen, Appearance and Privacy cards contain original animated vector
illustrations: icons arranging into a grid, a glass panel with changing shapes,
and a moving lock. They pause when hidden and respect disabled animations.

## Validation

Host validation includes the existing Suite tests, Studio policy edge cases,
SDK compilation of all launcher helpers, actual resource linking, widget metadata,
and compilation of the exact native long-press factories and app-filter adapters.
The guarded ports are applied to immutable Android 17 inputs, applied again, and
reverted to confirm idempotence and exact restoration. The previously published
HOME module/manifest and glass source have explicit upgrade handling.

```bash
bash tests/run_host_tests.sh
python3 tools/compile_sdk.py --cache generated/sdk
python3 tools/check_launcher.py --sdk-cache generated/sdk
python3 tools/check_native_ports.py --sdk-cache generated/sdk
```

These checks do not compile all of Launcher3, run its UI, or establish that the
ROM builds or boots. A full Soong build and phone testing are still required.
On a test build, verify:

1. Each clock, widget resize/reconfigure, 12/24-hour time, animation-disabled mode,
   screen off/on, unplugging and the unavailable CPU/weather cases.
2. Glass preview against the actual containers, all presets, zero opacity/border,
   tint sliders, four corners and wallpaper changes.
3. Grid/dock migration in both directions with existing folders/widgets; locked
   layout with app, folder, drawer and widget long presses.
4. Global and per-app icon choices, stock override, label reset, drawer sorting
   and independent label controls.
5. Notification posting, count changes, dismissal and access revoked/granted.
6. App Lock authentication, hide/unhide, and search/shortcut launching from
   accessible profiles, including the fallback native search.

## Home integration and colour

Clock Studio now controls a launcher-owned header on the first workspace page. It is measured
outside the movable app/widget cells, so adding it needs no widget placement or database rewrite.
Its height is reserved before measuring the existing grid. On a dense grid with insufficient space
it hides instead of covering icons; reduce grid rows to make room. The feature has its own switch.
Previously placed clock widgets remain supported and can be removed normally.

Aurora, Sunrise, Lagoon, Orchid and Classic palettes share the live preview and home renderer.
Messages rotate each minute and on returning home; tap the built-in clock for another message and
hold it for Studio. Screen-off pauses animations and sampling. A native local-search pill fills the
hotseat’s existing below-dock search slot. Clock Studio has one entry on the main Studio page;
Appearance contains Glass and Icon Studio. The broken extra-settings link has been removed.

# Caracal Dawn v2, Home depth and clock weather

The combined `test/floral-low-brightness-flicker-20261029` branch retains the
refresh protection, Settings/depth clocks, NetLab, Game Space and launcher work.
It now uses Caracal Dawn v2: an adult anime character in a full-coverage,
high-neck outfit, with more clear space on the left for the clock. The dawn
scenery stays the same. Both source layers are 860×1828 with real foreground alpha.

## Default appearance

A fresh user receives the layered wallpaper on both screens, the tall lock clock
at 76% opacity, the 750 ms lock wake reveal, and Home depth enabled. Time and date
are native drawing between the layers. The artwork has no baked-in clock.

The old bundled v1 updates to v2 only while the recorded wallpaper identity still
owns both Home and Lock and its depth setting remains enabled. That update keeps
chosen clock settings. Existing photos, live wallpapers and other selections are
retained. Each user's bounded layers live in device-protected storage. The v2 ID
invalidates the old in-memory scene; the provider still recognizes existing v1
device-protected files while they remain active.

**Hecker's Suite → Lock screen → Apply Caracal Dawn** explicitly reapplies the
current wallpaper and default clock. Importing another prepared pair has a
default-on **Use depth wallpaper on Home screen too** switch; switching it off
applies the scene to Lock only. Changing either system wallpaper disables only
that screen's stale subject layer.

## Hecker Launcher depth

**Launcher Studio → Home screen → Depth wallpaper** and **Animate depth on
unlock** are on by default. The native DragLayer draws cached background and
subject before its own scrim, focus indicator and children. Clock views redraw
the matching subject above their own time artwork, clipped to their bounds.
Apps, folders, navigation, gestures and weather stay above the scene. No overlay
window or touch target is added.

The two image layers share the lock screen's crop and 2.5% to 0% wake zoom, so
they keep aligned edges. Unlock uses the same 750 ms easing; Battery Saver and
disabled animations settle immediately. This is prepared-layer depth and an
unlock reveal, not arbitrary-photo segmentation or sensor-driven 3D motion.
Visible overlap depends on clock placement and the artwork's silhouette.

The launcher accepts only an active prepared pair bound to the current Home
wallpaper ID. New photos, live wallpapers, unavailable providers, stale loads,
invalid IDs and oversized images fall back to the native wallpaper. Decoding
runs off the UI thread. Observers, receivers and animations are cleaned up on
root detachment; transformed overview clock views skip foreground masking.
Only the same-user, platform-signed preinstalled Hecker Launcher can join
SystemUI and the Suite as a depth-file reader.

## Clock Studio weather

**Launcher Studio → Clock Studio → Show weather** is on by default for all ten
clock styles, the built-in Home clock, Clock Studio previews and clock widgets
hosted by Hecker Launcher. Each style has a bounded weather row, placed above
the depth subject for readability and included in accessibility descriptions.

The existing Google Smartspace adapter supplies actual text and an available
condition icon. It shares one subscription among visible clocks, stops when the
last one hides or the screen turns off, and respects the weather switch. The
Google app must provide weather; absent data leaves the row empty and keeps the
clock/date usable. No invented temperature, added location polling or new
weather service is used.

## Validation

- 127 Suite Python host tests and the complete Java/C++ host regression script.
- Production Home controller checks for ownership, opt-out, photo/live fallback,
  invalid and oversized loads, stale results, safe clipping, unlock, Battery Saver,
  reduced motion, observer failure and detach; 909 crop cases match the lock policy.
- Real weather bridge lifecycle and production subscription gates for all ten styles.
- Default v2 migration, independent Home/Lock invalidation and depth-reader identity tests.
- Full Suite/controller Java, Android resource and AIDL checks with the locked SDK.
- Launcher helper/resource compilation and the actual pinned native draw method;
  prepared source upgrades idempotently and reverts exactly.
- Retained low-brightness display policy: 1,372 checks and framework/device resources.

A full ROM/Soong build, boot and phone checks remain required for final placement,
weather availability and motion/refresh behavior.

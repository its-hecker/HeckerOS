# Native interaction features

This checkpoint extends `feat/suite-polish`, retaining Pixel Launcher as the only
shipped HOME/Recents provider and keeping the tracked `out/.find-ignore` NDK-cache
build fix. Rebuild the ROM after syncing the pinned Suite revision.

## Controls

Both One UI and Hecker QS layouts have the automatic-brightness toggle from the
previous source commit. It follows the active Android user.

The One UI volume panel is enabled by default. It uses TouchWiz-style compact and
expanded interaction: the active audio stream in a small side panel, then media,
ring, notification, alarm, brightness, sound/vibrate/mute and Live Caption controls.
Audio levels, dynamic remote streams, DND restrictions and callbacks come from the
native `VolumeDialogController`. Android's original safety and CSD dialog factories
retain hearing-exposure handling. The panel scrolls within small/landscape displays,
supports TalkBack/key progress actions and uses the native temporary/persistent
brightness APIs without triggering the QS brightness mirror. Disable **One UI
volume panel** under **Suite → Quick Settings** to return to the stock dialog.

Long press the clock or status icons to open date/time, Wi-Fi, Bluetooth, mobile
data, battery, Memory Boost and Settings actions. Normal taps and shade swipes
remain native. Actions are disabled on the lock screen and close on screen-off or
user changes. The switch is under **Suite → Status bar**. Settings shortcuts use
SystemUI's provisioning/keyguard-aware activity starter.

## Memory Boost

**Suite → Memory Boost** lists installed apps, with search and system apps hidden
by default. An app's dialog contains exactly these four independent controls:

| Control enabled | Native behavior |
| --- | --- |
| Background start | Block automatic new process starts; activity launches and helpers of a currently foreground/foreground-service UID remain allowed. |
| Background task | Every 15 seconds, clean the selected app's no-longer-important background processes; this can delay its notifications. Work is batched per user. |
| Clean task | After removal from Recents, clean eligible processes after a short grace period. A foreground return is protected. |
| Resident app | Exclude the app from all three cleanup paths and cap running processes' maximum OOM adjustment at the native perceptible-low priority. Disabling restores the previous cap. |

Resident protection does not force-start an app or guarantee survival under memory
pressure. Background-start restrictions remain independent. Cleanup never
force-stops packages or deletes app data/Recents. **Boost now** and the popup button
clean cached processes, not foreground apps. Explicit per-app background/task
cleanup can include ordinary services, while native important-process priority and
HOME state are protected. Persistent apps, shared UIDs, the current HOME/IME,
phone/SMS/assistant/emergency roles and essential platform packages cannot be
configured. System apps with ordinary isolated app UIDs can be shown and managed.

Per-user rules live in `Settings.Secure.hecker_memory_rules`. PM/settings operations
run off the process-manager locks. Startup checks read an immutable snapshot;
cleanup runs under Android 17's AMS/PSC locks. The service counts processes actually
marked killed by AMS and displays either those counts or an honest no-work message.
Its Binder entry points accept only the platform-signed Suite/SystemUI callers.

## Pixel Launcher enhancements

**Suite → Pixel Launcher enhancements** enables empty-home double-tap sleep and
frosted glass by default. Choose stock/frosted/liquid glass, choose dock, search
containers, open folders and transparent widgets, and set each corner radius.
Return home to apply. Icon, widget, folder, drag, pinch and accessibility double
taps are excluded from the sleep gesture. A narrow Binder bridge checks the active
user and preinstalled default Pixel Launcher before returning settings/wallpaper;
sleep additionally requires that launcher to be the focused visible task.

These are original framework-owned view/gesture adapters. Pixel Launcher and its
Recents code are not replaced, resigned or dex-patched. Reflection is restricted to
known Launcher3 state accessors in the Pixel process; unrecognized versions fall
back to stock behavior. Glass samples a bounded static-wallpaper bitmap and follows
the launcher's wallpaper offsets/zoom requests; live wallpapers use translucent
glass. The original backgrounds are restored when the activity pauses or leaves
HOME/All Apps. The runtime shader supports independent corner radii.

This is a native subset inspired by PixelLauncherEnhanced, not its complete Xposed
module. Grid changes, icon packs, drawer hiding/tabs, layout backup and the other
module hooks are not included. Bundling its APK alone would not enable them.

Inspected upstream references:

- RisingOS-Revived framework `0afe5c947f9c7e61c86e123ac99f870d72464c1a`:
  volume compact/expanded interactions and status-bar action concepts.
- PixelLauncherEnhanced `a0e8e98cd338739aac82ec3bf7b044ee51ba1bdb`:
  latest inspected wallpaper-zoom fix; `f745952ad90b06083fc0f3c0ba62db3c3fa8f261`
  adds per-corner widget-glass radii. Its GPL/Xposed implementation is not copied
  into the Apache framework adapters.

## Validation and device checks

Run `bash tests/run_host_tests.sh`, `python3 tools/compile_sdk.py --cache generated/sdk`,
`python3 tools/check_quick_settings.py --sdk-cache generated/sdk`,
`python3 tools/check_interaction_features.py --sdk-cache generated/sdk`, and
`python3 tools/check_native_ports.py --sdk-cache generated/sdk`. The new compilation
check uses actual generated Binder interfaces and pinned volume plugin APIs;
hidden system declarations are reviewed facades, not a complete platform build.
Source adapters must apply repeatedly, upgrade known copies and revert exactly.

A full Android/Soong build and Coral testing remain required. Check both QS layouts,
hardware-volume keys, DND/call/remote/caption streams, safety dialogs, brightness,
rotation and large fonts; swipe/long-press status bar on HOME, apps and lock screen;
test rules with a disposable app while verifying phone, media, keyboard and HOME
survive; test resident disable, task removal/foreground return and user switching.
Check Pixel gestures and glass against the actual shipped APK, wallpaper scrolling,
live wallpapers, folders, widgets, overview and gesture navigation. Native view
compatibility cannot be established by the source-only checks.

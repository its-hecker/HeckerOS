# UI and device features

The UI update fixes text contrast in Suite when hosted by Settings, uses explicit
rounded ripple buttons, and preserves the layered native Settings icons. Custom
Suite, Settings and Motion Sense pages have Simplified Chinese resources; native
Android pages keep their existing translations. Theme names and author credits
remain upstream names.

The default Quick Settings scene follows the supplied reference: a two-row white
header, paired Wi-Fi/Bluetooth cards, an eight-button circular tile tray, separate
brightness and media-volume pills, a compact playback/output strip and four
rounded footer cards. Original vector glyphs replace the reference panel's tile,
header and shortcut icons. The panel uses translucent glass over Android's real
window blur, enabled by the native notification-shade flag, a 48dp maximum blur
radius and SurfaceFlinger support. Native battery-saver, thermal and blur-support
gates still apply; an opaque dark background keeps the controls legible when
blur is unavailable. The wallpaper supplies the colors visible through the glass.

On first upgrade, each user's tile list gets the reference's initial order while
retaining their other selected tiles. Subsequent edits keep the native editor's
order; additional tiles remain reachable by paging. Connectivity icon taps toggle
the radio, card taps open the native dialog, and long presses use native settings.
The brightness track retains Android's gamma, policy, falsing and haptic handling.
Media uses the active user's real sessions, native transport controls and the
native output chooser. Security, active-app and user-switcher disclosures remain
visible. The optional Hecker dashboard layout remains available after migration.

Smart View opens Android screen casting, Modes opens Do Not Disturb settings,
and Nearby devices opens Bluetooth settings. Device control uses Android's
native controls tile, with an installed SmartThings app as an optional fallback.
Samsung's proprietary services are not bundled. The reference's phone/laptop
glyph represents Android's hotspot tile. Visual fidelity, scrolling with large
fonts, blur performance and all tile/media actions still require a built ROM
and comparison on Coral; the source checks cannot certify an exact screen match.

## Headphones

SystemUI starts the bounded connection card after its system-user services.
A2DP, HFP and LE Audio connection events cover standard Bluetooth audio devices,
including AirPods and Soundcore R50i. Wired and USB headset insertions also show
it. Existing devices at startup do not trigger a popup. Bluetooth profiles are
deduplicated for ten seconds per device; cards close after 6.5 seconds, screen off,
user switch or disconnect. Cards appear only while unlocked. The setting is
per-user and enabled by default.

Only Android battery values from 0 through 100 are displayed. Unknown, absent,
malformed and sentinel values are not converted to zero. Separate left, right
and case batteries appear only if available in Bluetooth metadata; otherwise
Android's main battery value is used. Universal case-opening detection or
manufacturer-specific controls are not promised.

The original signed LibrePods v1.0.4 FOSS APK is included as a separate privileged
system-ext app, with an exact size/SHA-256 download lock and explicit permission
allowlist. Open Suite → Headphones → AirPods controls for setup. Its metadata can
supply component batteries to the native card. Android 17 contains the upstream
Bluetooth fix described in LibrePods' installation guide; features requiring
LibrePods' optional VendorID/Xposed hook are not enabled by this integration.
If enabling LibrePods' own popups, disable the Hecker connection popup to avoid
duplicate cards. No audio routing, codec or Bluetooth firmware code is replaced.

## Sandboxed apps

The sandboxed-app restrictions are adapted from AxionOS's sandbox SDK and the
Infinity-X sandbox UI, with exact origins in `framework/sandbox/ORIGIN.json`.
Suite exposes per-app data isolation, restricted network GID and restricted
shared-storage GIDs. Applying a change force-stops the selected app so its next
process receives the new policy. Policies default to off and are per Android user.
System apps, updated system apps and packages sharing a UID are excluded both
in the picker and at process creation.

ProcessList filters the supplemental GIDs using Axion's exact constants and
opts selected apps into Android's existing app-data bind-mount isolation. Existing
mount exclusions, isolated-process behavior and the Android 17 PCC conditions
are retained. No Axion SDK dependency is added. This is not a virtual machine or
a second profile: apps retain Android API/Binder access and permissions. Document
picker/content-provider grants are not revoked by removing storage GIDs. It does
not import Axion's private vault, package hiding or spoofed settings. HeckerOS's
existing native App Lock is retained.

## Face unlock island

**Hecker Suite → Lock screen → Face unlock island** is enabled by default. A black
pill at the top of the portrait lock screen animates during face recognition.
A permitted successful authentication for the active user changes the lock into
a green completion check and displays the localized unlocked label.

The SystemUI CoreStartable observes `KeyguardUpdateMonitor` directly. Fingerprint,
face-detection-only events, another user's result and strong-auth-blocked results
cannot produce the success state. The window accepts no input and calls no
authentication, trust or keyguard-dismissal API. Native unlocking is never delayed.
The pill disappears when keyguard hides, the device sleeps, the user changes or
the feature is disabled. It stays off during AOD and respects disabled system
animations. A central display cutout moves the pill below its safe inset.

## Recovery and validation

See `recovery/README.md` for the exact recovery base, artwork and rebuild details.
Recovery inherits the HeckerOS version property and uses the caracal logo instead
of the PixelOS image. Coral requires a rebuilt boot image for this change.

Host tests cover battery parsing, GID restrictions, contrast, locale argument
preservation, exact recovery restoration and safe adapter upgrades. SDK checks
link resources and compile app Java. Native adapter checks use pinned Android
sources and verify idempotence and exact reversion. These checks do not replace
a full Android/Soong build or testing Bluetooth, the sandbox and SystemUI on coral.

The built-output checker verifies the original LibrePods APK bytes and both
permission XML files. Face-island tests exercise callback ordering, duplicate
results, per-user sessions, strong-auth restrictions, sleep and feature disable.
After building, test the island with both immediate unlock and swipe-to-unlock,
then recheck face enrollment, Motion Sense, camera, headphone profiles and native
QS editing/brightness/volume on Coral. The dedicated QS check compiles
the Kotlin/Java adapters with locked Compose dependencies and links all glyphs
and English/Chinese strings; reviewed facades cover the native SystemUI APIs.

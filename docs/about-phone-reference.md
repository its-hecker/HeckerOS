# About phone reference

The About home follows the supplied HeckerOS reference: near-black background,
purple 26 dp rounded brand card, compact toolbar, device name, two specification
tiles, joined information rows and a centred credits footer. Corresponding light
surfaces are provided. The existing Hecker Suite dashboard stays as it was.

## Native destinations and data

| Control | Destination/data owner |
| --- | --- |
| Device name | Original `device_name` preference and its policy-aware rename dialog |
| Android version | Original `firmware_version` preference |
| Processor | Original `device_model` preference |
| Software information | Native `FirmwareVersionSettings` |
| Device information | Original `MyDeviceInfoFragment` information hierarchy |
| Credits & legal | Original `legal_container` preference |
| Settings search result | Original hierarchy, retaining the requested preference key |

The home hides original top-level preference groups rather than deleting their
controllers or Catalyst binding. The details destination reveals them, including
identifiers, regulatory entries and build-number developer-options handling.
The page restores shared toolbar, app-bar and window appearance on exit.

The device name observes `Settings.Global.DEVICE_NAME` while active. Android and
hardware values use `Build`; HeckerOS version and maintainer use their existing
system properties. SM8150/msmnile is formatted as Snapdragon 855. Other reported
hardware is retained. No screenshot model, version, identifier or storage reading
is injected into the product.

## Shared glow

`framework/qs/MediaGlowSpec.java` supplies the existing media animation's colours,
gradient positions, radius, alpha and absolute-time 7.2-second phase to SystemUI
and Settings. The media player continues using its existing activity/fade logic.
The About card draws cached shaders behind its children and clips them to its
rounded background. The title, subtitle and emblem remain stationary.

The About animation requires an active page, attached/shown view, visible and
focused window, enabled system animations, Living Flow and no battery saver.
Observers and the power receiver are removed on detach; frame callbacks stop
when those conditions are false. Disabled motion leaves a static glow.

## Validation and preview limits

Host checks exercise the production glow class through a small Android lifecycle
facade and compare its phase with the media player's original phase function.
Navigation checks execute the prepared native About methods for home/details,
rename policy, search entry points and fragment lifecycle. Guarded source changes
are checked for fresh preparation, upgrade, repetition and exact revert.
The normal source/resource/translation checks remain required.

`tools/render_about_phone.py` reads the current XML, colours and vector paths,
compiles the production Java glow specification and exports one complete cycle
at 60 fps. It also exports light-mode and 2x-font layout studies. DejaVu substitutes
for Android fonts, the host layout/compositor approximates Android, and the
sample Pixel 4 XL/Android 17/version 1.0 values are illustrative. These exports
are source previews, not Android screenshots or performance measurements.

The complete Settings/SystemUI Soong build and Coral validation remain required:
default/large fonts, light/dark mode, TalkBack, rotation, repeated navigation,
search highlighting, rename/admin policy, native detail pages, build-number taps,
reduced motion and battery saver. This change advances only the Coral manifest;
Flame's source pin and device/kernel/vendor trees are unchanged.

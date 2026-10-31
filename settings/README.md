# HeckerOS Settings design

Adapted to the native Android 17 Settings app at the revision recorded in
`integration/source-revisions.json`. The latest reference defines a black homepage,
charcoal rounded groups, coloured icon discs, inset separators, title-only rows and
a floating Search pill. The HeckerOS hero, maintainer/Suite row, Suite grid and
Display & Personalization hero remain. Light mode uses corresponding light surfaces.
No reference battery, storage or account readings are hard-coded. See
[the Settings and depth lockscreen notes](../docs/settings-depth-lockscreen.md).

All 23 native home categories and their controllers remain present. Native search,
embedding IDs, badges, work-profile behavior, accessibility labels, display
controls and policy handling remain in Settings. Source adapters are guarded and
reversible; the main activity/fragment names stay native.

Suite retains its random offline Lottie greeting and the previously selected
App Lock, Gaming, infrared camera and About pages alongside the new grid.

The Suite dashboard and its own About UI are built as `HeckerSuiteSettingsUi`, a static
library of the native `Settings` APK. The existing Motion Sense controls and
their child pages are linked through `HeckerMotionSenseSettingsUi` in the plugin
namespace. The separate Motion Sense settings APK and its package allowlist are
excluded from the coral product. There are no additional launcher icons or
injected duplicate entries under System.

The separately installed, privileged `HeckerSuite` system package retains Gaming,
camera and other feature services under its own UID. Settings reaches its private
feature pages through an allowlisted router protected by a signature permission;
the router cannot select arbitrary classes or bypass native App Lock authentication.
3D Face Unlock opens Android's existing Face Settings page for both enrollment and
management, retaining the native credential, removal and biometric flows.
Its enrollment overlay is now Hecker's native curved 25-cell mesh, with translucent
accent capture feedback and a brief missing-angle cue. The Pixel HAL bucket order,
camera buffer/cutout, accessibility help and native completion gate are preserved.
The mesh renders only for callbacks or bounded animations and respects reduced
motion. The original Pixel rendering archive remains pinned for provenance; the
new mesh does not instantiate its renderer.
Coral and flame select that mesh on either preview path. The camera-ownership
resource still selects the existing camera controller, so a Camera2 preview does
not silently replace the pose grid with the generic progress ring.

Asset: `res/drawable-nodpi/hecker_mascot.webp`, also shared by Suite. Generated
with the built-in image generation tool, transparent-background mode, from the
approved caracal brand reference. Final prompt direction: a friendly polished 3D
caracal mascot in a black suit, white shirt, black tie and sunglasses, upright
bust, slight three-quarter pose, subtle cool blue rim light, isolated on a fully
transparent background, no text, no logo, no card or UI baked into the art.
The generated PNG was losslessly converted to WebP; no pixel editing was used.

Phone screenshot comparison, font-scale testing and the complete Settings APK
build remain part of coral validation. Resource compilation alone is not that test.

## Native About Phone

`MyDeviceInfoFragment` hosts the reference About home: a rounded HeckerOS card,
editable device name, Android/processor tiles, joined Software information and
Device information rows, and a centred Credits & legal footer. Its compact native
toolbar retains back and search. This page has its own light/dark palette; the
Hecker Suite dashboard is unchanged.

The card uses the media player's shared `MediaGlowSpec`: three blue/lavender
gradients with the same absolute-time 7.2-second cycle, geometry and colours.
Only the light moves. It is clipped behind the card's text and small outline
caracal. Drawing stops when hidden, unfocused or detached, when the page pauses,
in battery saver, and with animations or Living Flow disabled.

Native controllers still own device-name editing, Android and processor details,
legal pages, identifiers, search and the build-number developer-options flow.
Device information opens the original information categories, including the
partial Catalyst hierarchy. Search results expose that native hierarchy so keys
remain reachable. Model, Android, processor, maintainer and ROM version are read
from the device/build; the reference screenshot's values are not product defaults.

See [the About reference implementation and validation notes](../docs/about-phone-reference.md).
`python3 tools/render_about_phone.py` produces a short source preview and light/
large-font stills. Its fonts and Canvas compositing are approximations; it is not
a recording of Settings running on Coral.

The Official badge and Suite About page both read `ro.heckeros.build_type`.
Coral sets its release identity before inheriting platform configuration. The
vendor/custom adapter keeps HeckerOS away from the inherited PixelOS OTA channel;
an Official maintainer build does not imply an OTA feed or a certification verdict.

Device validation still needs normal/large fonts, dark/light mode, TalkBack,
page exit/return, rotation, live device-name changes and the existing native controls.

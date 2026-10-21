# Approved HeckerOS Settings design

Adapted to the native Android 17 Settings app at the revision recorded in
`integration/source-revisions.json`. The user's three-screen reference defines
the dark navy cards, blue icon discs, caracal branding, maintainer row, Suite
grid and Display & Personalization hero. Light mode uses corresponding native
light surfaces. No reference battery, storage or account readings are hard-coded.

All 23 native home categories and their controllers remain present. Native search,
embedding IDs, badges, work-profile behavior, accessibility labels, display
controls and policy handling remain in Settings. Source adapters are guarded and
reversible; the main activity/fragment names stay native.

Suite retains its random offline Lottie greeting and the previously selected
App Lock, Gaming, launcher, infrared camera and About pages alongside the new grid.

The Suite dashboard and About UI are built as `HeckerSuiteSettingsUi`, a static
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

`MyDeviceInfoFragment` hosts one `HeckerAboutPhonePreference` above its original
information categories. The previous account/avatar header stays hidden; device
name editing, identifiers, legal/regulatory information, search and the build-number
developer-options flow keep their native controllers, including the partial
Catalyst hierarchy. The new card uses the same light/dark Hecker palette.

The compact phone illustration is a native animated view, with no downloaded
assets. It stops when hidden, detached or the page stops and respects the system
animator setting. Model, codename, Android, processor, kernel, maintainer and ROM
version come from this build. RAM and data-partition usage are queried off the UI
thread; the battery value follows the battery broadcast only while the page is
started. Unknown values are displayed as unavailable. Data storage means the
data filesystem, not an invented retail storage capacity.

The Official badge and Suite About page both read `ro.heckeros.build_type`.
Coral sets its release identity before inheriting platform configuration. The
vendor/custom adapter keeps HeckerOS away from the inherited PixelOS OTA channel;
an Official maintainer build does not imply an OTA feed or a certification verdict.

Device validation still needs normal/large fonts, dark/light mode, TalkBack,
page exit/return, rotation, live battery changes and the existing native controls.

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

Asset: `res/drawable-nodpi/hecker_mascot.webp`, also shared by Suite. Generated
with the built-in image generation tool, transparent-background mode, from the
approved caracal brand reference. Final prompt direction: a friendly polished 3D
caracal mascot in a black suit, white shirt, black tie and sunglasses, upright
bust, slight three-quarter pose, subtle cool blue rim light, isolated on a fully
transparent background, no text, no logo, no card or UI baked into the art.
The generated PNG was losslessly converted to WebP; no pixel editing was used.

Phone screenshot comparison, font-scale testing and the complete Settings APK
build remain part of coral validation. Resource compilation alone is not that test.

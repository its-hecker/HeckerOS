# Source and asset provenance

| Component | Immutable source |
| --- | --- |
| PixelOS Android 17 manifest | `PixelOS-AOSP/android_manifest@c16cfd0c95c5c3d7f5f2ef85b5b3140787db2035` |
| Framework/SystemUI | `PixelOS-AOSP/android_frameworks_base@81b912ee97d9d4673ed4696bde30f14841b26c99` |
| Native Settings | `PixelOS-AOSP/android_packages_apps_Settings@9c2cdaad64508ddce1a2cc55a2ebad422bcac818` |
| PixelOS product configuration | `PixelOS-AOSP/android_vendor_custom@e389a1e2caec0cd1e8dc5a5459b88cc2bad38897` |
| Axion PIF native virtualization | `AxionAOSP/android_frameworks_base@a04d222e97d60c03e28479adf1cf39dd5002827a` |
| Axion GameProps and TrickyStore/keybox source snapshot | `AxionAOSP/android_frameworks_base@2017624b588861735445868ca809260bf2499f58` |
| Infinity-X Launcher | `https://gitea.com/projectInfinity-x/packages_apps_Launcher3@8a89db313ebfafc80572795e613679050095d5db` |
| Launcher dynamic colors | `RisingOS-Revived/android_frameworks_libs_systemui@bd7bae87451e7820535570e6a3499376dc928bf9` |
| Independent Google Smartspace client (Chaldeaprjkt) | `RisingOS-Revived/android_packages_apps_Launcher3@e2331fb7bc34fa33ecbe6040615457324a9ea60d:seraphixgoogle` |

Theme resource origin, catalog revision, every asset URL and SHA-256 are recorded
in `themes/payload.lock.json`. Original resource authors remain in the bundled
catalog. The native Suite picker replaces the Android 16 AxThemeStore UI; it
does not require Axion-specific Compose libraries. ThemeEngine sources retain
their original AxionOS license header and are adapted to Android 17/current user
handling. Their Lunaris framework source is recorded in `themes/ORIGIN.json`.

Infinity-X lock-screen lyric source and the source changes are recorded in
`framework/lyrics-origin.json`. Network lookup is opt-in and executes in SystemUI;
Suite's NetLab discovery now requests Internet/local-network socket access;
lyric lookup remains in SystemUI. This integration does not import an entire
unrelated ROM framework or its dynamic bar.

Motion Sense and 3D FaceID plugins are pinned in `integration/floral.xml`.
The hardware repositories carry `UPSTREAM_SOURCE.json`; original PixelOS repos
are unchanged. Existing Google Camera packaging and original signed APK-set
checksums are retained in `prebuilts/google-camera`.

The native Pixel pose-grid renderer, Axion integrity/game/keybox adaptations and
validation boundaries are documented in `docs/face-integrity.md`. The Settings
renderer and framework fork carry their own source/byte provenance. The 87
remaining upstream platform revisions are locked in
`integration/upstream-platform-lock.json`; copying them into new forks is optional.

Generated Settings artwork and its prompt/mode are documented in
`settings/README.md`. The approved boot storyboard is implemented by generated
registered caracal keyframes plus separately rendered ring/typography; see
`bootanimation/README.md`. GitHub Actions generates the checked-in ROM ZIP and
preview from that source. No image generation or rendering runs on the phone.

OmniJaws is not shipped, synced or initialized. The Infinity-X launcher base uses
its own media/event implementation; the independently licensed Chaldeaprjkt
Google widget client retains the previous Google weather integration. Launcher
settings, name, adaptive caracal icon and animation are branded for HeckerOS.
Original source licenses and credits are retained.

Sandboxed-app restrictions retain AxionOS attribution and use the immutable SDK
and Infinity-X UI revisions in `framework/sandbox/ORIGIN.json`. The scope and
native ProcessList adaptation are documented in `docs/device-features.md`.
LibrePods is a separate, original-signed GPL-3.0-or-later app; its exact release,
corresponding source and APK hash are in `prebuilts/librepods/source.json`.
Recovery's five caracal images reuse the approved Hecker mascot and match the
pinned PixelOS recovery's resource format; see `recovery/README.md`.

HeckerNetLab UI, original network-hub Lottie, controller, policy and discovery code
are original Apache-2.0 sources. The supplied modified NetCut APK was inspected
statically for metadata and feature-flow inspiration only; none of its code,
native libraries, artwork or executable assets are redistributed. See
`docs/network-lab.md` and the reproducible APK audit metadata.

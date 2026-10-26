# HeckerOS Pixel 4 (Flame) bring-up

Flame uses the same `feat/suite-polish` feature implementation as Coral. Its
`hecker_flame` product retains every Coral product inheritance: Motion Sense,
hardware 3D Face Unlock, Google Camera, LibrePods, native Suite/SystemUI/Settings,
Hecker Launcher with Pixel Recents, GameSpace and popup apps, Dolby, charging/AOD,
lyrics, customization, NetLab, Spoofing hub, development ADB and boot/recovery art.
Existing Coral limitations remain applicable; source parity is not phone acceptance.

## Source layout

| Component | Flame selection |
| --- | --- |
| Device repository | `its-hecker/heckeros-floral-devicetree`, branch `flame` |
| Shared hardware path | `device/google/coral` (contains `flame/` and `coral/`) |
| Vendor | `its-hecker/HeckerOs-Flame-vendor` at `vendor/google/flame` |
| Kernel | Same Coral `feat/game-booster-reference` pin and KernelSU gitlink |
| Product | `hecker_flame-hecker_suite-userdebug` |
| Display | Original Flame 1080x2280, 440 dpi |
| Manifest | `its-hecker/HeckerOs-Flame-localmanifest`, branch `main` |

Flame retains its own device overlays, power/thermal/audio/radio configuration,
CHRE namespace and stock fingerprint. The camera module is suffixed `-flame`,
while its executable stem and init/VINTF identity remain stock. The eight replaced
FaceID support modules use the same install-ownership fix as Coral. All proprietary
Flame files and radio pointers are preserved from the exact upstream snapshot.

The build helpers select vendor, overlays, policy, radio verification and camera
output checks together. They reject a manifest/vendor mismatch before LFS fetching
or preparing the source adapters. Coral remains the default in the shared helper;
the dedicated Flame helper defaults to Flame. The two devices share the feature
implementation and do not have separate copies of feature patches.

## Fresh checkout and build

Use a separate source directory so existing Coral build work stays available.
Run in the provider's initialized build/Byobu session to preserve its RBE settings.
Install the same Android 17 prerequisites as for Coral, including Git LFS, Python
3.12+, Java 21+, Pillow and numpy.

```bash
mkdir -p ~/heckeros-flame
cd ~/heckeros-flame
repo init -u https://github.com/its-hecker/HeckerOS -b feat/suite-polish --git-lfs
git clone --branch main https://github.com/its-hecker/HeckerOs-Flame-localmanifest .repo/local_manifests
repo sync -c -j8 --fail-fast --no-clone-bundle --no-tags
HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

For later builds after source setup:

```bash
HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

The helper selects `hecker_flame-hecker_suite-userdebug`, then runs `m heckeros`
and checks the installed output under `out/target/product/flame`. The resulting
ROM is a Flame image; use the device-specific build rather than a Coral OTA.

## Validation status

Host checks cover GNU Make product inheritance/identity, target/overlay/vendor
routing, rejection of mixed targets, existing integration regressions, manifest
pins and the unchanged proprietary input set. They do not compile or boot Android.
The first full Soong build and Flame phone tests remain required.

After first boot, verify display/touch/brightness/refresh, cameras and infrared,
3D Face Unlock enrollment/authentication, Motion Sense, mobile radio/Wi-Fi/Bluetooth,
audio, thermal behavior, charging/bypass, fingerprint identity, launcher/Recents,
Quick Settings, GameSpace/popup apps, AOD charging and Suite feature settings.
Capture build errors and boot/logcat evidence before diagnosing device regressions.

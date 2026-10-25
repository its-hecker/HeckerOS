# HeckerOS · Android 17

Hecker's custom PixelOS-based ROM for the Google Pixel 4 XL (**coral**).
The first integration targets coral only. Pixel 4/flame support will follow after
coral testing. Integration and hardware repositories use `main`; the owned
platform customization forks use `heckeros-17`.

## Included source

- The approved navy Settings UI: caracal hero, maintainer row, grouped native
  categories, a two-column Hecker Suite dashboard, and Display & Personalization.
- One native About Phone card with a small animated phone, live device details,
  maintainer/community access and an Official badge tied to build properties.
- Native Quick Settings with large Wi-Fi/Bluetooth cards, round tiles, media,
  separate volume/brightness controls, and the optional HeckerOS dashboard layout.
- Connection popups for Bluetooth, wired and USB headphones, including AirPods
  and Soundcore R50i; original-signed LibrePods for optional AirPods controls.
- Axion/Infinity-X sandboxed-app restrictions with per-user network/storage
  controls and native app-data isolation; system/shared-UID packages are excluded.
- HeckerOS recovery with the approved caracal logo, readable Suite text/buttons,
  native Settings icons, and Simplified Chinese custom-page translations.
- An animated native face-unlock island on the portrait lock screen, with a
  per-user switch and no change to authentication or unlock timing.
- Hardware 3D Face Unlock and Motion Sense, with their ROM integration owned here.
  Face enrollment uses Hecker's curved 25-cell pose mesh, subtle capture feedback
  and missing-angle guidance over the native infrared preview.
- Suite and Motion Sense pages built directly into Settings. Feature services
  retain their separate system UID; there are no extra launcher apps to install.
- Native App Lock, infrared camera viewer, and compact OEM-style **Game Space**
  with a circular real-task FPS gauge, battery telemetry, red accents, game/app
  tabs, capture controls and native floating app shortcuts. Performance profiles
  are exposed only when the Android game service supports the active game.
- AOSP Android 17-based Hecker Launcher with native home customization, alongside Pixel Launcher as the single Recents/Quickstep provider.
- Native Network Lab: bounded LAN discovery, capability-gated phone-hotspot Jail/restore,
  passive diagnostics, local traffic and an original network-hub Lottie. The separate
  controller uses no end-user root; speed enforcement and router adapters are not
  implemented. See [NetLab behavior, limits and phone tests](docs/network-lab.md).
- Opt-in synchronized lock-screen lyrics adapted from Infinity-X. The settings
  page discloses the LRCLIB artist/title lookup before it is enabled.
- Offline pickers for 21 pinned Wi-Fi and battery styles under Status Bar,
  plus an original cyan/violet energy-orbit charging animation.
- Offline random Lottie greeting, one About/donation/contact page, the existing
  Google Camera APK set, and the approved small caracal boot animation.

These are integrated sources. Suite Java/resource compilation, host checks,
immutable-source patch checks and asset validation do **not** establish that the
full ROM builds or boots. A full Soong build and coral hardware tests are required.

## Sync and build coral

Use a fresh Android source directory with the normal AOSP build prerequisites,
Git LFS, Python 3, Pillow and numpy. `repo sync` also initializes the kernel's
existing KernelSU submodule. The dedicated local manifest supplies exact hardware,
plugin, Settings and framework revisions. The remaining 87 platform projects use
their existing upstream repositories at exact commit pins. Forking them is
optional and is not a build prerequisite.

```bash
repo init -u https://github.com/its-hecker/HeckerOS -b main --git-lfs
git clone https://github.com/its-hecker/HeckerOs-localmanifest .repo/local_manifests
repo sync -c -j8 --fail-fast --no-clone-bundle --no-tags
bash .repo/local_manifests/build.sh
```

OmniJaws is excluded from the ROM and source sync. When upgrading a checkout
that previously included it, preserve its locally adapted files before syncing:

```bash
git -C .repo/local_manifests pull --ff-only
python3 .repo/local_manifests/tools/retire_launcher.py
python3 .repo/local_manifests/tools/retire_omnijaws.py
repo sync -c -j2 --fail-fast --no-clone-bundle --no-tags vendor/hecker/floral/suite packages/apps/Launcher3 packages/apps/Settings kernel/google/msm-4.14
HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

Run this from the existing Android source root. The retirement helpers preserve
old checkouts and local edits under `out/retired-projects/`. The build helper
uses `installclean` when retired app files are still staged. Pixel Launcher is
installed alongside the AOSP Hecker Launcher HOME app and remains the sole
Recents/Quickstep provider. ServerHive builds retain the provider's
RBE environment and should run in the initialized Byobu build session.

See [Suite polish and upgrade checks](docs/suite-polish.md) for the current
feature branch, canonical settings locations and device verification list.

The script prepares and verifies the plugins, GCam and theme payloads, applies
guarded source adapters, then selects `hecker_coral-hecker_suite-userdebug` and
runs `m heckeros`. Set `HECKER_BUILD_JOBS` to control build parallelism. The ROM
ZIP is named `HeckerOS_*.zip`. No stock PixelOS OTA channel is configured.
Official identifies the maintainer's HeckerOS build channel. It does not establish
a tested release, production signing keys, an OTA feed or a certification result.

This development branch enables USB debugging and accepts every connected PC on
`userdebug`/`eng` builds. For the final build, restore PC authorization with
`HECKER_DEV_USB_ADB=false HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh`.
Rebuild and flash to apply that change. See [development ADB](rom/adb/README.md)
for property checks and dirty-flash behavior.

Do not also copy `snippets/heckeros.xml` into `.repo/local_manifests`; it is the
source template for the dedicated manifest, and would duplicate its projects.

## Repositories

- [Coral local manifest and build tools](https://github.com/its-hecker/HeckerOs-localmanifest)
- [Hardware device tree](https://github.com/its-hecker/heckeros-floral-devicetree)
- [Coral vendor](https://github.com/its-hecker/heckeros-coral-vendor)
- [Floral kernel](https://github.com/its-hecker/heckeros-floral-kernel)

Device, vendor and kernel came from the existing tested PixelOS coral sources.
`UPSTREAM_SOURCE.json` in each repository records the exact import. Original
hardware identity, firmware and kernel code are retained. ROM products, feature
policy, Google Camera packaging, boot diagnostics and branding live in this repo.

## Verification and credits

```bash
bash tests/run_host_tests.sh
python3 tools/compile_sdk.py --cache generated/sdk
python3 tools/check_launcher.py --sdk-cache generated/sdk
python3 tools/check_about_phone.py --sdk-cache generated/sdk
python3 tools/check_motion_manifest.py
python3 tools/check_native_ports.py
python3 tools/check_spoofing.py
python3 tools/freeze_upstream.py --check
python3 integration/tools/validate.py
python3 -m unittest discover -s integration/tests -p 'test_*.py'
python3 tools/fetch_theme_assets.py
python3 tools/build_bootanimation.py --check
```

See [Suite behavior and phone checks](docs/Suite.md),
[source provenance](docs/Origins.md), [Settings design](settings/README.md), and
[boot animation](bootanimation/README.md). Preserve upstream licenses and credits;
the Lunaris resource binaries and Google Camera retain their original terms.

Build source policy (21 October 2026)

Native Settings includes the original Pixel pose-grid renderer and Axion-derived
PIF, game profile, keybox import and TrickyStore controls adapted to Android 17.
Photos retains the PixelOS implementation. Settings, frameworks/base,
vendor/custom and vendor/lineage use owned `heckeros-17` branches. The other 87
projects remain upstream and are pinned. Our customizations are retained without
copying those repositories. PixelOS changes require an explicit pin update,
review and rebuild. See [migration/README.md](migration/README.md) for optional
full ownership and [docs/face-integrity.md](docs/face-integrity.md) for configuration
and validation limits. Full platform compilation, coral testing and an actual
Play Integrity verdict remain required.

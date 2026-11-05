# HeckerOS · Android 17

## Penguin integrity integration — 5 November 2026

The current fix/device-feedback-20261105 build track includes the completed
Penguin PIF, TrickyStore, GameProps and ROM visibility source integration, with
matching native Settings. HeckerLab opens one Play Integrity page; the former
hub component remains a private upgrade redirect. Existing configuration is
preserved, and PropImitationHooks is removed.

See [changes, validation and the required incremental rebuild sequence](docs/penguin-integrity-2026-11-05.md).
Source/SDK/host checks pass. Full Coral ROM compilation and phone validation
remain required; no attestation verdict is claimed.


## VPN startup and fast QS close — 5 November 2026

The same `fix/device-feedback-20261105` branch now corrects the VPN controller's
missing platform-app SELinux service access, retries failed bindings, accepts
formatted Mullvad account numbers, refreshes countries after registration, and
imports readable configurations even when document metadata is unavailable.
The native QS container also stops drawing at the outer shade's zero expansion,
including while its inner media scene is settling. See [cause and validation](docs/vpn-qs-repair-2026-11-05.md).

## Coral device feedback — 5 November 2026

`fix/device-feedback-20261105` carries the latest HeckerVPN base plus the QS/media,
XMorph Home clocks, shared lock-clock motion/position, launcher glass/icons,
Motion Island, status-bar and compatibility corrections. See the
[complete changes and phone checks](docs/device-feedback-2026-11-05.md).
Use the same branch in `HeckerOs-localmanifest`; keep RBE enabled and leave
`HECKER_BUILD_JOBS` unset for the provider's default parallelism. Full ROM and
phone validation remain pending.


## HeckerVPN feature branch — 4 November 2026

`feat/heckervpn-20261104` adds **HeckerLab → HeckerVPN** with provider countries,
manual WireGuard imports, per-profile app routing, a QS tile and an init-owned
kernel controller. Mullvad is the first optional account-based provider; no
server hosting or separate WireGuard app is needed. Full ROM and phone validation
remain required. See [implementation, controls and test scope](docs/heckervpn-2026-11-04.md).

## Coral UI and compatibility — 2 November 2026

**4 November Launcher Kotlin build fix:** use the wallpaper action declared by
pinned Settings instead of the nonexistent `Settings.ACTION_WALLPAPER_SETTINGS`
constant. Previously prepared Launcher sources upgrade in place. The native
wallpaper method now compiles against the locked Android SDK in CI. See
[cause, regression coverage and incremental rebuild](docs/coral-launcher-wallpaper-build-fix-2026-11-04.md).

**4 November HeckerLab and device feedback:** rename the visible feature hub,
stabilize home depth through native Launcher transforms and reloads, retain the
English word clock in AOD, and use native unlock progress for the clock fade.
Add media Hide/Close app controls, common QQS/full-QS blur with an 85% default,
automatic wallpaper extraction from runtime wallpaper events, reliable lyrics
subscriptions, new GameSpace artwork and automatic session shutdown. Motion
Island gains three reference-based characters and adjustable bar height.
See [changes, validation and phone checks](docs/coral-heckerlab-feedback-2026-11-04.md).

**4 November native XMorphClock catalog:** add the 86 upstream clock/date faces
and 47 bundled fonts to Custom clocks, with a recycled picker and large/small/AOD
previews. SystemUI hosts the original renderer directly, shares the existing depth
mask, and uses native lifecycle/settings controls. GPL/source and font notices
are retained. See [implementation and validation](docs/xmorph-clock-native-2026-11-04.md).

**4 November stock/plugin clock depth:** extend depth masking to the native small
and large clock elements, preserving the selected stock or installed plugin clock.
Depth controls and import/extraction no longer enable the Hecker custom clock.
Only clock pixels are masked; AOD, Smartspace, notifications and controls retain
their native rendering. See [implementation, checks and use](docs/stock-clock-depth-2026-11-04.md).

**4 November Settings crash fix:** defer homepage divider configuration until
`onViewCreated()`, after AndroidX initializes its list. The exact crashing and
earlier prepared adapters upgrade automatically. The host regression reproduces
the previous null-list failure and accepts the corrected callbacks across view
recreation; Settings resource/adapter compilation passes. Rebuild and phone
validation remain pending. See [cause and incremental rebuild](docs/settings-home-crash-2026-11-04.md).

**3 November launcher and depth update:** fix the Launcher depth permission gate,
support aligned manual imports and explicit extraction of the current lock
wallpaper, preserve the selected clock, and enable depth for all built-in Hecker
lock clocks. Launcher Studio has new controls for clock height/top spacing,
message/weather position and search height. The settings UI is restyled, duplicate
PixelOS spoof preferences and the Caracal Dawn action are removed, and the QS
dashboard preview uses a reliable vector. See [changes and device checks](docs/coral-ui-depth-feedback-2026-11-03.md).

**3 November boot fix:** the Cyrillic font adapter no longer emits a forbidden
`lang` attribute inside named font lists. This was the Zygote/Typeface preload
failure in the supplied boot logs. Existing prepared checkouts upgrade the exact
old XML automatically. The pinned Android parsers reject the previous output and
accept the correction in host tests; a rebuilt ROM and phone test are pending.
See [boot cause, correction and rebuild](docs/coral-font-boot-fix-2026-11-03.md).

**3 November build fix:** reduce the ROM-visibility helper's stack use to fit
Bionic's 2 KiB limit, migrate the exact previously prepared copy, and add ARM/
ARM64 platform/APEX cross-compilation checks. See [cause and validation](docs/coral-bionic-stack-fix-2026-11-03.md).

This `fix/coral-ui-compatibility-20261102` branch adds independent AOD clock size,
lighter adjustable QS blur, native media visibility/expansion handling, reduced
per-frame work, single Settings card backgrounds, a visible GameSpace icon and
an OFL Google Sans Cyrillic fallback. See [changes and device acceptance](docs/coral-feedback-2026-11-02.md).

**Hecker Suite → App compatibility** retains the four existing switches and adds
an independent **Hide custom ROM** option. The native hooks adapt the reviewed
VoltageOS fixes to a per-app policy. Coral images exclude `addon.d` during the
build, including old staged copies. This disables add-on preservation across ROM
updates. See [coverage, restart behavior and limits](docs/app-compatibility.md).

The matching `HeckerOs-localmanifest:fix/coral-ui-compatibility-20261102` pins the
Suite and framework together. Full Android/SELinux builds and phone tests remain
pending. Pixel Launcher weather still needs Smartspace diagnostics; UBL's crash
and hardware attestation are not established as fixed. The older sections below
retain inherited product and feature documentation.

For an existing Coral checkout:

```bash
repo init -u https://github.com/its-hecker/HeckerOS.git -b fix/coral-ui-compatibility-20261102 --git-lfs &&
git -C .repo/local_manifests fetch origin fix/coral-ui-compatibility-20261102 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

With the job override unset, the helper calls `m heckeros` and Android chooses
parallelism. Existing RBE variables are preserved.


This experimental `feat/statusbar-lyrics-dual-20261029` branch adds
[shared status-bar lyrics and optional dual rows](docs/statusbar-lyrics-dual.md)
on top of [Living Flow clocks, wake blur, Hecker QS and native motion](docs/living-flow.md)
alongside the existing
[native low-brightness refresh protection and exit hysteresis](docs/floral-display-flicker.md)
and complete `feat/settings-depth-lockscreen-20261028` feature set. It includes the
[reference Settings homepage, animated word clock and imported-layer depth clock](docs/settings-depth-lockscreen.md),
[synchronized smoked-glass folders/icons/search, launcher toggles and default Caracal Dawn artwork](docs/launcher-glass-caracal.md),
and [NetLab touch fixes, saved device names and automatic manufacturer enrichment](docs/netlab-ui.md)
on top of the complete Coral root-privacy/astronaut test. Use this same branch in the
[Coral local manifest](https://github.com/its-hecker/HeckerOs-localmanifest/tree/feat/statusbar-lyrics-dual-20261029).
[Brightness/volume percentages in both QS layouts](docs/qs-slider-percentages.md) are included.
[Game Space](docs/game-booster.md) adds battery temperature to its floating pill,
free shortcut movement and a launcher entry with default-on game-icon hiding.
[The modest Caracal Dawn v2 artwork, layered Home wallpaper and weather in all
Clock Studio styles](docs/home-depth-weather.md) are included. Fresh users receive
the depth lock wallpaper and tall 76% clock automatically.
Full ROM and phone validation remain pending; the navigation
freeze is under investigation and is not claimed fixed.

Hecker's custom PixelOS-based ROM for the Google Pixel 4 XL (**coral**) and
Google Pixel 4 (**flame**). Both products inherit the same feature sources. The
integration branch for this Coral experiment is `feat/statusbar-lyrics-dual-20261029`;
the matching manifest also selects experimental device/framework branches.
The matching Flame feature manifest retains its own framework, hardware and kernel pins.

For Flame, use the [dedicated local manifest](https://github.com/its-hecker/HeckerOs-Flame-localmanifest/tree/feat/statusbar-lyrics-dual-20261029)
and [Flame build notes](docs/flame-bringup.md). Its shared hardware checkout remains
at `device/google/coral`, with its own vendor at `vendor/google/flame`.

## Coral Living Flow polish and upstream review

The `feat/living-flow-polish-20261030` branch contains the screenshot-reference
clock/launcher/media/blur updates and a pinned Coral-only PixelOS/Lineage update.
See [Living Flow polish](docs/living-flow-polish.md) and the
[30 October upstream review](docs/coral-upstream-review-2026-10-30.md).
Use the matching root-manifest and Coral local-manifest branches together.
Flame manifests and hardware pins are unchanged.

The same Coral branch now restores [Display double-tap to sleep](docs/double-tap-sleep.md)
on the collapsed status bar and native lockscreen. Both follow the existing
foreground user's Display switch, including live changes, while retaining AOD
wake and native lockscreen state handling.

## Included source

- Russian translations for 1,120 custom UI resources across Suite, Hecker Launcher,
  native Settings and SystemUI. Android's selected language controls the locale;
  see [coverage and validation](docs/RUSSIAN_TRANSLATIONS_2026-11-01.md).
- Manual [Sensor recovery](docs/GAME_PILL_SENSOR_RECOVERY_2026-11-01.md), available
  from Suite and a QS tile, through a fixed system/init action without user root
  access. This requires a new ROM build; physical recovery remains to be tested.
- The reference grouped Settings UI: retained caracal hero, maintainer row, coloured icons,
  floating native Search, and grouped native
  categories, a two-column Hecker Suite dashboard, and Display & Personalization.
- A reference About Phone home with the shared media glow and live device details,
  native software/device destinations and legal pages.
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
- AOSP Android 17-based Hecker Launcher with [Hecker Launcher Studio](docs/launcher-studio.md):
  ten built-in home clocks (including animated Thermal, Stacked and Caracal), colour palettes and a below-dock search bar, Glass Studio,
  separate home/drawer layout and icon controls, local app/shortcut search, native
  App Lock shortcuts and notification-count badges. Includes swipe-down Quick QS
  and double-tap sleep, alongside Pixel Launcher as the single Recents provider.
- Native Network Lab: bounded LAN discovery, capability-gated phone-hotspot Jail/restore,
  passive diagnostics, local traffic and an original network-hub Lottie. Explicit
  rooted Wi-Fi controls add temporary ARP-based IPv4 pause and per-device upload/download
  caps, with Stop/restore and a native watchdog. IPv6, direct local traffic and router
  protections remain outside that backend; phone validation is pending.
  See [NetLab behavior, limits and phone tests](docs/network-lab.md).
- Optional dual status-bar rows with real native insets, and independently enabled
  status-bar lyrics sharing LRCLIB fetching, caching and timing with the lock screen.
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
plugin, Settings and framework revisions. The remaining 88 platform projects use
their existing upstream repositories at exact commit pins. Forking them is
optional and is not a build prerequisite.

```bash
repo init -u https://github.com/its-hecker/HeckerOS -b main --git-lfs
git clone -b feat/statusbar-lyrics-dual-20261029 https://github.com/its-hecker/HeckerOs-localmanifest .repo/local_manifests
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
python3 tools/check_ims_icons.py --sdk-cache generated/sdk
python3 tools/check_spoofing.py
python3 tools/check_spoofing_hub.py
python3 tools/freeze_upstream.py --check
python3 integration/tools/validate.py
python3 -m unittest discover -s integration/tests -p 'test_*.py'
python3 tools/fetch_theme_assets.py
python3 tools/build_bootanimation.py --check
```

Pakistan IMS defaults, status-bar switches, modem limitations and device test
instructions are documented in [ims/README.md](ims/README.md).

See [Suite behavior and phone checks](docs/Suite.md),
[source provenance](docs/Origins.md), [Settings design](settings/README.md), and
[boot animation](bootanimation/README.md). Preserve upstream licenses and credits;
the Lunaris resource binaries and Google Camera retain their original terms.

Build source policy (21 October 2026)

Native Settings includes the original Pixel pose-grid renderer and Axion-derived
PIF, game profile, keybox import and TrickyStore controls adapted to Android 17.
The current [Spoofing hub](docs/spoofing-hub.md) uses Evolution X Android 17
screens, Canary metadata discovery and an original Lottie animation.
Photos retains the PixelOS implementation. Settings, frameworks/base,
vendor/custom and vendor/lineage use owned `heckeros-17` branches. The other 88
projects remain upstream and are pinned. Our customizations are retained without
copying those repositories. PixelOS changes require an explicit pin update,
review and rebuild. See [migration/README.md](migration/README.md) for optional
full ownership and [docs/face-integrity.md](docs/face-integrity.md) for configuration
and validation limits. Full platform compilation, coral testing and an actual
Play Integrity verdict remain required.

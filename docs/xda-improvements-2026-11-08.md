# XDA improvements — 8 November 2026

Source: [Natxus0898/hecker_os](https://github.com/Natxus0898/hecker_os),
revision `e52ebc3b498c8cd82cfe98ea98a6be4117c69088`.
Thanks to Natxus0898 for diagnosing these issues and sharing the fixes.

This port builds on the current Coral `fix/vault-vpn-review-20261107` sources.
The contributor's binary patches target `HeckerOS_flame-17.0-20261026-2156.zip`;
they are not applied to the newer Settings or SystemUI APKs.

| Item | Source status |
| --- | --- |
| 1. Setup wizard/eSIM | Google-signed SIM Manager D.2.1.989982212-google (306110) is pinned in the vendor input. Its five additional product permissions are installed, covering all 14 required privileged permissions. Source integration is complete; setup on a phone remains to be tested. |
| 2. Motion Sense Bridge | The original Google-signed Pixel 4 stub is pinned and imported presigned/preprocessed. The port validator accepts this GApps/Play profile. Native OsloFeedback and Motion Island retain their current implementations. |
| 3. Settings | The current homepage colours use Material You system tokens in both themes. Rows have 32dp side padding; brand/maintainer cards have 16dp outer and 16dp inner padding. Current native controllers, adaptive icons, badges, search and grouped-card decoration remain. |
| 4. Gesture pill | Two mutable product overlays hide Pixel Quickstep's stashed handle and remove only `taskbar_stashed_size`. A single optional switch applies both in one transaction. It is in **Hecker Suite > Status bar > Gesture navigation** and is off on a fresh install. |
| 5. Shade | The three framework `shade_panel_*` colour aliases are transparent, avoiding an extra tinted layer over the existing shade surfaces. |
| 6. Dark theme | The native tile path stays in use. Its fallback reads the effective resource configuration when tapped and retains the Display settings fallback on a rejected request. This also handles automatic/scheduled theme policy. |
| 7. Face enrollment | The Camera2 preview prefers 1440x1080 and other supported 4:3 streams. Translation is calculated from the measured view with optional calibration offsets, rather than copying Flame pixel offsets. Captured grid cells, glow and outlines are stronger. The HAL capture/completion logic is unchanged. |

## APK provenance

Motion Sense Bridge was extracted from Google's
`flame-tp1a.221005.002.b2-factory-38e4f49a.zip` product image.
Its ZIP CRC and original APK signature were verified with Android's ApkVerifier.

* Package/version: `com.google.android.apps.motionsense.bridge`,
  `1.0.254391180.stub`, version code `3`.
* Size: `16797` bytes.
* APK SHA-256: `d8b3b81758f9d308a68a8ff88e3811c1caadc932b7998982138d84f10ca57c05`.
* Google signer SHA-256: `4fa71b2d890e657bbe02ed7511eb7bf35dd78b27aa9ecd28666156f524fee6f3`.

The contributor documents the APKs but does not commit them. The user-shared
`HeckerOS_flame-17.0-20261108-mod.zip` contains the exact newer SIM Manager.
It was extracted using SHA-256-verified payload operations, and Android
ApkVerifier 8.7.3 verified the original v3 signature and expected Google signer.

* Package/version: `com.google.android.euicc`, `D.2.1.989982212-google`, code `306110`.
* Size: `18037540` bytes.
* APK SHA-256: `579f75da460e6a6137a1f2b39e603e20999b4f55c2ec1bde51acc4feac1c6760`.
* Google signer SHA-256: `7e26b6d5802a16799448ad635868f0345d6730310634684c0ae44e7e9f7ea764`.

Both embedded native libraries remain uncompressed and 4096-byte aligned.
The existing product/presigned/preprocessed import is retained. All 14 requested
privileged permissions are covered by the existing product grants plus the five
additional grants. The vendor provenance JSON records this audit. The signed APK
is included in the vendor Git bundle, so publication does not depend on the
temporary TransferNow link. All seven items are now integrated at source level.

## Validation and limits

* Existing Suite Python tests pass; the new gesture controller probe additionally
  checks the atomic overlay pair, failure preservation, opt-out, user isolation
  and target-package validation.
* Complete Suite Java compilation, Android resource linking, native AIDL and
  the native Settings dashboard compilation pass with the locked SDK inputs.
* Both new gesture overlays and the light/dark Settings colour tokens compile
  and link with aapt2.
* The actual Kotlin dark-mode handlers were compiled and exercised through four
  consecutive toggles, effective dark/light configuration changes and a rejected
  toggle that opens Display settings.
* The production face geometry is checked at multiple view sizes, including the
  contributor's 946px Flame preview and the larger Coral layout. Native pose-grid
  geometry and HAL bucket checks remain part of the platform feature checks.

A full Android ROM build and physical Coral/Flame testing have not been run here.
On-device checks include clean setup/eSIM after the APK update, Play Bridge
updates, gesture/three-button navigation, keyboard insets, shade readability with
blur and battery saver on/off, scheduled dark theme, and face enrollment at
different display/font sizes.

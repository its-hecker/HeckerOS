# LibrePods in HeckerOS

The original signed FOSS release of LibrePods provides optional AirPods-specific
protocol support and controls. Open **Hecker Suite → Headphones → AirPods controls**
to configure it. HeckerOS's connection card also works independently for other
Bluetooth audio devices, including Soundcore R50i, and wired/USB headsets.

The generic card displays only battery data Android actually reports. LibrePods
can publish separate AirPods component batteries through Bluetooth metadata.
Case-opening detection and proprietary controls are model-specific.

The release, immutable source revision and APK digest are recorded in `source.json`.
`tools/prepare_librepods.py` downloads and verifies that exact APK before a ROM build.
It is not re-signed, and is not linked into HeckerSuite or SystemUI.

LibrePods is licensed under GPL version 3 or later. Copyright and license notices are
retained in LICENSE and the APK. Corresponding source, build instructions and
dependencies are available at:

https://github.com/librepods-org/librepods/tree/89356c08cea3db0b6b54792d9329d09a87cc85fa

When distributing the ROM, preserve this source reference and provide equivalent
access to the corresponding LibrePods source alongside the ROM download.

The Android 17 Bluetooth fix supports the standard LibrePods connection path.
VendorID/Xposed-only features remain optional upstream features. Configure one
popup provider at a time if enabling LibrePods' own popup UI.

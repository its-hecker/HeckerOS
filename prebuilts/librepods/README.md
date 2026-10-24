# LibrePods in HeckerOS

HeckerOS builds a LibrePods FOSS source variant with application ID
`org.hecker.librepods`, signed by the ROM. Open **Hecker Suite → Headphones →
Headphones and AirPods**, or the connection card's details button. Connected
Soundcore R50i and other non-Apple headphones open their own animated device page;
AirPods retain the upstream protocol and controls. The separate application ID
allows an upstream-signed LibrePods installation to coexist.

The connection animation depicts a case opening and earbuds rising when the audio
device connects; it does not claim to detect a physical case opening. The device
page displays only battery data Android actually reports. For R50i/P20i/P25i
(A3949), a bounded foreground RFCOMM status request also reads left/right battery,
charging state and firmware. Battery steps are 20%; this protocol does not report
case battery. Unknown fields stay blank. R50i NC/A3959 is a different protocol and
is deliberately excluded. A failed vendor read falls back to Android metadata.

The immutable upstream revision is recorded in `source.json`. With Python 3.12+,
Java 21+, Git and network access, run `python3 tools/prepare_librepods.py` from the
Suite checkout. The ROM preparation helper runs this automatically. The script
verifies and caches the Android SDK/NDK toolchain, exports that exact source,
applies the changes in `tools/build_librepods.py`, copies `hecker/` and the shared
Apache-licensed `framework/earbuds/HeckerEarbudsView.java`, then assembles the
arm64 FOSS release. Gradle is limited to two workers and a 4 GiB heap. The APK and
recipe/content hashes are saved in ignored `prebuilt/` files, verified on reuse,
and signed by Soong during ROM packaging. `--offline` verifies an existing build.
It is not linked into HeckerSuite or SystemUI.

LibrePods is licensed under GPL version 3 or later. Copyright and license notices are
retained in LICENSE and the APK. Corresponding source, build instructions and
dependencies are available at:

https://github.com/librepods-org/librepods/tree/89356c08cea3db0b6b54792d9329d09a87cc85fa

Hecker additions in `hecker/` and the packet regression/fixture use GPL-3.0-or-later.
The A3949 status layout follows OpenSCQ30 at
https://github.com/Oppzippy/OpenSCQ30/tree/200d9e1657dff67d516ed1b6347f312d7a1b01a7/lib/src/devices/soundcore/a3949.rs.
When distributing the ROM, provide equivalent access to this Suite revision
(including the build script and additions), the pinned upstream source and its
dependencies alongside the ROM download. The GitHub Actions build also publishes
the unsigned APK and its recipe record for review.

The Android 17 Bluetooth fix supports the standard LibrePods connection path.
VendorID/Xposed-only features remain optional upstream features. Configure one
popup provider at a time if enabling LibrePods' own popup UI.

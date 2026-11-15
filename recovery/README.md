# HeckerOS Recovery branding

Recovery comes from PixelOS's `bootable/recovery` at
`410707c908dc61faec79bf9fec2ef17712fde8ce`. Its flashing, wiping, sideloading and verification behavior are retained. The\nPhase 1 presentation layer changes only how progress and logs are drawn.

`res-*/images/logo_image.png` contains HeckerOS branding and the approved caracal
mascot. The five density assets have the same dimensions and RGB PNG format as
the original resources. Regenerate them with `python3 tools/build_recovery_art.py`.

`tools/recovery_port.py` contributes the binary asset replacements to the existing
atomic prepare/backup/revert plan. It accepts only the reviewed original or
HeckerOS asset, and refuses unrelated local artwork. Originals are retained under
`upstream/` solely for exact restoration; they are not installed in the new image.

Coral uses `BOARD_USES_RECOVERY_AS_BOOT`, so the new recovery artwork requires a
rebuilt `boot.img`. It cannot be changed by formatting user data.

## Phase 1: clean installation and ADB sideload UI

`tools/recovery_phase1.py` applies a guarded source overlay to the selected
PixelOS `bootable/recovery/recovery_ui/screen_ui.cpp` and header during the
existing atomic preparation step. The overlay provides:

- **Black background** with the existing HeckerOS caracal Recovery logo (unchanged).
- Centered install/sideload title and real progress percentage, where supplied.
- Thin accent-colored progress track with an indeterminate back-and-forth
  indicator while waiting for an ADB package.
- Text statuses derived from recovery's actual messages (receive, verify, install),
  a three-step label only when those messages have established the stage, and
  optional recovery-provided OTA stage indicators.
- Latest recovery logs arranged in a compact panel at the bottom. The complete
  text buffer and stock recovery menus remain accessible.
- Error screens and menu selection logic are left to the original UI.

The patch applies only to the exact reviewed source shapes. Unrecognized upstream
changes stop preparation rather than forcing an unsafe rewrite. Both `--check`
and `--verify` detect drift, while `--revert` restores the original C++ source
and recovery bitmap assets. The installer and ADB protocol are not changed.

The target is the pinned PixelOS recovery source; CI contract tests use that
exact upstream revision. A new ROM build is required because Coral/Flame use
recovery-as-boot; the new UI is included in `boot.img`. Verify on real devices
before shipping an official build.

## Recovery 2.0: OrangeFox-based interactive recovery branding

The Recovery 2.0 build embeds the pinned Coral OrangeFox ramdisk in Android 17
`boot.img`. The build-time `tools/recovery2_rom.py` overlay now packages the
existing HeckerOS caracal PNG (`recovery/res-xxhdpi/images/logo_image.png`)
directly into the interactive recovery theme. It rebinds the splash screen,
welcome screen, About page and available splash-preview/color-theme logo
resources. The splash keeps its black background; the About page says
**HeckerOS Recovery**. Recovery Lab's visible tool labels are rebranded,
while OrangeFox upstream attribution and functional internals are retained.

The existing **Backup** navigation icon is a backup symbol, not a recovery
brand logo; it stays intact for usability. Noninteractive update/fastbootd
paths continue to use the Android 17 stock recovery and its HeckerOS bitmap.

Branding is installed only when the source asset exists and is a valid-sized
PNG, and the archived XML matches reviewed structures. `verify` checks the
branded image exists in the extracted boot ramdisk, in addition to the
existing recovery/sideload/fastbootd checks.

**This branding work does not implement credential-free FBE decryption.**
Android can decrypt metadata without the lockscreen user data key, but the
remaining user files stay encrypted until valid credential authentication.
Do not add a setting that claims to provide automatic decryption without a
tested cryptographic recovery backend. A real Coral build and boot test are
required to confirm the screenshot appearance and asset scaling.

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

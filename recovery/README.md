# HeckerOS Recovery branding

Recovery comes from PixelOS's `bootable/recovery` at
`410707c908dc61faec79bf9fec2ef17712fde8ce`. Its recovery UI, flashing, wiping,
sideloading and verification behavior are retained.

`res-*/images/logo_image.png` contains HeckerOS branding and the approved caracal
mascot. The five density assets have the same dimensions and RGB PNG format as
the original resources. Regenerate them with `python3 tools/build_recovery_art.py`.

`tools/recovery_port.py` contributes the binary asset replacements to the existing
atomic prepare/backup/revert plan. It accepts only the reviewed original or
HeckerOS asset, and refuses unrelated local artwork. Originals are retained under
`upstream/` solely for exact restoration; they are not installed in the new image.

Coral uses `BOARD_USES_RECOVERY_AS_BOOT`, so the new recovery artwork requires a
rebuilt `boot.img`. It cannot be changed by formatting user data.

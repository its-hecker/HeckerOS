# HeckerOS boot animation

Approved sequence: ring glow, suited caracal appears wearing sunglasses, removes them,
looks at you, winks, then the HeckerOS wordmark and BEYOND ORDINARY. appear.
The intro runs once. A subtle ring orbit then loops until Android finishes booting.
Both parts can exit immediately when boot completes.

The native viewport is 720 × 1200 at 30 fps, centered at its native size on coral's
1440 × 3040 display. The 318 px cat remains small on the panel. The MP4 preview
shows the actual screen proportions at half resolution.

`python3 tools/build_bootanimation.py` regenerates the STORE-format bootanimation.zip,
preview and checksum record. Requires Pillow, numpy, ffmpeg and DejaVu Sans.
`python3 tools/build_bootanimation.py --check` checks the shipped ZIP without dependencies
beyond Pillow and numpy. The ROM consumes the checked-in ZIP; it never renders at boot.

`bootanimation_heckeros` is the sole enabled prebuilt installing
`product/media/bootanimation.zip`. The guarded source adapter selects that package
and disables the inherited PixelOS prebuilt. Package selection alone does not
remove Soong's other install action. Native source checks verify the single
owner against both actual module definitions and check exact patch reversion.

The native boot selector tries the product animation before the APEX default.
On dark boots (`ro.boot.theme=1`), it first tries the dark asset, then the normal
product ZIP. This prevents the built-in Android animation when only our normal
ZIP exists. The selected path is logged. Shutdown and userspace-reboot selection
retain their own paths. A compiled probe runs the actual native selector for
normal, dark, custom, fallback, shutdown and userspace-reboot cases. The build
helper also checks the installed product ZIP against the locked payload hash.

Keyframe source was created with the built-in image generation tool from Hecker's
approved storyboard. Prompt: a registered 4 × 3 sheet of the same realistic suited
caracal on black, progressively removing blue sunglasses, looking at the viewer,
winking once, then lowering its paw. No text or ring baked into the sheet.
Frame nine is intentionally excluded because its wink is on the opposite eye.
FFmpeg optical flow interpolates poses. Ring and typography are rendered separately.

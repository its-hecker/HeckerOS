# HeckerOS terminal boot animation

The approved asset is Hecker's supplied `bootanimation_B_terminal_boot.zip`,
stored in two checksum-pinned binary chunks under `source/` and assembled byte
for byte as `bootanimation.zip` during build preparation. It reveals the suited caracal,
types the HeckerOS wordmark and terminal startup lines, then loops the ring and
cursor until Android finishes booting. The tagline is BEYOND ORDINARY.

The native viewport is 720 × 1200 at 30 fps, centered at its native size on
coral's 1440 × 3040 display. `part0` contains 150 frames (five seconds); its `c`
descriptor lets the intro finish even if Android becomes ready sooner. `part1`
contains 60 frames (two seconds) and uses `p 0` to loop until boot completes.
The supplied PNGs and descriptor are retained without resampling or re-encoding.
`preview-coral.mp4` shows these exact frames on the coral screen at half size.

`python3 tools/build_bootanimation.py --check` assembles the exact ZIP and verifies its locked SHA-256,
size, descriptor, 210 complete PNG frames, dimensions, unique archive entries
and Android's STORE compression requirement. Default invocation also validates;
neither CI nor build preparation renders or re-encodes the animation. Pillow is
the only third-party dependency. `payload.json` records the selected ZIP and
its sequence and both source chunks. The assembled ZIP is ignored by Git; the
old keyframe sheet is retained as historical source only.

`bootanimation_heckeros` remains the sole enabled prebuilt installing
`product/media/bootanimation.zip`. The guarded source adapter selects that
package and disables the inherited PixelOS prebuilt. Native source checks
verify the single owner against both module definitions.

The native boot selector tries the product animation before the APEX default.
On dark boots (`ro.boot.theme=1`), it tries the dark asset, then the normal
product ZIP. Shutdown and userspace reboot retain their own paths. The build
helper checks the installed ZIP against the approved payload hash.

# HeckerOS 2.0 geometric bootanimation

The selected visual is the minimal HeckerOs geometric/glitch intro (ivory + pink),
followed by a smooth, indeterminate pink loading pill moving endlessly left to right
and back. Black background, centered lettering, no fake progress percentage.

Native viewport: **1080 x 2280**, **24 fps**. `part0` has 90 one-time intro
frames (**3.75s**); `part1` has 72 frames and repeats (**3s** per smooth cycle)
until Android exits bootanimation. `p 1` allows Android to end the intro early
if boot is already ready. It never imposes a fixed boot time.

The approved frames are preserved in checksum-locked ZIP chunks in `source/`;
`python3 tools/build_bootanimation.py --check` reconstructs and checks PNG
size, frame completeness, ZIP stored compression, SHA-256, and descriptor.
Build preparation never re-renders artwork. `preview-coral.mp4` previews the
intro and two loading loops.

`tools/heckeros2_logo_source.py`, `tools/heckeros2_package.py`, and
`tools/heckeros2_lock_payload.py` are provenance/recreation tools, not run
on Android or during normal ROM build. The feature is in `product/media` via
the existing `bootanimation_heckeros` prebuilt, shared by coral and flame.

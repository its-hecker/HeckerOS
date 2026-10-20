# Original Google Camera for PixelOS Coral

This product installs the same Google-signed **8.7.250.494820638.44** APK set
tested in the stable Infinity-X Coral build: base plus nine common feature
splits, including the startup JNI library. Tensor-only assets are excluded.
Google's APK bytes and signatures are unchanged.

`ORIGIN.json` pins the Infinity vendor commit. The local-manifest preparation
helper downloads its three committed chunks once, validates every chunk,
the joined archive, its install-group TOC and all ten original APK hashes.
Generated chunks are ignored here. `--offline` reuses a fully verified cache;
Android compilation itself performs no Camera downloads.

`PRODUCT_NO_CAMERA := true` is set before Lineage/PixelOS common inheritance to
exclude Aperture. GoogleCameraCoral overrides both Aperture and the AOSP Camera2
app inherited from handheld_product.mk, including inherited package entries.
The existing Google Camera SELinux domain and hardware rules
are retained. Product defaults grant only normal runtime camera/media/audio
permissions, and the original sysconfig permits the app's hidden API calls.

After compilation, the build helper checks the installed provider binary,
exact init script, VINTF declaration and all ten original Camera APKs. A stale
Camera2 or Aperture installation triggers installclean before rebuilding; compiled
intermediate objects are retained. Camera/flashlight/Face ID still require
testing on the resulting PixelOS build.

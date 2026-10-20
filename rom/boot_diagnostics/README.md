# PixelOS boot diagnostics (experimental branch)

This branch adds a userdebug/eng startup capture because the failed phone boot
has no usable Android ADB connection, saved crash files or pstore records.

The init service starts at `early-fs`, after Coral's first-stage Metadata mount
and before filesystem/checkpoint actions or `late-fs` can block on userdata. It uses the existing AOSP `su` diagnostic domain on
debug builds; the device-wide SELinux setting and ADB authentication are unchanged.
The product does not install the capture on `user` builds, and the script also
checks `ro.debuggable` and skips recovery/fastboot boots.

Logs are stored under `/metadata/pixelos-boot-debug`, independent of `/data` and
its encryption. Logcat rotates through four approximately 192 KiB files;
kernel, property, process and mount snapshots are individually capped. A file
size limit also bounds stderr. Capture requires at least 4 MiB free on Metadata
and stops at `sys.boot_completed=1` or after about five minutes. A normal Android
boot replaces only this capture's known files; booting recovery preserves them.

After testing the resulting ROM, leave it at the stuck animation for two to
three minutes, then reboot to recovery and run on the PC:

```bat
adb devices
adb pull /metadata/pixelos-boot-debug PixelOS-boot-debug
```

ZIP that folder and include the ROM ZIP filename and installation sequence.
This capture collects evidence; it does not claim to fix the startup failure.

The October 17 Android boot capture identified a missing Motion Sense
WRITE_SECURE_SETTINGS privileged permission grant and a FaceProvider HIDL
registration race. This device branch now installs the narrow permission XML
on system_ext, alongside FloralMotionSenseSettings. The matching local manifest
preparation patches the reviewed framework registration method. These source
fixes still require a new ROM build and phone test. The native Face HAL also
crashed in the dirty-flash capture and must be retested separately.

The October 18 running-system report detects zero cameras and no parsed camera
provider init script. The matching vendor diagnostic pin now owns that script
on the selected prebuilt; local-manifest preparation clears the unused source
module's attachment. This branch also lets vendor_init publish
ro.vendor.aware_available, whose denied set operation left Motion Sense
unavailable. Google Camera replaces Aperture using the unchanged signed split
set tested in Infinity-X. Check camera/README.md for its immutable origin.

# Temporary development USB debugging

On this development branch, `userdebug` and `eng` builds enable USB debugging by
default and accept every USB-connected PC without an RSA authorization prompt.
`rom/common.mk` includes `development_adb.mk`, which sets the product properties
`ro.adb.secure=0`, `persist.sys.usb.config=adb` and `ro.heckeros.dev_usb_adb=1`.
The native ADB daemon handles this mode; no framework authorization dialog is
patched. The setting does not enable TCP or wireless ADB, change SELinux, or
enable ADB root by default.

Build the current test image normally from the initialized Android source root:

```bash
HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

For the final build, disable this temporary mode:

```bash
HECKER_DEV_USB_ADB=false HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

This restores `ro.adb.secure=1` and sets `ro.heckeros.dev_usb_adb=0`, including on
`eng` builds. Normal Android PC authorization then applies. A `user` variant
defaults to authenticated ADB and rejects `HECKER_DEV_USB_ADB=true`.
The build helper checks the installed product properties against the selected
mode and rejects an inconsistent image. The flag changes the image: rebuild and
flash it to change authorization on the phone.

After flashing the test build, connect the PC and run:

```bash
adb devices
adb shell getprop ro.adb.secure
adb shell getprop ro.heckeros.dev_usb_adb
```

Expect `device`, `0` and `1`, respectively, without a PC authorization prompt.
With the final-build flag, expect `1` and `0` for the two properties after normal
PC authorization. A previously authorized PC may already be trusted by Android.
On a dirty flash, an existing saved USB-debugging choice can override the image's
USB default; enable USB debugging in Settings if needed. The image's
`ro.adb.secure` value still determines whether PC authorization is required.

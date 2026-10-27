# Temporary HeckerOS boot-permission repair

This package addresses the exact system_server failure listing:

- com.android.launcher3: android.permission.READ_WALLPAPER_INTERNAL
- org.hecker.netlab: android.permission.SCHEDULE_EXACT_ALARM

The permanent fix installs the two grants in their apps' system_ext partition.
This root script can apply an equivalent live repair to an already running bootloop.
It preserves the controller's existing NETWORK_SETTINGS and TETHER_PRIVILEGED
entries and adds only the two missing grants. It recognizes the exact failed
NetLab allowlist and the corrected NetLab-only allowlist by SHA-256. An unfamiliar
file or altered payload is rejected before mounting.

Extract HeckerOS_Boot_Repair.zip on the PC, keeping the HeckerOS_Boot_Repair folder.
With the phone connected and root already available to ADB shell, run:

```text
adb push HeckerOS_Boot_Repair /data/local/tmp/
adb shell "su -c 'sh /data/local/tmp/HeckerOS_Boot_Repair/repair.sh'"
```

If ADB shell itself already runs as UID 0, the second command can instead be:

```text
adb shell sh /data/local/tmp/HeckerOS_Boot_Repair/repair.sh
```

The script enters init's mount namespace if necessary, labels the checked XML as
a system file, and bind-mounts it over the existing NetLab allowlist. The next
automatic system_server restart can then read both grants. Wait 30–60 seconds
without rebooting. The mount is temporary until reboot; install the corrected ROM
for persistence. This package is an ADB repair bundle, not a flashable module.

If the phone still loops, collect a fresh crash snapshot after applying:

```text
adb logcat -b crash -d -v threadtime > crash-after-permission-repair.txt
```

To undo only the live mount:

```text
adb shell "su -c 'sh /data/local/tmp/HeckerOS_Boot_Repair/repair.sh --undo'"
```

The script neither changes global permission-enforcement properties nor removes
apps or app data. Its host tests cover root/namespace checks, unfamiliar files,
altered payloads, SELinux-label and mount failures, and restoration. Android root
namespace/SELinux behavior still needs validation on the actual phone.

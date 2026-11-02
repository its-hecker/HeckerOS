# App compatibility — Coral source track

Hecker Suite → App compatibility replaces the Root manager privacy page. The old
activity component and `hecker_root_private_apps` storage key remain intact so
existing selections survive an upgrade. The current Android user's full installed
app list is searchable by name or package, includes system apps, and opens an
individual app's five independent options. New options default to off.

| Option | Effect |
| --- | --- |
| Root manager privacy | Existing native PackageManager filtering for the four supported manager packages; applies before visibility caches and query-all permission. |
| Allow app downgrade | Permit a lower APK version for the selected package through normal installation. Signature, installer permission, SDK, shared-UID and other validation remains in place. It does not fetch or install an APK. |
| Hide developer options | Report `development_settings_enabled`, `adb_enabled` and `adb_wifi_enabled` as `0` through Android Settings read APIs in that app. Actual device settings stay unchanged. |
| Bootloader spoof | In that app's process, report `ro.boot.verifiedbootstate=green`, `ro.boot.flash.locked=1` and `ro.boot.vbmeta.device_state=locked` using the existing guarded private property-mapping engine. |
| Hide custom ROM | For the selected app, filter common ROM software properties, align cached `Build.TYPE` and `Build.TAGS`, hide selected ROM file/directory markers and proc/SELinux text lines through Bionic, and restrict visibility of the Suite/NetLab packages. Requires backend version 2. |

## Scope and application lifecycle

Only privileged Suite can write the per-user Secure settings. The framework loads
an immutable snapshot on its foreground worker; package and install checks do no
settings I/O under PackageManager locks. PackageManager places the selected bits
in a hidden ApplicationInfo field with copy and parcel transport. Both regular
app binding and package-specific app-zygote preload initialize the policy before
app code. The global zygote, system server, shell and root UIDs are excluded.
Settings reads apply the override before their per-process value cache. Native
boot property virtualization uses fixed keys and private process mappings; no
system property write, bootloader operation or keystore modification is involved.

The old root key plus `hecker_allow_downgrade_apps`,
`hecker_hide_developer_options_apps`, `hecker_bootloader_spoof_apps` and
`hecker_hide_custom_rom_apps` are stored
independently for each Android user. APK code is shared across users: downgrade
consent is required for every installed user. System apps retain the factory
version floor even on a userdebug image. APEX uses its unchanged separate path.
Core system UIDs and Suite itself are visible in the list but cannot be configured.
The custom ROM option is also excluded for SystemUI, Settings and the permission
controller, which need the real ROM configuration.

The native ROM policy starts disabled. The framework explicitly sets or clears
it before package code runs, including app-zygote children. Property access does
not read Settings or a policy file. File wrappers run the original kernel check
first so bad pointers retain `EFAULT` and existing permission errors. Filtered
read-only views preserve close-on-exec and readlink truncation; a failed copy
keeps the original descriptor. Writable and creation handles are not replaced.
No file, partition-device number or on-disk SELinux policy is rewritten.

Separately, the build restricts Lineage/profile service discovery for untrusted
app SELinux domains. That small service policy applies to all untrusted apps;
it does not read a per-app setting. System, shell, platform-app and native service
callers keep their existing access. Root-manager package filtering remains its
own option.

Use **Close app to apply**, then reopen it, after changing a profile. This explicit
action uses the existing privileged force-stop permission for that package and
current user. It can interrupt downloads or unsaved work. Restart the phone if a
retained process/app zygote still carries the old mapping. Ordinary secondary
processes use their package's profile. Packages sharing a Linux process cannot
have independently different process-local answers within that same process.

## Limits

The current ROM advertises `ro.hecker.app_compatibility=2`. Version 1 provides
the original four options; version 2 adds Hide custom ROM. An APK-only update
cannot supply the framework, parcel or installer changes. The page is read-only
when that backend version is missing. This source track updates Coral; it does not
advance Flame's separately pinned manifest.

The ROM hooks adapt the reviewed VoltageOS Bionic and service-manager work,
including its property-iteration, performance and crash-fix commits. Immutable
upstream inputs and attribution are recorded in
[`native-compat/source-lock.json`](../native-compat/source-lock.json). Hecker's
process flag replaces VoltageOS's global app policy and package allowlist file.
The hard-coded partition-device spoofing and its extra SELinux file permissions
are not included.

Developer visibility covers Settings APIs, not arbitrary direct provider queries,
other debugging signals or instrumentation. Boot property overrides do not change
the bootloader, hardware-backed Key Attestation, Play Integrity, boot partitions,
`/proc` or sysfs evidence, or fresh executables started with exec. Native mapping
failure is logged and does not crash app initialization. No attestation key or
banking-app patch is included. UBL compatibility and the previously captured
obfuscated NullPointerException are not established as fixed by these controls.
The ROM option reduces common software signals; raw syscalls, direct property
area access, fresh executables and other unhandled probes can still identify the
base ROM. It does not provide hardware attestation or a Play Integrity result.

A downgrade can retain data that an older application cannot read. Use a matching,
trusted APK and an appropriate data backup. The setting does not bypass Android's
signature validation or make an incompatible APK compatible.

## Validation

`tools/check_app_compatibility.py` checks hash-locked framework files and runs the
production package/privacy policy, the actual installer version-check method and
the client Settings policy against boundary fakes. It checks the bounded native
boot-rule entry point and the copy/parcel/startup integration. These tests do not
stand in for a full framework, SELinux, ART, Binder or phone test.
`tools/check_rom_compatibility.py` applies the actual native adapters to locked
PixelOS inputs, compiles the prepared wrappers and property API bodies against
boundary fakes, executes their policies and checks exact reversion. It also runs
Bionic's real syscall generator for arm, arm64, x86, x86_64 and riscv64 to verify
that public wrappers and private syscall stubs have distinct ownership.

On a matched Coral ROM test all five switches independently and in combinations;
profile persistence, search, disabled/system apps, large fonts and Russian/Chinese;
same-signature downgrade and wrong-signature rejection; factory floor and a second
Android user; native/Java property reads in selected and unselected apps plus ADB;
developer options and actual USB debugging remaining enabled; app process and app
zygote restart, profile disable and reboot. Retest UBL and retain its real result.

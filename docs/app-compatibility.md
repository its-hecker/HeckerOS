# App compatibility — Coral source track

HeckerLab → App compatibility replaces the Root manager privacy page. The old
activity component and `hecker_root_private_apps` storage key remain intact so
existing selections survive an upgrade. The current Android user's full installed
app list is searchable by name or package, includes system apps, and opens an
individual app's four independent options. These per-app options default to off.
Custom ROM visibility protection applies across app processes in backend version 3.

| Option | Effect |
| --- | --- |
| Root manager privacy | Root-manager/terminal catalog plus verified KernelSU Next signer lineage, so randomized package names are covered; applies before visibility caches and query-all permission. |
| Allow app downgrade | Permit a lower APK version for the selected package through normal installation. Signature, installer permission, SDK, shared-UID and other validation remains in place. It does not fetch or install an APK. |
| Hide developer options | Report Settings debugging values as `0`; adapt common native ADB properties and socket listing. The matched kernel refuses that app's exact abstract JDWP connect probe and hides adbd stat without disabling device debugging. |
| Bootloader and attestation compatibility | In that app's process, report `ro.boot.verifiedbootstate=green`, `ro.boot.flash.locked=1` and `ro.boot.vbmeta.device_state=locked` using the existing guarded private property-mapping engine. The same selection authorizes the private TrickyStore backend if enabled and a valid keybox is imported. An explicit Skip wins; keybox access remains limited to selected primary-user UIDs. |
| ROM-wide visibility protection | Enabled by default in backend version 3: filter common ROM software properties, align partition fingerprints/type/tags with the ROM's main device profile and cached `Build.TYPE`/`Build.TAGS`, filter the reviewed file/directory and proc/SELinux markers, and restrict ROM-component package visibility for app processes. There is no per-app switch. |

## Scope and application lifecycle

Only privileged Suite can write the per-user Secure settings. The framework loads
an immutable snapshot on its foreground worker; package and install checks do no
settings I/O under PackageManager locks. PackageManager places the selected bits
in a hidden ApplicationInfo field with copy and parcel transport. Both regular
app binding and package-specific app-zygote preload initialize the policy before
app code. The global zygote, system server, shell and root UIDs are excluded.
Settings reads apply the override before their per-process value cache. Native
boot property virtualization uses fixed keys and private process mappings; no
system property write or bootloader locking is involved. The optional, separately enabled TrickyStore backend can generate/import an attested key only under its scoped configuration.

The old root key plus `hecker_allow_downgrade_apps`,
`hecker_hide_developer_options_apps` and `hecker_bootloader_spoof_apps` are stored
independently for each Android user. APK code is shared across users: downgrade
consent is required for every installed user. System apps retain the factory
version floor even on a userdebug image. APEX uses its unchanged separate path.
Core system UIDs and Suite itself are visible in the list but cannot be configured.
ROM visibility exceptions cover native Settings/SystemUI, the permission controller,
both bundled launchers and Hecker services, which need the real ROM configuration.

The framework enables the native ROM policy for app processes by default, and
clears it for native component exceptions before package code runs, including
app-zygote children. Property access does
not read Settings or a policy file. File wrappers run the original kernel check
first so bad pointers retain `EFAULT` and existing permission errors. Filtered
read-only views preserve close-on-exec and readlink truncation; a failed copy
keeps the original descriptor. Writable and creation handles are not replaced.
No file, partition-device number or on-disk SELinux policy is rewritten.

Separately, the build restricts Lineage/profile service discovery for untrusted
app SELinux domains. That small service policy applies to all untrusted apps;
it does not read a per-app setting. System, shell, platform-app and native service
callers keep their existing access. Root-manager package filtering remains its
own option. Penguin visibility also preserves trusted system/shared-UID callers,
self access and each user's current default launcher.

Use **Close app to apply**, then reopen it, after changing a profile. This explicit
action uses the existing privileged force-stop permission for that package and
current user. It can interrupt downloads or unsaved work. Restart the phone if a
retained process/app zygote still carries the old mapping. Ordinary secondary
processes use their package's profile. Packages sharing a Linux process cannot
have independently different process-local answers within that same process.

## Limits

The current Coral track advertises `ro.hecker.app_compatibility=3`. Version 1
provides the four independent options; version 2 used a per-app ROM switch;
version 3 makes that ROM policy global and removes its per-app control. An APK-only update
cannot supply the framework, parcel or installer changes. The page is read-only
when that backend version is missing. This source track updates Coral; it does not
advance Flame's separately pinned manifest.

The ROM hooks adapt the reviewed VoltageOS Bionic and service-manager work,
including its property-iteration, performance and crash-fix commits. Immutable
upstream inputs and attribution are recorded in
[`native-compat/source-lock.json`](../native-compat/source-lock.json). Hecker's
process flag is now enabled by the global framework policy, retaining explicit
exceptions for native components without a package allowlist file on hot paths.
The hard-coded partition-device spoofing and its extra SELinux file permissions
are not included.

Debugging privacy is scoped to the selected process. A non-privileged, self-only
kernel handshake stores the debugging bit in copy-on-write SELinux credentials;
threads, forks and exec retain it until a new application bind resets it. It adds
no capability or SELinux allow rule. Core UIDs, other UNIX sockets and opt-out
apps retain their normal behavior. The actual developer/USB settings and init
service remain enabled. Direct Binder/USB state and other unhandled instrumentation
can still reveal debugging. Libc property flags do not survive fresh exec.

Boot properties do not lock the bootloader, alter the trusted hardware boot state,
repair an expired certificate or guarantee Play Integrity/Wallet acceptance.
An enabled TrickyStore configuration with valid imported private keys is required
for certificate adaptation. The reported vendor `-10003` and standard unavailable
KeyMint/provisioning errors can fall back only for selected ordinary RSA/EC
attestations. Authentication-bound/device-encryption-critical keys, device IDs,
explicit attestation-key aliases, unsupported algorithms and probe aliases stay
native. Explicit Skip, missing keyboxes, unrelated errors and unselected UIDs
cannot trigger fallback. Healthy native generation stays on the native path.
The availability cache uses elapsed time; this is not a hardware-damage diagnosis.

No private attestation key or banking-app patch ships. UBL's obfuscated
NullPointerException still needs a fresh device crash report. Raw syscalls,
direct policy-file/property-area access, native symbol inspection (including the
reported stagefright symbol), fresh executables and other unhandled probes can
still identify this ROM. The scoped views reduce specific signals; they are not
a claim that the device is undetectably stock.

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

On a matched Coral ROM test the four switches independently and in combinations, plus the global ROM policy;
profile persistence, search, disabled/system apps, large fonts and Russian/Chinese;
same-signature downgrade and wrong-signature rejection; factory floor and a second
Android user; native/Java property reads in existing and newly installed apps plus ADB;
developer options and actual USB debugging remaining enabled; app process and app
zygote restart, profile disable and reboot. Retest UBL and retain its real result.

`tools/check_kernel_privacy.py` hash-locks the selected kernel and KernelSU files,
executes their actual context parser and debugging helpers with boundary fakes
under ASan/UBSan, and checks LSM ordering and hookup. It covers app/isolated/user
UIDs, manager/root/admin bypass, bounded user copies, exact socket addresses,
credential reset/allocation failure and unsupported flags. It is not a kernel build.

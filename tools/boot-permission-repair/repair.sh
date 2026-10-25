#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Temporary, narrowly scoped repair for the two boot-fatal allowlist omissions.
set -eu
fail() { printf '%s\n' "HeckerOS repair: $*" >&2; exit 1; }
[ "$(id -u)" = 0 ] || fail "Run this script through an already authorized root shell."
case "${1:-}" in ''|--undo) ;; *) fail "Usage: repair.sh [--undo]" ;; esac
repair_dir=$(cd "$(dirname "$0")" && pwd)
repair_target=/system_ext/etc/permissions/privapp-permissions-hecker-netlab.xml
repair_payload=$repair_dir/privapp-permissions-hecker-bootfix.xml
repair_original=ee8f68b3cec540b782f3c8a748725820c7bbd7f783f086155b75d95cf5acc118
repair_fixed_netlab=4457a2fdff6f8b5bd232ab147b6695998d69e9ebff0d0a540ee547e74bf90969
repair_expected=14250767b0af14d42b753b26e7b651e75a09363da152c47b866ce29e270ff72c

# A bind in an app's private namespace cannot repair system_server. Enter init's
# mount namespace first, without changing its services or any policy property.
repair_self_ns=$(readlink /proc/self/ns/mnt) || fail "Cannot inspect the current mount namespace."
repair_init_ns=$(readlink /proc/1/ns/mnt) || fail "Root cannot inspect init's mount namespace."
if [ "$repair_self_ns" != "$repair_init_ns" ]; then
    [ "${HECKER_REPAIR_INIT_NS:-0}" != 1 ] || fail "Root could not enter init's mount namespace."
    command -v nsenter >/dev/null 2>&1 || fail "nsenter is unavailable; use the corrected ROM build."
    export HECKER_REPAIR_INIT_NS=1
    exec nsenter -t 1 -m /system/bin/sh "$repair_dir/repair.sh" "$@"
fi

[ -f "$repair_target" ] || fail "Expected NetLab permission file is absent."
[ -f /system_ext/priv-app/HeckerLauncher/HeckerLauncher.apk ] || fail "Expected Hecker Launcher is absent."
[ -f /system_ext/priv-app/HeckerNetLabController/HeckerNetLabController.apk ] || fail "Expected Hecker NetLab controller is absent."
repair_current=$(sha256sum "$repair_target" | cut -d ' ' -f 1)
if [ "${1:-}" = --undo ]; then
    [ -f "$repair_dir/active" ] && [ "$repair_current" = "$repair_expected" ] \
        || fail "The temporary repair is not active; nothing unmounted."
    umount "$repair_target" || fail "Cannot unmount the temporary repair."
    rm -f "$repair_dir/active"
    printf '%s\n' "Temporary repair removed. Reboot only after installing the corrected ROM."
    exit 0
fi

[ -f "$repair_payload" ] || fail "The repair XML must be beside repair.sh."
[ "$(sha256sum "$repair_payload" | cut -d ' ' -f 1)" = "$repair_expected" ] \
    || fail "Repair XML checksum mismatch; nothing mounted."
if [ "$repair_current" = "$repair_expected" ]; then
    printf '%s\n' "The targeted permission repair is already active."
    exit 0
fi
case "$repair_current" in
    "$repair_original"|"$repair_fixed_netlab") ;;
    *) fail "Unfamiliar NetLab permission file; preserving it without mounting." ;;
esac

chown 0:0 "$repair_payload" || fail "Cannot set the XML owner."
chmod 0644 "$repair_payload" || fail "Cannot set XML access permissions."
chcon u:object_r:system_file:s0 "$repair_payload" || fail "Root cannot apply the required file label."
mount --bind "$repair_payload" "$repair_target" || fail "Root cannot bind the permission XML."
if [ "$(sha256sum "$repair_target" | cut -d ' ' -f 1)" != "$repair_expected" ]; then
    umount "$repair_target" || true
    fail "Mounted XML verification failed; the temporary mount was rolled back."
fi
: > "$repair_dir/active"
printf '%s\n' "Installed the two missing permission grants in init's mount namespace." \
    "Wait for Android's next automatic system_server restart; keep the phone powered on." \
    "This repair is temporary until reboot. Install the corrected ROM for persistence."

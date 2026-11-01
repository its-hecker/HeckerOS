#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Manual Pixel 4/4 XL recovery. No calibration, persist or firmware writes.
mode=${1:---status}
case "$mode" in
    --status|--recover) ;;
    *) echo 'RESULT=invalid_argument'; exit 64 ;;
esac
device=$(getprop ro.product.device)
case "$device" in
    coral|flame) ;;
    *) echo 'RESULT=unsupported_device'; exit 3 ;;
esac

service=
binary=
for candidate in vendor.sensors-hal-2-0-multihal vendor.sensors-hal-2-1-multihal vendor.sensors-hal-2-0; do
    if [ "$(getprop "init.svc.$candidate")" = running ]; then
        service=$candidate
        case "$candidate" in
            vendor.sensors-hal-2-0-multihal) binary=android.hardware.sensors@2.0-service.multihal ;;
            vendor.sensors-hal-2-1-multihal) binary=android.hardware.sensors@2.1-service.multihal ;;
            vendor.sensors-hal-2-0) binary=android.hardware.sensors@2.0-service ;;
        esac
        break
    fi
done
if [ -z "$service" ]; then echo 'RESULT=unsupported_service'; exit 4; fi

sensor_pid() {
    value=$(getprop "init.svc_debug_pid.$service")
    case "$value" in ''|*[!0-9]*) value=$(pidof "$binary" 2>/dev/null) ;; esac
    # Multiple processes or missing PID cannot prove that one HAL restarted.
    case "$value" in ''|*[!0-9]*) return 1 ;; esac
    [ "$value" -gt 1 ] || return 1
    printf '%s\n' "$value"
}
before=$(sensor_pid) || before=
printf 'DEVICE=%s\nSERVICE=%s\nPID_BEFORE=%s\n' "$device" "$service" "$before"
if [ "$mode" = --status ]; then echo 'RESULT=status_only'; exit 0; fi
if [ "$(id -u)" != 0 ]; then echo 'RESULT=root_required'; exit 2; fi
if [ -z "$before" ]; then echo 'RESULT=pid_unavailable'; exit 8; fi

# One bounded, user-requested restart across CLI and Suite callers.
lock="${TMPDIR:-/data/local/tmp}/hecker-proximity-recover.lock"
if ! mkdir "$lock" 2>/dev/null; then echo 'RESULT=busy'; exit 5; fi
trap 'rmdir "$lock" 2>/dev/null' EXIT
trap 'exit 130' INT
trap 'exit 143' HUP TERM
if ! setprop ctl.restart "$service"; then echo 'RESULT=restart_denied'; exit 6; fi
attempt=0
while [ "$attempt" -lt 20 ]; do
    attempt=$((attempt + 1))
    sleep 1
    if [ "$(getprop "init.svc.$service")" = running ]; then
        after=$(sensor_pid) || after=
        if [ -n "$after" ] && [ "$after" != "$before" ]; then
            printf 'PID_AFTER=%s\nRESULT=service_restarted\n' "$after"
            echo 'CHECK=Uncover, cover and uncover the sensor; confirm near/far changes, then retry Face Unlock.'
            exit 0
        fi
    fi
done
echo 'RESULT=restart_not_verified'
exit 7

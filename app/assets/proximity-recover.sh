#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Root-free client for updated HeckerOS. All authorization and init work stay in the ROM broker.
case "${1:---status}" in
    --status) operation=status ;;
    --recover) operation=recover ;;
    *) echo 'RESULT=invalid_argument'; exit 64 ;;
esac
if ! cmd -l 2>/dev/null | grep -q '^[[:space:]]*hecker_sensor_recovery$'; then
    echo 'RESULT=unavailable'
    echo 'Install the HeckerOS build with the built-in sensor-recovery service.'
    exit 4
fi
output=$(cmd hecker_sensor_recovery "$operation") || { printf '%s\n' "$output"; exit 1; }
printf '%s\n' "$output"
[ "$operation" = status ] && exit 0
attempt=0
while [ "$attempt" -lt 25 ]; do
    attempt=$((attempt + 1));sleep 1
    output=$(cmd hecker_sensor_recovery status)
    case "$output" in
        *RESULT=running*) continue ;;
        *RESULT=service_restarted*)
            printf '%s\n' "$output"
            echo 'CHECK=Uncover, cover and uncover; confirm near/far changes, then retry Face Unlock.'
            exit 0 ;;
        *) printf '%s\n' "$output";exit 1 ;;
    esac
done
echo 'RESULT=restart_not_verified'
exit 7

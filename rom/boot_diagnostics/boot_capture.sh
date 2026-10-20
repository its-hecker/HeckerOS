#!/system/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Experimental boot evidence. Read back with recovery ADB; userdata is not used.
set -u
umask 077

[ "$(getprop ro.debuggable)" = "1" ] || exit 0
case "$(getprop ro.bootmode) $(getprop ro.boot.mode)" in
    *recovery*|*fastboot*) exit 0 ;;
esac

# Never write into an unmounted /metadata directory or crowd out encryption/OTA
# metadata. This device's metadata filesystem is small (about 10 MiB usable).
grep -q ' /metadata ' /proc/mounts || exit 1
fs_space="$(stat -f -c '%a %S' /metadata)" || exit 1
set -- $fs_space
[ "$#" -eq 2 ] || exit 1
case "$1$2" in
    ''|*[!0-9]*) exit 1 ;;
esac
free_bytes=$(($1 * $2))
[ "$free_bytes" -ge 4194304 ] || exit 1

capture_dir=/metadata/pixelos-boot-debug
[ ! -L "$capture_dir" ] || exit 1
mkdir -p "$capture_dir" || exit 1
chmod 0700 "$capture_dir" || exit 1
# Only this capture's known files are replaced, at the next normal Android boot.
rm -f "$capture_dir"/logcat.txt "$capture_dir"/logcat.txt.[1-3] \
    "$capture_dir"/logcat-errors.txt "$capture_dir"/kernel.txt \
    "$capture_dir"/properties.txt "$capture_dir"/processes.txt \
    "$capture_dir"/mounts.txt "$capture_dir"/status.txt

# Android mksh uses 512-byte file blocks; this also bounds an unexpected stream
# of logger errors. Each rotated logcat file is smaller than that limit.
ulimit -f 512 || exit 1
{
    printf 'capture_version=1\nstage=early-fs\ninitial_metadata_free_bytes=%s\n' "$free_bytes"
    printf 'kernel=%s\nboot_id=%s\n' "$(uname -r)" "$(cat /proc/sys/kernel/random/boot_id)"
} > "$capture_dir/status.txt"

logger_pid=
stop_logger() {
    if [ -n "$logger_pid" ]; then
        kill "$logger_pid" 2>/dev/null || :
        wait "$logger_pid" 2>/dev/null || :
    fi
}
trap stop_logger EXIT
trap 'exit 0' HUP INT TERM
start_logger() {
    # Four files of roughly 192 KiB each, rather than an unbounded metadata log.
    logcat -b all -v threadtime -f "$capture_dir/logcat.txt" -r 192 -n 3 \
        >/dev/null 2>"$capture_dir/logcat-errors.txt" &
    logger_pid=$!
}
start_logger

attempt=0
while [ "$attempt" -lt 60 ]; do
    dmesg 2>&1 | tail -c 131072 > "$capture_dir/kernel.txt"
    getprop 2>&1 | head -c 65536 > "$capture_dir/properties.txt"
    ps -A 2>&1 | tail -c 65536 > "$capture_dir/processes.txt"
    head -c 32768 /proc/mounts > "$capture_dir/mounts.txt"
    if [ "$(getprop sys.boot_completed)" = "1" ]; then
        printf 'result=boot_completed\n' >> "$capture_dir/status.txt"
        exit 0
    fi
    if ! kill -0 "$logger_pid" 2>/dev/null; then
        wait "$logger_pid" 2>/dev/null || :
        start_logger
    fi
    attempt=$((attempt + 1))
    sleep 5
done
printf 'result=capture_timeout_300_seconds\n' >> "$capture_dir/status.txt"

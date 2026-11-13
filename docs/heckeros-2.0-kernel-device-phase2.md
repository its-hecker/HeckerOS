# HeckerOS 2.0 — kernel/device performance trial (Coral)

This is an experimental, **not yet device-validated**, branch. No measured speed,
FPS, thermal or battery-life improvements are claimed. Do not automatically merge
into `cnb_latest_stable` before full ROM and Pixel 4 XL tests.

## Baseline and changes

- **Kernel / schedutil**: a rejected fast-switch DVFS request no longer consumes
  the update deadline or leaves an un-applied next frequency cached. This changes
  the failure path only; governor selection, energy policy and kernel security
  features are unchanged.
- **Device / Power HAL**: the 1,500 ms LAUNCH hints are narrowed to independent
  320–850 ms windows, preserve energy-aware scheduling during launches and use
  supported (not fabricated) node frequencies. Short GPU-rail/clock prewarm
  remains, with a quicker release; camera and sustained-mode hints are untouched.
  Faster launches are **not assumed**: A/B test cold and warm launches.
- **Device / cpusets**: the foreground group includes CPU4 again (`0-6`) while
  the unrestricted top-app group stays `0-7`. Other cpusets remain untouched.
- **About Phone / identity**: `ro.heckeros.version=2.0` is set only on this
  trial branch. The native Settings About view now uses system Monet/Material You
  accent/neutral tokens, day/night tonal surfaces and MD3 rounded touch targets.
  Navigation destinations, Android build metadata and hardware reporting are unchanged.
  Fixed-color offline previews use **illustrative** palette samples; they are
  not captures of actual wallpaper-derived colors.

## Follow-up efficiency experiment (November 13)

These are **experimental settings requiring an on-device A/B run**:

- **CPU frequency downscale**: `schedutil/down_rate_limit_us` is now 10,000
  instead of 20,000 on Silver/Gold/Prime, while the existing 500 us upward
  limit is unchanged. This is a shorter rate limit, **not proof** of a 2x
  drop in frequency nor a 5x power saving. Revert if rapid navigation janks.
- **Workqueues**: `CONFIG_WQ_POWER_EFFICIENT_DEFAULT=y` lets workqueues
  explicitly declaring `WQ_POWER_EFFICIENT` use unbound workers.
  It does **not** pin all background work to efficiency cores.
- **Kernel diagnostics**: disable unused interactive KGDB/KDB only.
  Retain scheduling stats, dynamic debug, tracepoints, logs, CFI and KernelSU
  needed for the previously reported navigation-pill freeze.
- **Memory**: expand zRAM from 2 to 3 GiB with existing LZ4, 8 compression
  streams, 512M backing-device allowance, and swappiness 100. Confirm actual
  device uses the new size; quantify compression CPU and app reloads.
- **UFS**: switch existing `sda`, `sde`, and `sdf` from CFQ to
  `deadline` (supported by the kernel's I/O scheduler Kconfig), and use
  `read_ahead_kb=128` for sda. Confirm the scheduler took effect via sysfs:
  an unsupported sysfs write can be ignored by init. Leave `/data` F2FS
  mounting, data encryption, discards and runtime queue depth unchanged.
- **TCP**: build BBR with fq pacing and make it the default **only in this test
  branch**. Keep CUBIC and fq_codel compiled as fallback. Verify
  `/proc/sys/net/ipv4/tcp_congestion_control` and
  `/proc/sys/net/core/default_qdisc` on-device: netd/vendor may override
  defaults. A/B Wi-Fi and cellular TCP throughput, RTT, packet loss and battery.
  This does not accelerate QUIC/UDP and will not fix WireGuard/Onic issues.

**Explicitly excluded:** modem/radio sleep tuning. No changes to RIL, IMS,
APNs, modem properties, call reception or idle network connectivity are intended.
Do not modify carrier attach or incoming-call handling to pursue standby gains.

No GPU overclock, GPU undervolt, forced 90 Hz, thermal throttling bypass, LMKD
policy, aggressive background-app killing, KSU/SELinux changes or 4.14 version
bump has been made. The kernel/device efficiency options must be measured before
promoting them to stable. These need separate
measurements before considering more edits. The camera, face and Motion Sense
integration should remain isolated from this trial.

## Static checks

From the Android tree root after syncing the matching **test** manifest:

```bash
python3 vendor/hecker/floral/suite/tools/check_smoothness_2.py --rom-root "$PWD"
python3 vendor/hecker/floral/suite/tools/check_kernel_device_phase2.py --rom-root "$PWD"
```

## A/B phone acceptance criteria

1. Compile full coral userdebug ROM and boot it successfully. Verify About Phone
   reads **HeckerOS 2.0** on both light/dark modes; change wallpapers and ensure
   the About colors change. Test long names, accessibility text scale, search
   results and each detail/navigation action.
2. Capture Perfetto FrameTimeline + CPU scheduler and frequency traces before/after
   using the **same** workloads, battery level and temperature. Compare app
   launches, Settings cold/warm starts, launcher/QS gesture frames, touch-to-frame
   latency, UI jank and CPU frequency residency.
3. Test 60 Hz/90 Hz and adaptive switching, navigation pill visible/hidden,
   Recents/gesture freeze reproduction, camera capture, face unlock, Motion Sense,
   gaming overlay, networking, Wi-Fi and LTE/eSIM.
4. Compare screen-on energy/battery and sustained temperature after at least a
   representative browsing/gaming session. Check background task residency,
   LMKD kills, zRAM swaps, UFS latencies and idle drain.
5. If startup or frame delivery regresses, return to the original stable manifest.
   Keep thermal protection and verified-boot/security policies intact.

The original `cnb_latest_stable` branches remain unchanged until user-approved
promotion. Do not use `repo sync --force-sync` to override uncommitted local work.

# HeckerVPN integration

Upstream: WireGuard/wireguard-tools at `a998407747005ea7e4e0258d96f105c97241e1d3`.
`UPSTREAM.json` records the original source hashes. The GPL-2.0 source and license are retained.

Only `src/wg-quick/android.c` is adapted:

- `auto_su` requires init-supplied root and never invokes a root manager.
- Tool invocations use the fixed `/system_ext/bin/hecker-wg` path.
- The successfully created netid is journaled for interrupted setup cleanup.
- Teardown also runs after interface removal; fixed journal cleanup destroys the
  owned DNS cache/network after interrupted setup.
- WireGuard-app broadcasts are replaced by controller-owned status reporting.

Soong modules install distinct tool names. The separately built HeckerVPN daemon validates
canonical config tokens before invoking this helper; hooks and arbitrary shell fields are rejected.
Android 17 netd/DnsResolver ABI, SELinux and routing behavior require ROM and device validation.

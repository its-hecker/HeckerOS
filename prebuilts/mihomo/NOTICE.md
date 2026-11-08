# Native TCP core

HeckerVPN includes the GPLv3 arm64 C ABI core from official FlClash 0.8.92.
Only the checksum-verified native core is extracted; the release app is not installed.

- Release: https://github.com/chen08209/FlClash/releases/tag/v0.8.92
- C ABI source: https://github.com/chen08209/FlClash/tree/2fbb96f5c1b8a7255c188d9b92a42b03bb608730/core
- Corresponding Mihomo submodule: https://github.com/chen08209/Clash.Meta/tree/c27f82fbdf2a79b1b9ed0dd46d7a50829b168bc4
- Upstream build procedure: https://github.com/chen08209/FlClash/tree/v0.8.92
- Local root integration source: `vpn/native/proxy_core.cpp`, `proxy_config.cpp` and `daemon.cpp`.

The release APK, extracted arm64 ELF and SnakeYAML 2.4 JAR are checksum locked in
`assets.lock.json`. Run `tools/prepare_proxy_assets.py` before Soong. The Floral
build preparation driver runs it automatically. `--offline` requires verified assets.

SnakeYAML is Apache-2.0: https://github.com/snakeyaml/snakeyaml/tree/snakeyaml-2.4
PicoJSON is BSD-2-Clause: https://github.com/kazuho/picojson/tree/v1.3.0

The native worker runs in its own restricted SELinux domain. It exposes no public
control port and accepts no remote executable, filename, ruleset or YAML configuration.

The previous WhiteVPN 1.7.1 core has an Android-only configuration policy requiring
WhiteVPN's local proxy/controller/DNS listeners. It rejects HeckerVPN's generated
listener-free configuration. Official FlClash 0.8.92 has the same C ABI without
that application-specific policy. FlClash 0.9.x uses a different Android API and
is not a drop-in replacement. Keep this version pinned until that API is ported.

The wrapper avoids upstream's getTotalTraffic C export (which returns freed
storage), uses the asynchronous action instead, and checks the TUN release callback.

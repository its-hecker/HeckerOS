# Native TCP core

HeckerVPN includes the native Mihomo/FlClash engine from WhiteVPN 1.7.1, under GPLv3.
Only the native core is extracted. WhiteVPN application code, telemetry, configuration,
server credentials and geodata assets are not packaged.

- Release: https://github.com/WhiteDNS/WhiteVPN/releases/tag/v1.7.1
- C ABI and corresponding wrapper source: https://github.com/WhiteDNS/FlClash/tree/8f08f6e2d732c58bcbb9d13771af4456c039fe46/core
- Corresponding Mihomo source: https://github.com/WhiteDNS/mihomo/tree/05fb40f89637aa27d81b638bff765a6d57797cdb
- Upstream build procedure: https://github.com/WhiteDNS/WhiteVPN/blob/v1.7.1/scripts/build-flclash-core.sh
- Local root integration source: `vpn/native/proxy_core.cpp`, `proxy_config.cpp` and `daemon.cpp`.

The release APK, extracted arm64 ELF and SnakeYAML 2.4 JAR are checksum locked in
`assets.lock.json`. Run `tools/prepare_proxy_assets.py` before Soong. The Floral
build preparation driver runs it automatically. `--offline` requires verified assets.

SnakeYAML is Apache-2.0: https://github.com/snakeyaml/snakeyaml/tree/snakeyaml-2.4
PicoJSON is BSD-2-Clause: https://github.com/kazuho/picojson/tree/v1.3.0

The native worker runs in its own restricted SELinux domain. It exposes no public
control port and accepts no remote executable, filename, ruleset or YAML configuration.

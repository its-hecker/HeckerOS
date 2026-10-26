# NetLab reference layout

NetLab follows the supplied 369.jpg layout using Android views and locally drawn vector icons: black background, charcoal header/cards/footer, white text, an orange speed bar and an underlined tab row. It has exactly **CUT**, **JAIL**, and **OPTION** tabs. There are no Report or Pro tabs.

The header reads **NetLab**, with **EXIT** at the right. A dark, nonmodal popup says exactly `NetLab` on the first line and `We are Hecker` on the second. It appears for 3.5 seconds on first opening; tapping the NetLab title shows it again. A dedicated Scan Wi-Fi / Stop scan button sits below the tabs. It cancels both discovery stages, including a root scan still finishing after ordinary discovery. The footer stays below the scrolling device list and clears system bars, display cutouts and the keyboard.

## Device cards

- CUT lists discovered/remembered devices; the heading counts devices currently observed online. JAIL lists controlled devices, including pending or failed restoration, and counts those entries.
- Each card has a name (or **Untitled**), timer/menu and Wi-Fi action, IP/MAC with clipboard copy, trust/control/timer fields, and a brand/OS footer. Brand names come from a bundled, pinned IEEE assignment snapshot (54,269 MA-L/MA-M/MA-S entries via BSD-licensed oui-data), with advertised model/manufacturer hints retained. Private MAC prefixes are not fingerprinted; absent other hints their label is Private MAC. At narrow widths or larger font scales, IP and MAC move onto separate lines.
- Tap a device name to expand/collapse the orange speed controls. Long press the name, or tap the timer, for its action menu and duration controls. The first discovered Wi-Fi peer starts expanded.
- The Wi-Fi button pauses a currently available device, or restores a controlled one. Routers and offline devices cannot be paused. Native hotspot clients retain their existing native pause route.
- The orange slider proposes a download rate in kbps, opens the existing duration/upload/download confirmation dialog, and applies only after confirmation. The left end opens pause; the right end restores an existing control. Touch, keyboard and TalkBack adjustments share that route. Polling does not replace the card during adjustment.
- The speed bar reflects confirmed state after refresh. A cancelled dialog does not save or apply a new limit. An existing selected peer/group can switch between cut and limit during its running session. The original end time remains fixed. Adding devices outside that selection still requires stopping/restoring and starting a new selection.

## Footer and options

The outlined **MONITOR** button opens network information. It does not claim active ARP protection. The lock restores all devices. The footer Wi-Fi icon is a master pause/restore toggle for the current Wi-Fi peers; it does not scan. Refresh polls state and the globe opens search. A separate fixed orange master speed bar pauses, limits or restores the group. Group actions use the existing duration/limit confirmation, exclude the gateway, this phone, offline/history entries, hotspot entries and other Network scopes, and ignore list search/filter state. Selections above the existing 32-peer backend limit prompt for a smaller selection rather than silently truncating it. Current scan status and relevant controller/session messages remain visible, including **Stop and restore** when needed.

OPTION contains search, filters, multi-device controls, restore all, traffic, network information, diagnostics, advanced root controls, and the existing private history/name settings. The initial list no longer needs a search field, filter spinner or separate More menu. Search/filter state, expanded device and selected tab survive Activity recreation. The Jail view ignores the Cut search/filter so controlled devices remain accessible.

Reference fields that NetLab cannot verify are not fabricated: the card uses **Trusted** and **Control**, and the footer says **MONITOR**. No NetCut code, binaries, images or logos are included. Icons explicitly scale to their density-sized image bounds; the ImageButton CENTER default no longer leaves 24 raw pixels on a high-density phone. Router, Android, Apple, Windows, Linux and media icons follow gateway/service evidence. Recognized manufacturers without OS evidence use a brand monogram (for example Xiaomi MI); truly unknown devices use a question mark rather than a PC monitor. Manufacturer identity is not presented as an OS fingerprint. English and Simplified Chinese resources are provided; branding remains exactly as requested.

## Verification

Android resources link and complete Suite/controller Java plus generated AIDL compile against the locked SDK inputs. The source/privilege checks, existing UI contrast/translation regressions, 30 vendor/parser/private-MAC/source-image checks, and 16 master-selection scope/exclusion/bounds checks pass. The packaged registry hash/count and byte-for-byte rebuild against its pinned source are verified. The follow-up changes below include a typed live-policy Binder operation and native timing/repair fixes. Caller/permission boundaries and device-wide forwarding configuration are unchanged. Controller state exposes its action so a returning Activity can identify a running scan. Full ROM/Soong compilation and phone visual/touch/rotation/large-font acceptance remain pending.

## Offline manufacturer provenance

The public [IEEE assignment listings](https://standards.ieee.org/products-programs/regauth/) define MA-L/MA-M/MA-S prefixes. This build uses a hash-pinned snapshot from [silverwind/oui-data](https://github.com/silverwind/oui-data), version 2.1.46. The exact revision, input hash, output hash, counts and asset path are in `netlab/vendor-registry.lock.json`; the BSD-2-Clause attribution/license ships in `app/assets/netlab/NOTICE.txt`. The Android module declares that additional license.

`python3 tools/update_netlab_vendors.py --check` checks the installed data snapshot. Use `--source /path/to/the/pinned/index.json --check` to prove exact reproducibility, or omit `--check` to rebuild from that verified source. Without `--source`, the maintenance tool downloads only the pinned source URL. The app itself loads the compressed table once off the UI thread and sends no MAC addresses to an external lookup service. Longest-prefix matching checks 36, then 28, then 24 bits. Locally administered/multicast/invalid MACs never produce a manufacturer from the prefix. Existing service-advertised identity can still supply a brand/device-type hint.

## Slider/cut follow-up

- Unchanged taps, cancelled gestures and scrolling interception do not apply a policy. Keyboard/TalkBack adjustments commit after the last change. Pending cut/limit state uses the requested policy rather than appearing enabled, and upload-only limits retain a route to the right Restore endpoint. Mixed groups use a distinct middle position and visible Mixed label.
- The master slider remains available during an editable active session. Live changes use the fixed `lanUpdate` Binder operation, preserve caller/signature/local-network checks, validate the same link and cached IP/MAC membership, and send bounded SET records only for existing native peers. New peers, expired/stopping sessions and bad rates are rejected. Known members remain editable while that session is active even after their old scan timestamp ages out; this does not change their reported discovery/online status. Existing transaction numbers are preserved by appending the AIDL method.
- The helper arms its active duration and heartbeat after root/peer/firewall preparation. Preparation can no longer consume the heartbeat or short session lifetime before the first block. A repeated arm is rejected. Live policy edits do not extend the deadline. Stale per-peer repair jobs are removed on re-cut, and per-peer repairs cannot restore an actively controlled peer.
- Startup timeout and helper errors remain visible with their controller codes. Baselines are emitted before interception and at live policy changes. Cut verification requires new dropped IPv4 bytes under the current policy; limit verification requires newly forwarded bytes. Old cumulative traffic, old-policy replies and counter resets cannot establish success.

After reproducing a failed cut on the phone, capture:

```bash
adb shell dumpsys activity service org.hecker.netlab/.NetLabService > netlab-diagnostics.txt
adb logcat -d -s HeckerNetLab > netlab-log.txt
```

The service dump shows helper action/error/exit, requested/actual modes and directional byte counters. It omits device names and MAC addresses. Logcat records stage/error codes without peer addresses or captured payloads. These are debugging outputs to retain privately. A Requested/Waiting state means effectiveness is unverified. This controller covers selected IPv4 gateway traffic; IPv6 and Wi-Fi association remain outside it.

The source regressions exercise delayed preparation, live limit→cut→limit, unchanged/cancelled gestures, upload-only limits, mixed restoration, old-policy traffic, expiry, re-cut repairs and malformed/unowned updates without sending LAN packets. A physical phone/router cut still needs an owned-device connectivity test; passing host tests is not that confirmation.

Follow-up verification: 540 slider/evidence/expiry checks and 256 native core checks passed, including a multi-device restore→re-cut sequence. The existing 1,156 route-reader checks, guardian cleanup tests, bounded actual-helper preflight, 103 Python regressions and complete Java host suite also pass. The Suite/controller Java, real AIDL and Android resources compile/link against the locked SDK inputs. Full ROM/Soong and phone/router verification remain pending.

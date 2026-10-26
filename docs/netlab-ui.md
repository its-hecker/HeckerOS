# NetLab reference layout

NetLab follows the supplied 369.jpg layout using Android views and locally drawn vector icons: black background, charcoal header/cards/footer, white text, an orange speed bar and an underlined tab row. It has exactly **CUT**, **JAIL**, and **OPTION** tabs. There are no Report or Pro tabs.

The header reads **NetLab**, with **EXIT** at the right. A dark, nonmodal popup says exactly `NetLab` on the first line and `We are Hecker` on the second. It appears for 3.5 seconds on first opening; tapping the NetLab title shows it again. The footer stays below the scrolling device list and clears system bars, display cutouts and the keyboard.

## Device cards

- CUT lists discovered/remembered devices; the heading counts devices currently observed online. JAIL lists controlled devices, including pending or failed restoration, and counts those entries.
- Each card has a name (or **Untitled**), timer/menu and Wi-Fi action, IP/MAC with clipboard copy, trust/control/timer fields, and a brand/OS footer. Unknown identity stays unknown. At narrow widths or larger font scales, IP and MAC move onto separate lines.
- Tap a device name to expand/collapse the orange speed controls. Long press the name, or tap the timer, for its action menu and duration controls. The first discovered Wi-Fi peer starts expanded.
- The Wi-Fi button pauses a currently available device, or restores a controlled one. Routers and offline devices cannot be paused. Native hotspot clients retain their existing native pause route.
- The orange slider proposes a download rate in kbps, opens the existing duration/upload/download confirmation dialog, and applies only after confirmation. The left end opens pause; the right end restores an existing control. Touch, keyboard and TalkBack adjustments share that route. Polling does not replace the card during adjustment.
- The speed bar reflects confirmed state after refresh. A cancelled dialog does not save or apply a new limit. Existing single-session restrictions still require stopping the running Wi-Fi session before starting another.

## Footer and options

The outlined **MONITOR** button opens network information. It does not claim active ARP protection. The lock restores all devices, the Wi-Fi icon scans/stops scanning, refresh polls state, and the globe opens search. Current scan status and relevant controller/session messages remain visible, including **Stop and restore** when needed.

OPTION contains search, filters, multi-device controls, restore all, traffic, network information, diagnostics, advanced root controls, and the existing private history/name settings. The initial list no longer needs a search field, filter spinner or separate More menu. Search/filter state, expanded device and selected tab survive Activity recreation. The Jail view ignores the Cut search/filter so controlled devices remain accessible.

Reference fields that NetLab cannot verify are not fabricated: the card uses **Trusted** and **Control**, and the footer says **MONITOR**. No NetCut code, binaries, images or logos are included. English and Simplified Chinese resources are provided; branding remains exactly as requested.

## Verification

Android resources link and complete Suite/controller Java plus generated AIDL compile against the locked SDK inputs. The source/privilege checks and existing UI contrast/translation regressions pass. This is a presentation change; there are no native relay, Binder, permission, root or firewall changes. Full ROM/Soong compilation and phone visual/touch/rotation/large-font acceptance remain pending.

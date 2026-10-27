# Device feedback follow-up

- Expanded GameDashboard movement now has its own top grip, separate from the existing bottom resize grip. Dashboard, edge-handle and stats-pill placement is bounded and orientation-specific; cancellation, inward-swipe opening and accessible directional movement are covered by regressions.
- Screen-off charging requests retry across doze transitions and pending pulses with a bounded CPU wake lock. Native proximity/power suppression cancels the request, and old asynchronous callbacks cannot cancel a new plug event.
- Oslo glow retains one attached surface, restores native shade holds only while locked, seeds the current doze/pulse state on attachment and refreshes GL on native doze display/tick callbacks. Coral AOD rendering still needs a flashed test.
- Quick QS no longer composes a player or reserves its space. The expanded Hecker/One UI dashboards retain their own players; lockscreen playback keeps its separate host.
- The custom lockscreen clock sits 24 dp lower in the small layout and 32 dp lower in the large layout, with matching measured space.
- Clock Studio controls a built-in first-page home header. The native grid reserves its measured height, and dense layouts hide the clock instead of covering icons. Five palettes, minute/return/tap message rotation and a native below-dock search pill add colour and purpose. Previous widgets remain compatible and can be removed normally.
- Launcher settings use rounded, spaced actions. Clock Studio has one main entry; the dead extra-settings action is removed.
- Charging visuals have independent AOD and brief-ambient switches. Native Always-on keeps its own setting; charging does not force it on. AOD presentation has precedence while Always-on is enabled. The third animation fills the detailed gauge into the actual battery level. Other intros crossfade into information without a black gap.
- Plug negotiation shows Connected rather than prematurely claiming Paused. Positive current or Android's charging status confirms charging; full and confirmed paused states remain distinct. Native burn-in translation is positional, not a scheduled brightness flash.
- NetLab uses full-width growing action buttons and spaced scrolling chips. It collects hotspot DHCP names and bounded, Wi-Fi-scoped mDNS name/model hints. OS labels remain uncertain; unavailable MAC/model data stays unavailable. LAN details do not offer nonfunctional speed controls. Saved rate presets are explicitly labelled as presets.
- GameDashboard has more usable height, follows the new limit for an earlier maximum-height choice and restores CPU/GPU meters to the compact landscape layout. Meters interpolate real samples. Shortcuts defer compatibility resizing to Android and request their own bounded freeform task.
- Settings keybox fetch follows Evolution X's manual-fetch policy: a cryptographically trusted, unexpired chain can be saved with Unknown revocation status when the live service is unavailable. Bad/expired/revoked/suspended/untrusted chains are rejected with the specific reason. Public roots can use a previously fetched cache; successful fetch refreshes Google processes.
- The matching Evolution X Android 17 certificate backend updates are adapted to HeckerOS's private native store. Atomic keybox publication, issuer caching, identity/patch tags and deterministic boot-key fallback are included. HeckerOS retains working Android 17 RSA/EC signing, key/certificate proof, strict complete XML and existing UID/Skip/master-switch protections.

## Limits

This is not a claim that Basic, Device or Strong Integrity passes. Google decides those verdicts;
certificate validation and host tests cannot establish them. No provider keybox is bundled.

NetLab still has no router management or per-peer rate-shaping backend. A normal Wi-Fi client,
even rooted, does not automatically carry other peers' traffic. Supported phone-hotspot clients
can be disconnected through native HAL controls. Router clients need router/AP enforcement.

Host validation includes Suite/controller Java, Android resources/AIDL, Settings Java/resources,
SystemUI Kotlin/Compose, native launcher measurement/layout fragments, immutable adapter
apply/upgrade/revert checks, private configuration isolation and synthetic RSA/EC certificates.
ASan/UBSan checks ran with LeakSanitizer disabled because this host's ptrace/proc restrictions
prevent LSan startup. No native C++ implementation changed in this update.

A full Android/Soong build and Coral testing remain required, particularly brief ambient pulses,
charging transitions, dense launcher grids, app compatibility windows, peer discovery and actual
Play Integrity results. A repeating whole-screen AOD flash after this change needs device logs;
it is not an intentional burn-in effect.

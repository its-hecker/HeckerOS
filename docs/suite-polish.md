# Hecker Suite polish

The `feat/suite-polish` branch builds on the Game Booster reference branch and
keeps its matching kernel pin. It is a source checkpoint; full Android/Soong
compilation and Coral hardware validation are still required.

## Visible changes

- All native Suite pages use a measured toolbar above their scrolling content,
  with status-bar, cutout and keyboard insets applied once. This covers the
  dashboard greeting, App Lock, sandboxed apps and private style pickers.
- Quick Settings has two animated Lottie layout cards. Lock screen, Status bar,
  Headphones, sandboxed apps and screenshot settings have original offline artwork.
  Native Play Integrity settings use grouped cards, an animated shield, readable
  status text and rounded actions; profile import/fetch behavior is preserved.
- Hecker brightness and volume use the same glass track, height and pill corners.
  Native brightness policy and media-volume handling remain in their existing paths.
- The expanded QS header fades as one element during the first 20% of collapse;
  its clock no longer shares a scene element with the compact header. This applies
  to One UI and Hecker layouts. Wide layouts retain their single native footer pair;
  small layouts use the single pair in the header. Security and active-app information
  remains available in the native footer.
- SmartThings opens the Samsung app when installed, otherwise Android's device
  controls/provider setup, with Bluetooth settings as a fallback. Bluetooth uses
  an open vector path with padding so its upper-right stroke is not clipped.
- Bluetooth connection cards animate the case opening and earbuds rising. Their
  details action opens the source-built LibrePods variant, whose non-Apple device
  page supports Android battery metadata and real R50i status/firmware reads.
  AirPods retain upstream controls. See `prebuilts/librepods/README.md` for protocol
  scope and source/build information.
- The native locked-app biometric prompt gains an orbit around the real app icon,
  a short entrance, error feedback and a success check. The trusted biometric
  prompt, credential fallback, cancellation and target-launch callbacks remain native.
  Animations respect the system animator setting and stop when detached/hidden.
- Moto and Nothing charging resources are retired. The new optional energy orbit
  renders directly in the trusted wired-charging window, shows the battery level,
  does not take touch input and cancels on unplug. It respects reduced motion.

## Canonical locations

| Setting | Location |
| --- | --- |
| One UI / Hecker QS | Hecker Suite → Quick Settings |
| Lyrics, face-unlock island, energy charging | Hecker Suite → Lock screen |
| Wi-Fi / battery style | Hecker Suite → Status bar |
| Lock-screen display / always-on display | Settings → Display |
| 3D face enrollment and management | Settings → Security |
| System navigation | Android Settings |
| Protected-window capture switch | Hecker Suite → Screenshots |

The Suite dashboard no longer has separate Theme Pack, Lyrics, 3D Face Unlock,
Navigation, Animations or launcher-selection entries. The style catalog and
product contain only the 21 supported Wi-Fi/battery resources. On upgrades, the
native theme service ignores retired theme categories and clears native icon-map
entries only when their package belongs to the retired bundled payloads; wallpaper
colors, fonts and unrelated selections are preserved.

## Secure screenshots

This exposes Android's existing `disable_secure_windows` setting, default off,
through a signature-protected Suite page. WindowManager now reads the active
user's value, refreshes secure surfaces on changes/user switches and supports the
explicit setting on release builds. When enabled, this also affects screen
recordings of FLAG_SECURE windows. Hardware DRM video may still be blank.

## Upgrade

The PIN bouncer falls back to explicit confirmation when stored PIN length is
unavailable or invalid before the first unlock after a reboot. It keeps entered
digits, shows the confirm button and uses the normal credential verifier. A valid
length restores the configured auto-confirm behavior. A second guard prevents
per-digit credential attempts if length becomes unavailable after the UI loads;
wrong-PIN rejection and lockout handling remain in their existing paths.

Use the companion `HeckerOs-localmanifest` feature branch. Before syncing a
checkout that contains the retired Hecker Launcher, run its updated
`tools/retire_launcher.py`. It accepts the known RisingOS and Infinity-X sources,
preserves tracked and untracked files and refuses unfamiliar repositories or
symlinks. The build helper also invokes it before Soong preparation and clears
staged retired apps only when found. Pixel Launcher remains supplied by the
existing GApps product. No custom launcher or Recents overlay is installed.
Stock AOSP Launcher3 **source** remains pinned and synced because Android's
SystemUI flag library and TAPL test dependencies require its build modules. Its
launcher APKs are filtered out of the product. After updating the local manifest,
sync both `vendor/hecker/floral/suite` and `packages/apps/Launcher3`; this repairs
the missing `launcher-aosp-tapl` and `com_android_launcher3_flags_lib` Soong errors.

## Validation

Motion Sense's original Oslo glow uses one attached, trusted, non-touchable SystemUI
window throughout the plugin lifetime. It does not reparent during gesture callbacks.
The native shade hold is retained while keyguard is showing and released when unlocked.
Late-attached Oslo views receive the current dozing/pulsing state, which the native
callback registration does not replay. Native doze display transitions and minute ticks
request a fresh GL frame without adding a persistent render timer. Gesture eligibility,
colors and minimizer behavior remain native. Host regression checks cover initial AOD
state, pulse/tick redraw, locked-only shade holds, failed windows and disconnect cleanup.
The additional window has its own matching window context, uses Android's
trusted-overlay flag and takes no touch input; window failures retain the native
host. Soli rendering and gestures on HOME/apps still require a Coral hardware test.

The face feedback pill centers its icon and measured label as one group and
centers the text baseline using font metrics, including larger font sizes.

Run the host suite, Suite Java/resource compiler, QS Kotlin/Compose compiler,
Settings UI compiler, integration suite and immutable native adapter checker.
The native check executes the prepared WindowManager method against default-off,
user switching and live surface-refresh cases. Theme migration checks verify
that retained styles, colors and fonts survive and that repeat runs are harmless.
The PIN regression compiles prepared guard/confirm-flow bodies with the actual
input state and base bouncer lifecycle. It covers unavailable lengths, repeated
boot state, metadata refresh, user isolation, wrong PINs and active lockouts using
fake platform storage and credential verification.
The R50i codec regression checks packet fragmentation, length/checksum validation,
model separation, absent data and bounded buffering. The companion page and
Bluetooth reader are compiled against Android resources; CI also builds and
optimizes the complete arm64 LibrePods FOSS release and verifies its recipe cache.

On Coral, verify every page at normal/large font sizes, in both themes and after
rotation; drag both QS sliders; interrupt/reverse collapse gestures; open a locked
app and test success, failure, cancellation and credential fallback; test capture
with the toggle off/on; switch users; plug/unplug the charger; and confirm Pixel
HOME/Recents after a dirty upgrade and reboot. A complete ROM build, on-device
visual comparison and these hardware checks have not been performed in this task.
Also verify correct and wrong PIN entry before the first unlock after reboot,
with auto-confirm both enabled and disabled.
Pair R50i, verify connection artwork, real left/right readings and firmware,
disconnect during a read, reopen LibrePods and test the AirPods route. Confirm
the glow appears on HOME/apps as well as lockscreen/AOD, and check centered
scanning/unlocked labels at the selected font size.

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

Use the companion `HeckerOs-localmanifest` feature branch. Before syncing a
checkout that contains the retired Hecker Launcher, run its updated
`tools/retire_launcher.py`. It accepts the known RisingOS and Infinity-X sources,
preserves tracked and untracked files and refuses unfamiliar repositories or
symlinks. The build helper also invokes it before Soong preparation and clears
staged retired apps only when found. Pixel Launcher remains supplied by the
existing GApps product. No custom launcher or Recents overlay is installed.

## Validation

Run the host suite, Suite Java/resource compiler, QS Kotlin/Compose compiler,
Settings UI compiler, integration suite and immutable native adapter checker.
The native check executes the prepared WindowManager method against default-off,
user switching and live surface-refresh cases. Theme migration checks verify
that retained styles, colors and fonts survive and that repeat runs are harmless.

On Coral, verify every page at normal/large font sizes, in both themes and after
rotation; drag both QS sliders; interrupt/reverse collapse gestures; open a locked
app and test success, failure, cancellation and credential fallback; test capture
with the toggle off/on; switch users; plug/unplug the charger; and confirm Pixel
HOME/Recents after a dirty upgrade and reboot. A complete ROM build, on-device
visual comparison and these hardware checks have not been performed in this task.

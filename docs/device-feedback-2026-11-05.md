# Coral device feedback — 5 November 2026

Integration branch: `fix/device-feedback-20261105`, based on the published
`feat/heckervpn-20261104` source (`b7b7efa`). Use the matching Coral local manifest.
These are source changes; a complete ROM build and physical phone acceptance are
still required.

## Changes

| Area | Result |
| --- | --- |
| Quick/full QS media | Hide dismisses only the Quick QS copy. Full QS retains its player or play-last surface. Close app still stops the selected media application, while the full-QS surface remains available. Resume, a new track or a different playing session restores the compact player. |
| Media controls and text | Replace the bottom action row with a small top-right overflow control, retaining a usable touch target. Reserve separate title/artist line space and move the output control clear of the menu. Both dashboard styles use the same geometry. |
| Fast shade closure | Gate the entire native QS scene drawing layer against actual shade visibility, including the full-player shared-element drawing. The legacy hide-immediately callback also clears media visibility. |
| Home clocks | Replace the ten old launcher clock faces with the same 86 packaged XMorphClock faces used by SystemUI. Home and each widget retain their own selected preset. The native renderer and all 47 font resources are linked into Launcher; old unsupported selections fall back to the first XMorph preset. Weather/messages retain their separate information area above the depth subject. |
| Lock clocks | Remove Tall depth, Living tall and Living horizontal from the picker. Preserve stable IDs for Geometric, English word clock, Living bold and XMorphClock; migrate the retired IDs to XMorphClock. Remove Match clock on Home. |
| Clock position and flow | Move the measured clock region through available screen space and reserve room for active media, accessibility text and the clock's scale. Apply reversible wake/unlock scale, translation and fade to stock/plugin clocks as well as Hecker clocks. XMorph AOD scale now interpolates through wake instead of switching at the first nonzero awake frame. Clock and wallpaper masks follow native scene progress; Home stays at its native final geometry underneath keyguard. |
| GameSpace icon | Specify the GameSpace icon on the target activity and alias, including round-icon metadata, so launcher fallback does not select the generic application artwork. |
| Unsupported icons | Keep native monochrome glyphs. Harmonize legacy artwork into the same pale accent family while retaining luminance/alpha detail, including icons missing from a selected pack. Avoid flattening opaque artwork into solid white discs. |
| Glass Studio | Read preset/style/tint selections from the same keys and types used by the renderer. Persist and display the selected values, refresh the preview, and retain explicit RGB changes. |
| Dock animation | Add Dock bounce animation and connect it to the actual hotseat spring/translation interpolator. Turning it off retains the dock's target placement and removes its spring overshoot. |
| Hidden apps and dialogs | Searchable app rows with icons and switches replace the package-name dialog. A visible override can restore a game hidden by automatic GameSpace filtering. Studio and HeckerLab dialogs share rounded surfaces, themed text and accents. |
| Motion Island position | Add horizontal −160…160 dp and vertical 0…160 dp controls, bounded to the visible display. The capsule and companion move together. Thickness remains independently adjustable. |
| Companion artwork | Tether every character to the capsule. Add continuous head/arm mesh articulation alongside the existing distinct monster, goblin and ninja body choreography, without double-painted limbs. |
| Media reactions | Add a default-on toggle for companion reactions to native play, pause and track changes, including automatic playlist advancement. Ignore the initial subscription snapshot and repeated unchanged playback callbacks. Preserve an in-flight Motion Sense skip and strict broadcast replay checks. |
| Dual status bar | Default to native-size icons, remove row-fit scaling of the clock, and anchor network speed next to the system icon block instead of its earlier left-hand position. Explicit icon-size controls remain available. |
| Auto brightness | In Hecker QS, the slider's sun control toggles the real per-user automatic-brightness mode and crossfades to A. Remove the separate bottom auto-brightness control from this layout. One UI retains its existing control. |
| Connection popups | Add optional glass blur for the shared wired/Bluetooth/earbud connection popup, including AirPods and buds. Use native window background blur and an opaque fallback when blur is unavailable or power saving is active. |
| ROM visibility | Enable the existing reviewed Voltage/Axion-derived Java/native ROM-visibility hooks for app processes by default, including newly installed and unselected apps across Android users. Remove the fifth per-app switch and its settings observer. Retain native OS components, both bundled launchers and Hecker services as exceptions. The other four per-app compatibility controls remain available. |
| HeckerVPN | Opening the page reads saved state without waiting for the kernel probe. Import/account dialogs remain usable during service work. Missing selection opens My profiles with guidance; busy requests give visible feedback. Consume file-picker results after the activity restarts. Report kernel capability only after a real controller response. |

## Validation

The checks cover source application/upgrades/reverts, Java/Kotlin compilation,
Android resource linking, native bridge contracts, clock catalog parsing and host
behavior. They do not establish ROM boot, visual smoothness on Coral, VPN traffic,
provider account acceptance or app-specific compatibility verdicts.

- Full host suite: 190 tests.
- Media geometry: 72,072 continuous forward/reverse layout cases with bounded,
  disjoint title/control areas and retained playback touch targets.
- Clock layout: screen/scale/media/position cases retain the measured media slot;
  existing depth and clock-information registration tests remain in place.
- Motion music coordinator: 35 lifecycle, gesture, manual-reaction and position checks.
- XMorph: 86 faces, 344 large/small/AOD state graphs and 47 packaged fonts; the
  shared library also compiles against the public Android SDK used by Launcher.
- Suite/Settings, Launcher, SystemUI/QS, headphone/Motion Island and VPN compilation.
- Prepared-source upgrade from `b7b7efa`, repeat application and complete revert
  checks against 138 immutable native input files.
- The actual prepared ROM-wide policy is executed for new/unselected apps,
  independent flags, multiple users and native component exceptions.

The local reduced JDK lacks `ct.sym`. Its unchanged gesture host fixture was run
with Java 17 source/bytecode settings; CI retains the strict `--release 17` check.

## Phone acceptance after building

1. In both QS styles, hide and close a player, reopen full QS, resume playback,
   and rapidly close/reopen the shade. Check long titles and large font sizes.
2. Try stock, word, bold and XMorph lock clocks through AOD/wake/unlock. Move the
   clock to both position limits with media playing and paused. Check the Home
   depth subject and a canceled unlock gesture.
3. Apply several Home/widget XMorph presets; change Glass Studio preset, style,
   tint and RGB values; leave and reopen the pages. Toggle dock bounce and hidden
   games. Check default and legacy icons in the drawer.
4. Test every character with Motion Sense and manual play/pause/skip, automatic
   next track, the media-reaction toggle, and both bar-position controls. Check
   the thread attachment, finite lifetime and separate head/arm motion.
5. Enable both dual-row layouts and inspect icon/network-speed placement. Toggle
   automatic brightness from the slider and reconnect wired/Bluetooth headphones
   with glass blur enabled and disabled.
6. Import a valid WireGuard profile through Android's picker, select it and
   connect. Separately test Mullvad account/country actions with a valid account.
   Verify real handshake, traffic, DNS, app routing, network changes and cleanup.
7. Verify app compatibility after restarting target apps, including a newly
   installed app. ROM visibility protection does not establish hardware
   attestation, conceal every possible detection signal, or replace device tests.

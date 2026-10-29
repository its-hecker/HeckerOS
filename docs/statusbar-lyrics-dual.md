# Status-bar lyrics and dual rows

Implementation: `feat/statusbar-lyrics-dual-20261029`, based on the published
Living Flow Suite revision `65f42ab27da9ce25fde3e1bb8862879cd78814f9`.
Both `hecker_flame` and `hecker_coral` inherit the SystemUI helpers and built-in
height overlays. This is source implementation; full Android builds and physical
phone acceptance remain pending.

## Controls

Open **Settings → Hecker Suite → Status bar**. Both switches default **off**.

| Control | Behavior |
| --- | --- |
| Dual status bar | Uses two rows and changes the native status-bar height. |
| Status bar lyrics | Shows the synchronized current line while unlocked; works with either one or two rows. |
| Second row: Notifications and battery | Clock/date and native connectivity indicators above; notifications and a live battery percentage/charging detail below. |
| Second row: Lyrics | Keeps native notification/system indicators in the top row and reserves the second row for lyrics. Requires the separate lyrics switch; otherwise uses the notification layout. |
| Height | Compact 44 dp, Balanced 52 dp, Tall 60 dp. |
| Row spacing / icon size | 0–8 dp gap; 12–20 dp icons, further fitted to available row height. |
| Lyric appearance | Physical left/centre/right alignment, preferred width 80–360 dp, font 9–18 sp, adaptive colour or a palette/custom `#RRGGBB`. |
| Lyric transition | Fade, scrolling marquee, or immediate replacement. Power saving and disabled system animations suppress motion. |

The native battery icon remains inside its existing native system-icon group.
The lower notification row adds battery information rather than moving or
duplicating the battery pipeline. Date text fits the spare clock-side space.
The existing lock-screen lyric switch remains independent. Neither new switch
changes it, the depth-wallpaper settings, or the AOD/lock clock style.

Width and font are preferences, bounded by the actual safe space. A single row
shares the clock-side region with notification icons and reserves notification
space. On narrow displays or with larger clock/text settings, a short line may
be ellipsized or hidden if it cannot fit. A dedicated lyric row offers more room.
There is no extra root module, service APK, external overlay window or hardware
requirement.

## Shared LRCLIB engine

`LyricsController` now exposes reference-counted lock-screen/status-bar
subscriptions over one current-user media-session observer, cancellable network
worker, recording matcher and bounded 24-entry memory cache. Artist/title lookup
and the existing LRCLIB disclosure remain unchanged. Negative results expire after
five minutes; stale responses from an old track/user are discarded.

Previously the controller lived only inside the lock-screen composition. The
status-bar view now holds its own subscription while attached, enabled, visible
and unlocked. Playback metadata and timing therefore continue updating while the
phone is unlocked, without relying on the lock-screen composition staying alive.
The two display switches are read independently for the foreground user.

`StatusBarLyricsPolicy` derives the current line and next update deadline from
the shared media snapshot and elapsed realtime, including playback speed and
seeks. No network request is made per line. Pausing/stopping playback, ending a
track, missing lyrics, instrumental results and timestamped blank lines clear
the status text and cancel its update callbacks. Screen-off, keyguard, hidden
status bar, opt-out and detachment also clear it. The lock screen retains its
existing three-line/AOD presentation.

## Native layout and insets

Three platform-signed, mutable RROs target framework dimensions:
`status_bar_height_default`, `status_bar_height`,
`status_bar_height_portrait` and `status_bar_height_landscape`.
The Settings controller enables exactly one of these built-ins, rolls back
preferences/selection if applying fails, and disables all three when opted out.
It does not edit unrelated overlays.

The native theme-service worker observes the height switches and user changes.
It applies the foreground user's choice to both that user and the system user,
so system_server/SystemUI and foreground apps receive the same framework height.
Stored preferences remain separate for every user; switching back restores that
user's selection, including the off state. Boot reconciliation is independent of
wallpaper preparation.

Native `SystemBarUtils`, status-bar window configuration and per-rotation inset
providers then use the framework height. App/status/tappable/gesture insets and
the native Quick QS minimum offset follow it, including fullscreen launch-window
handling. Keyguard keeps its native indicator layout and reads the taller native
inset; the custom phone rows and unlocked lyric text do not replace its UI.

`PhoneStatusBarView` calls the owned helper after its normal layout. Native views
retain their parents, LayoutParams types, media/notification bindings and touch
dispatch. The next measurement restores native measurement/clipping/scales before
fitting the selected row layout. Owned text is a non-clickable child, follows the
native indicator visibility/alpha and clock tint, and adds no touch interceptor.

## Validation and acceptance

Host checks compile the production shared LRCLIB engine against controlled media,
clock, user and HTTP facades. They exercise independent switches, one shared fetch,
unlocked visibility, pause/seek/gaps, positive/negative caching, rapid track skips,
late results, user changes and final-subscriber cleanup. Pure geometry checks cover
30,303 bounded placements and row-height/padding/spacing combinations. Real overlay
controller/synchronizer tests cover rollback, exclusive selection, foreground/system
height agreement, disabled users and switching back without rewriting preferences.

Locked SDK checks compile app/resources/AIDL, the SystemUI/Compose helpers and the
actual new native override bodies. AAPT2 compiles/links all three RROs. Native-source
checks use both the Coral and Flame framework pins, apply/reapply/revert the guarded
adapters, and run the actual pinned window-height/QQS-offset methods over 320
height/density/rotation/cutout/launch combinations.

These checks do not establish a full SystemUI/Soong build, frame pacing or phone
layout correctness. Before release, build and boot both devices and check:

- Each switch alone, both together, both row arrangements, off/reboot/user switch,
  and all heights in portrait/landscape and with display/font scaling and RTL.
- App content, gesture regions, shade/Quick QS/header, lock screen, AOD/wake,
  notification overflow, ongoing-call/chip, heads-up, privacy indicators and IMS icons.
- Play/pause/stop/seek/skip, blank lyric gaps, unavailable/offline LRCLIB, media-app
  changes, lock/unlock, screen-off, saver mode and reduced animations.
- Native notification touch/accessibility behavior, marquee/fade readability and
  rapid height changes without layout loops, clipping or additional frame drops.

Coral's matching manifest retains its experimental low-brightness device/framework
and root-privacy kernel pins. Flame's matching manifests retain its own existing
framework, hardware and kernel choices, including a separate non-KSU build variant.
This feature does not promote any of those experimental branches to a stable release.

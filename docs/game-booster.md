# In-game Game Booster

Game Space uses the supplied Samsung Game Booster images as the panel reference:
translucent navy glass, rounded action pills, a game-icon telemetry header,
Performance / Balanced / Battery saver with purple / blue / teal selection,
and an editable six-app dock. The collapsed overlay is a movable 246 dp-wide
FPS / CPU % / GPU % / RAM % / battery °C pill, clamped to the screen width.
Large text uses two rows with an increased height. The panel tracks the reference's approximate
43% landscape width, with safe-area limits and scrolling on short displays.

The expanded dashboard has a dedicated top drag grip. It moves the complete window;
the bottom grip still resizes it. Sliders and scrolling retain their own gestures.
The collapsed 48 × 48 dp dashboard shortcut moves horizontally and vertically;
tapping it opens the panel. Closing the panel retains its saved position.
Dashboard, handle and stats-pill positions are clamped to the cutout/navigation safe
area and saved separately for portrait and landscape. Resizing reclamps placement;
cancelled or multi-touch drags restore the starting position. Directional accessibility
actions move the grips without touch dragging. Existing saved HUD placement migrates.

Brightness and media volume use the same 44 dp, thumb-free white glass tracks
as `HeckerGlassTrack` in the One UI Quick Settings implementation. Accessibility
range actions and keyboard adjustments remain available. The dock launches actual
Android freeform tasks and retains the game session while a supported popup is
visible. Android decides compatibility resizing; a manifest-only resize check no longer rejects apps or aliases in advance. Shortcuts request a separate freeform task with bounds, and optional always-on-top support cannot block the launch when absent.

The panel includes game Focus/DND, screenshot, recording, navigation blocking,
edge blocking, auto-brightness blocking, assistant blocking, floating stats,
system monitor, shortcut editing and USB battery-charging pause. On HeckerOS,
Samsung's Bixby control maps to the Android assistant; the edge-panel control
blocks side-back gestures. Popup apps temporarily regain navigation and assistant
input. Memory cleanup, touch lock and Wi-Fi remain in System monitor. Performance
and battery-saver availability comes from Android's actual per-game mode APIs.
Unavailable modes remain dim and never pretend to apply a profile.

The reference's panel-access location selector and screenshot-resolution selector
are omitted.

## Measurements and session controls

FPS comes from the active game's `TaskFpsCallback`, CPU from `/proc/stat` deltas,
GPU from the KGSL `gpubusy` sample window, and RAM from Android memory information.
The pill's `BATT °C` reading comes from `ACTION_BATTERY_CHANGED` temperature in
tenths of a degree Celsius, matching the dashboard's battery-temperature reading.
It describes the battery sensor; CPU, GPU and skin temperatures are separate measurements.
Missing readings display an em dash. No display-refresh or fake telemetry fallback
is used. The platform service alone reads the hardware endpoints, authenticates
the platform-signed Suite UID, and requires a visible game on the current user's
default display. Binder death, screen-off, shutdown, user switching, game exit and
a five-second lease restore session controls. Saved auto-brightness mode is also
recovered after a system-server restart.

USB charging pause requires the matching Floral kernel, a wired source and battery
above 20%. It adds a separate SM8150 `game_charge_disable` inhibitor; it does not
write USB input-suspend, thermal limits or the health service's own inhibit request.
Health-service charging restrictions remain effective when this inhibitor clears.
The kernel automatically clears the game inhibitor after six seconds without a
heartbeat. This pauses battery charging while USB power remains available; battery
isolation and sufficient charger power are hardware-dependent.

## Verification

The gaming Binder name `hecker_game` has one dedicated label,
`u:object_r:hecker_game_service:s0`, in the Suite's system_ext service contexts.
The platform service adapter registers `hecker_memory` and `hecker_launcher`;
it must not add a second gaming label using `activity_service`.

Preparation upgrades both the previous three-service platform block and a block
where the obsolete gaming line was removed manually. The native integration check
combines platform and Suite context declarations, rejects duplicate gaming labels,
and exercises all three Java service registrations with the pinned AOSP fuzzer
checker. The standalone `tools/service_contexts.py` remains available for inspecting
and repairing a prepared checkout, including linked platform source and recovery
context caches.

- Suite Java compilation and Android resource linking with the locked SDK.
- Native gaming Java and the real generated AIDL interface.
- CPU/GPU/RAM parsing, counter reset and unavailable-reading regressions.
- Production kernel charge functions exercised with simulated register I/O and
  workqueues, including health inhibition, heartbeat renewal, timeout and I/O failure.
- Guarded native source patch apply / repeat / revert against immutable inputs.
- Host and Floral integration suites.

A full Android/Soong build, SELinux enforcement, physical charging measurements,
freeform apps and visual comparison on Coral/Flame remain device validation work.

## Game library in Hecker Launcher

Game Space now has its own launcher entry, opening the existing game library.
The default-on **Hide games in Hecker Launcher** switch in Game Space → Tools
hides library games from the drawer, launcher searches, home-screen icons and
folder previews. The library includes automatically detected games and manually
added apps, respecting exclusions. The Game Space entry stays visible.

Removing a game from the library or turning the switch off restores its launcher
visibility. Existing home/folder records and positions are retained. The filter
does not disable packages, so games still launch normally from Game Space.
Other launchers are unaffected, and each Android user's library is separate;
the personal-profile filter does not hide work-profile apps.

The read-only library provider accepts the Suite itself and the same-user,
preinstalled, matching-signature Hecker Launcher. Its signature permission is
requested only by the Hecker HOME variant. Launcher queries run off the UI
thread, observe library/package changes, retry observation after unlock, and
retain their last private cache during temporary provider failure. Existing
manual launcher hiding continues to apply independently.

The host regression runs the production provider and launcher client together,
covering caller identity, opt-out/removal restoration, cached results, view
recycling, observer recovery and profile isolation. The actual shortcut drag
listener is exercised for XY movement, tap, cancellation and accessibility.

## Build from an existing Coral checkout

The Suite and local manifest use `feat/settings-depth-lockscreen-20261028`.
The manifest pins the matching kernel and shared feature sources. Update this
same branch in the existing checkout:

```sh
cd ~/heckeros-coral
git -C .repo/local_manifests switch feat/settings-depth-lockscreen-20261028
git -C .repo/local_manifests pull --ff-only
repo sync -c -j2 --fail-fast --no-clone-bundle --no-tags
HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

Sync the complete manifest so the Suite, launcher preparation and hardware
sources match. Suite-only sync does not install a different charging kernel.

The panel uses a larger initial height and leaves 8 dp of vertical margin at its maximum. A
previous full-height selection follows the new limit. Both portrait and compact landscape panels
include live CPU/GPU segment meters; transitions interpolate real samples and respect reduced
motion. These are not simulated utilization animations.

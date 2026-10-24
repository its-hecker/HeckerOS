# In-game Game Booster

Game Space uses the supplied Samsung Game Booster images as the panel reference:
translucent navy glass, rounded action pills, a game-icon telemetry header,
Performance / Balanced / Battery saver with purple / blue / teal selection,
and an editable six-app dock. The collapsed overlay is a movable 170 × 40 dp
FPS / CPU % / GPU % / RAM % pill. The panel tracks the reference's approximate
43% landscape width, with safe-area limits and scrolling on short displays.

Brightness and media volume use the same 44 dp, thumb-free white glass tracks
as `HeckerGlassTrack` in the One UI Quick Settings implementation. Accessibility
range actions and keyboard adjustments remain available. The dock launches actual
Android freeform tasks and retains the game session while a supported popup is
visible. Apps that disallow freeform resizing report that limitation.

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

## Build from an existing Coral checkout

The Suite, kernel and local manifest use `feat/game-booster-reference`. These
branches include the earlier launcher/theme/boot fixes from the existing working
branch. For the first checkout of this new manifest branch:

```sh
cd ~/heckeros-coral
git -C .repo/local_manifests fetch origin feat/game-booster-reference:refs/remotes/origin/feat/game-booster-reference
git -C .repo/local_manifests switch --track origin/feat/game-booster-reference
repo sync -c -j2 --fail-fast --no-clone-bundle --no-tags vendor/hecker/floral/suite kernel/google/msm-4.14
HECKER_BUILD_JOBS=8 bash .repo/local_manifests/build.sh
```

If the manifest branch already exists locally, use `git switch
feat/game-booster-reference` and `git pull --ff-only` in `.repo/local_manifests`
instead of creating another tracking branch. Sync both the Suite and kernel;
Suite-only sync does not install the new charging control.

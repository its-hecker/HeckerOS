# Coral feedback and HeckerLab — 4 November 2026

This update builds on the native XMorphClock source cohort and is published on
`fix/coral-ui-compatibility-20261102`, with the matching Coral manifest pin.

## Clocks, Launcher and wallpaper

The selected English word clock remains the same renderer throughout AOD and
wake. Its animation stops while dozing. Previously, AOD explicitly replaced
styles 0/1 with the simple clock, producing a different appearance on wake.

Custom clocks now fade with actual native dismissal progress. The previous tall
clock handoff guessed a Home clock position and size even when Home used a
different clock widget. The new transition uses a continuous fade; it does not
claim a glyph-for-glyph morph between arbitrary, different widget designs.
Both the built-in and XMorph hosts follow the same dismissal envelope.

Home depth retains the verified, matching bitmap pair while that same selection
reloads. A failed temporary provider read does not discard a usable current pair.
Wrong wallpaper IDs, disabled depth and live wallpaper still invalidate it.
Missing layers can retry on subsequent Home draws. Both layers keep one settled
crop, without restarting a separate 750 ms zoom when returning from an app.

The foreground redraw over a clock uses the full inverse clock-to-global and
root-to-global matrices. It remains registered during Launcher scale/rotation
animations; the old path skipped drawing whenever an ancestor was scaled.
It clips to the clock and does not paint over icons or touch targets.

Empty-workspace double-tap sleep commits on the second down event. Wallpaper &
styles first resolves the configured picker, then the installed implicit picker,
then the native Settings wallpaper action, preserving the existing admin gate.

Automatic lock extraction depends on its own opt-in, even if an old depth mask was
disabled. SystemUI's runtime wallpaper/unlock receiver asks the signature-checked
depth provider to schedule the current user's debounced job. This also works
while the feature app has no active UI. Publication keeps the existing wallpaper
identity, cancellation, selected-clock and home-layer checks. Reconciliation
shares the publication lock so a wallpaper broadcast cannot race a manual import.

## Icons and GameSpace

GameSpace has separate colored and monochrome controller artwork, with actual
cutouts and an adaptive-icon safe zone. Unsupported glass icons retain their
original artwork/colors inside the plate. They are not flattened into a white
silhouette; real monochrome artwork continues to use the existing tint.

GameSpace removes its foreground notification and stops its dashboard service
when the game disappears. It restores the existing session controls and clears
the old task. A native, event-driven task listener starts a new dashboard session
for a focused, visible game. It respects the current user, opt-in, automatic game
tags, manual additions/exclusions, screen/keyguard and display checks. It does
not need an idle foreground service or polling loop between games.

## Media and blur

The QS card has **Hide** while idle/paused and **Close app** for an active session.
Hide is shared by the QQS/full-QS copies and resets on fresh playback. Close app
force-stops only the foreground user's currently selected ordinary media package
and hides its card on success. Core UIDs and SystemUI are excluded. The required
platform permission is declared by SystemUI; no root or shell command is used.

The player follows real panel expansion and a draw-time scene gate, so a closing
shared-element transition cannot leave its artwork visible after the shade exits.

Blur defaults to **85%** when no explicit user setting exists. Strength is applied
once at the common native window-request boundary for both QQS and full QS.
Changing the slider replays the unscaled native request. Bouncer, communal,
brightness mirror, unlock, app launch, thermal/power and display gates remain
native. Existing explicit slider values are preserved.

## Motion Island, lyrics and naming

The motion bar height is adjustable from **26–56 dp**, default **32 dp**. The
companion window, attachment point and artwork scale with the bar. Astronaut
remains available, alongside the supplied blue monster, goblin and ninja designs.
Transparent cutouts are packaged locally and decoded on a worker at bounded
resolution. There is no runtime download.

Each new character has different finite choreography: monster hops and squashes,
goblin sways/bows, and ninja dashes/tilts. The existing astronaut welcome uses
opaque articulated poses rather than crossfading two visible arms. Music resumes
can produce a welcome without another Oslo command. Buffering during a skip
preserves its directional reaction, and a bounded skip grace retains the capsule
while a player temporarily removes/replaces its session. Reduced motion, power
saver, screen/AOD and user cleanup remain in force.

Lyrics retain callbacks for metadata-less paused candidates, so an existing
session can start without a membership event or toggle cycle. Status-bar unlock
state also refreshes after native keyguard changes. The shared LRCLIB provider,
independent surface switches and user-scoped cache remain.

Visible hub names are **HeckerLab**. Package/component IDs and existing capture
folder paths are retained for installed settings/data compatibility.

## Validation and remaining device work

- 187 Python host tests, including real cold-start lyrics callbacks, scaled depth
  rendering, provider access, skip/resume coordination and GameSpace exit/monitor.
- 29,799 bounded character/action/direction frames and transparent image checks.
- Complete Suite Java/resources/AIDL, independent Settings dashboard, SystemUI
  Kotlin/Compose and Motion Island Java/resources compile with locked inputs.
- Owned Launcher Java/resources compile with the locked SDK.
- 135 pinned upstream files: fresh/repeat/upgrade/revert preparation checks,
  native blur and panel callbacks, permissions and gesture routes.
- Shared integration checks and the matching Coral manifest checks.

The local host has a slim JDK without `ct.sym`; host service probes retain Java 17
source/bytecode checks against their reviewed service facades. The repository's
unmodified `--release 17` checks remain in GitHub CI. Transient HTTP 429/5xx native
source downloads now have bounded retries; immutable pins remain unchanged.

Full Android/Soong compilation and physical Coral acceptance remain pending.
After rebuilding, check repeated app/Home/unlock cycles with independent Home
and lock wallpapers, word-clock AOD, quick shade reversals, idle Hide and playback
restore, media-app shutdown, auto extraction from Wallpaper & styles, every
character/direction/height, lyrics from a stopped/paused player, and game exit plus
the next automatic launch. Retain `out/` and the existing RBE/default-jobs command.

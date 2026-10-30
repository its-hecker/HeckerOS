# Display double-tap to sleep

Settings → Display → Double tap to sleep controls the awake status bar and
lockscreen through `Settings.System.double_tap_sleep_gesture`, default off.
It is independent of the launcher's empty-workspace double-tap preference.
The existing switch and label are retained.

## Cause

The inspected PixelOS framework revision
`044b965aa2d30d056b930e5ff013ba5303cde9fa` and Coral's pinned framework
`c72dce10290956bd3c4bfd23e787800d1340c46e` have identical
`QQSGestureListener` and `KeyguardTouchHandlingInteractor` implementations.
The older shade listener reads the Display switch. The modern keyguard
interactor instead reads `Settings.Secure.DOUBLE_TAP_TO_SLEEP` and is gated by
an AOSP flag and `config_supportDoubleTapSleep`. Enabling the Display switch
does not enable that separate path.

The collapsed status bar has another gap: in scene mode its controller caches
and discards stationary taps, forwarding drag gestures to the shade. A detector
attached only to the shade window does not receive those complete tap pairs.

## Change

The native keyguard interactor now observes the Display setting through the
existing `SystemSettingsRepository`. Its foreground-user-aware implementation
handles preference changes and user switching. The awake software gesture does
not require the unrelated AOSP preference, experiment flag or hardware capability
resource. The native touch-view callback follows its bound enabled state without
the additional experiment gate.

Keyguard retains its fully transitioned lockscreen, QS visibility and secure-lock
conditions, its native touch handling and its existing wake fallback. AOD wake
gestures remain on the existing pulsing path. Face authentication and single-tap
handling are not replaced.

The actual `PhoneStatusBarView` observes completed, stationary pairs before
native child dispatch. It reads the current user's Display setting at DOWN and
UP, ignores touch exploration, non-finger input, multiple pointers and noninteractive
or locked screens, and resets on cancellation, user change, disabled interactivity
and detach. The keyguard path handles locked screens. It consumes only a valid
second UP and sends native children a CANCEL so click/drag tracking finishes.
Timing and movement bounds come from `ViewConfiguration`.

The legacy QQS listener reads the same current-user setting at completion and
retains its dozing, header/keyguard, bouncer and falsing checks. The panel's
redundant second detector and cached settings observer are retired, avoiding
duplicate sleep calls and a stale user preference in that path. Native keyguard
test construction and flag/config expectations are updated with the adapter.

All changes are applied by guarded, reversible Suite adapters. The Coral manifest
advances the Suite pin; Flame manifests and hardware/kernel/vendor pins stay at
their existing revisions.

## Validation

The host checks execute the production status-bar helper and the prepared native
dispatch method with Android service doubles. They cover default off, enable,
disable, current-user isolation and switching between taps, CANCEL delivery,
interactivity, AOD/keyguard/accessibility exclusion, pointer/tool filtering,
drags, long press, timing/slop bounds and repeated UP events.

The Kotlin check compiles and executes the actual prepared QQS listener, keyguard
setting flow/callbacks and touch-view callback with the native repository
interface and locked Kotlin/coroutines dependencies. It checks live preference
and user-flow changes, transition/QS/secure-lock gating, wake fallback, bouncer
and falsing behavior. It also checks the existing user-aware repository binding.

`check_native_ports.py` includes these checks. Fresh source, upgrade from the
previous Living Flow Suite, repeat application and exact reversion are checked
against immutable Coral inputs. The existing 135 Python tests and locked
SDK/native source checks pass. These checks are not a full SystemUI/Soong build
or an instrumented Android gesture test.

On Coral, verify both surfaces with the Display switch off, on and off again;
status-bar drags, notifications, QS expansion, large/dual status rows, normal and
depth clocks, bouncer/FaceID, AOD double-tap-to-wake, TalkBack and a second user.
Confirm that enabling the switch does not require restarting SystemUI.

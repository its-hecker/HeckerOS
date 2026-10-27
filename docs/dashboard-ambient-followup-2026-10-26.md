# Dashboard and ambient follow-up — 26 October 2026

This update implements the remaining expanded-dashboard movement and repairs concrete
charging/glow lifecycle gaps found in the previous source checkpoint. It is source
work; a full Android build and a new Coral test are still required.

## GameSpace

The top grip drags the expanded dashboard; the bottom grip resizes it. The edge
handle moves up/down and still opens with an inward swipe. The stats pill remains
movable. All three surfaces save independent normalized positions per orientation.
System-bar/cutout bounds apply to restore, drag and resize. Cancellation restores
the starting position; a second pointer cancels the drag. Accessibility exposes
directional movement. Sliders, scroll content, game modes and bypass retain their
own paths.

## Charger-triggered ambient display

The earlier implementation discarded the request if DozeMachine was transitioning.
The native bridge now retries transient/busy states within ten seconds, uses an
existing full pulse when possible, and lets native proximity/power policy decide.
A timeout-bounded partial wake lock keeps the CPU available only while queuing;
native doze owns the accepted pulse. Unplug, interactive wake, user switch,
suppression and timeout cancel outstanding work. Tokens isolate consecutive plugs.
The intro still starts only when native display refresh becomes available.

## Motion Sense glow

The original plugin registers a status listener without receiving an initial
dozing/pulsing snapshot. The host now initializes newly attached Oslo feedback
views from the native controller, retains the native shade hold while locked,
and refreshes the existing GL surface after native display transitions/minute ticks.
There is no detach/reparent from a gesture-start callback and no independent render
loop. The window stays non-touchable and callbacks are removed on disconnect.

## NetLab dependency

LAN discovery and native phone-hotspot pause/restore remain available as documented
in `network-lab.md`. Router-peer disconnect/reconnect and bandwidth enforcement need
the router/AP model, firmware and supported management interface. Those details
have not been supplied, so this update does not assume OpenWrt or ship a pretend
speed-control backend. The speed Apply action remains unavailable until a real
backend is implemented and checked for the selected network.

## Device acceptance

1. Drag, resize, close/reopen and rotate the expanded dashboard. Check the handle,
   stats pill, cutouts, cancellation, accessibility and unaffected game controls.
2. Plug in from a fully black screen with Always-on off, then with it on. Confirm
   automatic ambient intro and its transition to information without a manual tap.
3. Cover proximity, unplug/replug quickly, wake during the intro and switch users.
   No delayed replay or retained `HeckerAmbientCharging:pulse` wake lock should remain.
4. Check real Soli presence/gestures and glow on HOME, lockscreen, Always-on and a
   brief ambient pulse; repeat after SystemUI/plugin restart while already on AOD.
5. Inspect SystemUI/doze/power logs for a failure before changing sensor or plugin
   policy. Host tests cannot establish physical on-screen rendering or radio HAL support.

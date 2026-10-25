# Ambient charging and the custom lockscreen clock

Hecker Suite → Lock screen contains the geometric digital clock switch, ambient charging switch,
three finite charging animations (energy orbit, multicolour plasma and gauge fill), and three information
choices: Off, Detailed, and Minimal.

The detailed AOD follows the supplied reference: a thin geometric clock and date, an open magenta
charging meter, battery percentage, temperature/voltage/estimate cells, and battery-side watts.
The minimal AOD shows only a dim percentage and bolt/charging label. The native root layout
continues to measure lyrics separately and subtract their height from the panel's available area.
Small viewports scale the detailed composition together so its clock and readings do not overlap.
The stock AOD charging/percentage line is hidden while the custom scene owns charging information;
biometric, transient, alignment and battery-defender warnings retain their native precedence.

The clock switch replaces both small and large clock regions, including the selected packaged
clock plugin. Turning it off restores the existing Wallpaper & style selection. It does not
replace authentication, shortcuts, media, notifications, or other keyguard security controls.

## Plug sequence

1. For an off-screen plug event, PowerManager preserves noninteractive wakefulness on supported
   AOD hardware instead of waking the full lockscreen.
2. SystemUI requests a normal, proximity-checked doze pulse. A pending animation starts when
   the native display is actually refreshable, rather than expiring while the panel is off.
3. The selected animation plays once for 2.2 seconds. It then becomes static Detailed or Minimal
   information. Off returns to the regular AOD after the intro.
4. Unplug, a power-button wake, user switch, or disabling the feature cancels the intro. Sticky
   battery broadcasts, booting while plugged in, and ordinary telemetry updates do not replay it.
   A pocket-suppressed intro expires rather than appearing hours later.

Always-on display keeps its own native switch. Charging styles have separate AOD and brief-ambient
switches. While Always-on is enabled, the AOD charging switch takes precedence; with Always-on off,
the ambient switch controls tap, lift, reach and plug pulses. Neither charging-style switch forces
persistent AOD. The hardware's ordinary default is used when no explicit AOD preference is stored.
Inversion, proximity, power saving and pulse duration remain under native doze policy.

Gauge fill animates the detailed magenta meter into its actual battery level without a black
transition. Energy and plasma crossfade into the static information. These finite transitions do
not schedule repeating pulses. A small AOD positional shift belongs to native burn-in protection;
a repeating full-screen brightness flash is not an intentional effect of this renderer.

## Live data and power

Temperature, voltage, status and battery level/scale come from the system battery broadcast.
Current and time-to-full are queried off the UI thread through BatteryManager. Power is the
absolute battery current in microamps times battery voltage in millivolts, converted to watts;
it is not advertised adapter output. Missing data displays a dash. Integer-only battery hardware
displays `.00` in the detailed field; the UI does not invent hundredth-of-a-percent progress.
During plug negotiation the label says Charger connected. Positive battery current or the charging status confirms Charging; full and confirmed paused states retain their own labels. The label reports charging/full/paused state and does not claim proprietary FlashCharge.

Only the short intro requests animation frames. The persistent gauge has no rotating ring or
particle loop. Minimal mode has no clock timer and uses low-luminance text on black. Native AOD
brightness and burn-in translation apply; detailed clock updates occur on minute boundaries,
and battery readings update on real battery broadcasts. Reduced-motion users receive the
information directly.

## Validation

`tests/AmbientChargingPolicyTest.java` exercises connection edges, sticky replays, wake/unplug,
pending pulse expiry, reduced motion, percentage precision and current/voltage conversion.
`tools/check_ambient_charging.py` compiles and executes the actual prepared Android power and
ambient policy methods for user switching, supported/unsupported hardware, inversion, plug,
unplug, ordinary Always-on, and animation-only behavior. It runs in the native adapter check.
The complete Suite/resources and Kotlin/Compose adapters are compiled with locked dependencies.

A full Android 17 Soong build and Pixel 4 XL tests are still required. Verify a charger plug from
both screen-off and existing AOD, each animation/information combination, unplug during the
intro, plug during another doze pulse, a covered proximity sensor, Always-on off, full/paused
charging, lyric placement, clock restoration, user switching and power-button/face unlock.

# One UI battery charging ripple

The selected One UI battery style (`com.android.systemui.battery.oneui7`) now
animates its green fill with a gentle liquid edge while the battery is charging.
The edge stays centered on the actual percentage; it does not repeatedly pretend
that the battery has reached 100%. The existing overlay silhouette, dimensions,
fill direction, color option, size control and percentage remain in use.
Percentage contrast follows the same animated fill path.

The native drawable reuses its paths and bounds and requests at most one frame
every 33 ms, with a 2.4-second wave at the normal system animation speed. An
ImageView hosted in the existing Compose battery slot supplies drawable callbacks
and window/display lifecycle signals. It stops on unplug, 100%, power saver,
hidden/detached views, display off/doze and disabled system animations. The
duration-scale listener is registered only while attached and also handles
re-enabling animations. Other battery themes retain their static fill.

Host regression compiles the production drawable and exercises both fill
directions at empty, low, middle and full levels, phase progression, bounded
frame scheduling, percentage contrast, original-mask preservation, unplug,
visibility, detachment, power saver and duration scaling. The existing battery
geometry regression still checks overlay resource identity and dimensions. CI's
Quick Settings compiler covers the Compose/ImageView bridge against locked
Android and Compose dependencies. These checks do not replace a full SystemUI
Soong build or a visual test on Coral.

On the next build, select the One UI battery under Hecker Suite → Status bar,
then check wired/wireless charging, unplug/replug, 100%, power saver, another
battery theme, light/dark status bars, battery size and percentage options,
fullscreen apps, lockscreen, screen off/AOD and disabling/re-enabling system
animations. Confirm the ripple is smooth and that no screen-off frame loop
remains.

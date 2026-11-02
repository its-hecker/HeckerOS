# Coral device feedback — 2 November 2026

This branch continues the November 1 app-compatibility source. The supplied
Russian screenshots and QS recording identify the targets below. These are
source fixes with host/SDK validation; they have not been measured on Coral.

| Report | Change |
| --- | --- |
| AOD clocks are too small and have no size control | Add an independent AOD clock size slider, 70–200%, default 140%. The renderer scales the grouped geometry and retains the native clock area and burn-in movement. Awake clock size is independent. |
| QS media appears late and lingers over Home after a quick close | Feed real legacy QS visibility/expansion into the shared media scene, reserve the compact media target, and fade the media itself early on exit. Keep controls and the shared media parent separate. |
| Blur feels excessive and the ROM feels slower | Add a live blur strength slider, default 55%, with 0 disabling shade blur. Reuse media measurement buffers, stop position/glow work while hidden, bound decorative glow cadence and avoid duplicate session queries/date-formatter work. Native power and thermal gates remain. |
| Settings cards have doubled edges and lines | Give the grouped decoration sole ownership of the card surface, use bounded inset ripples for rows, and disable the default homepage dividers. Reapply styling after native selection callbacks. |
| GameSpace icon is a blank white circle | Give the launcher alias a dedicated colored adaptive icon, legacy fallback and monochrome mask. |
| Product Sans appearance for Russian | Bundle OFL Google Sans Cyrillic-only fallback fonts. Preserve existing Latin, digits and clock designs; add Russian glyph coverage including Ё/ё to the named Pixel text families. This does not redistribute proprietary Product Sans. Font source and reproducible output hashes are in `fonts/google-sans-cyrillic/ORIGIN.json`. |
| Stock launcher weather is missing | Still needs device diagnostics. The screenshot shows Pixel Launcher/Google Smartspace; the retained Hecker Launcher weather bridge is a separate path. No source evidence yet establishes which Google provider, permission or feed is failing. |
| More custom-ROM detection points | Add the independent **Hide custom ROM** profile option with the reviewed VoltageOS native adaptations. See [scope and limits](app-compatibility.md). |
| `/system/addon.d` is a detection point | Exclude add-on preservation scripts from Coral product copy destinations and known script modules after product inheritance. Reject any remaining `addon.d` directory, file or symlink in the built partition staging trees. |

## Add-on behavior and incremental builds

The build removes `addon.d` from image inputs; it does not attempt to delete files
from a running phone or remount a partition. This does not depend on the phone's
filesystem being ext4, F2FS or EROFS. Existing bundled Google apps are retained.

The tradeoff is loss of `addon.d` preservation across ROM updates. Removing it
does not inherently prevent initial flashing of GApps or a recording package;
whether a particular package can install still depends on that package and the
ROM's installation method. Packages that rely on `addon.d` may need reinstalling
after an update.

The build helper detects old staged `addon.d` paths, including broken symlinks,
and runs Android's `installclean` before building. A final output check stops the
helper if a new install rule recreates them. Do not bypass that check when adding
new packages. With `HECKER_BUILD_JOBS` unset, the helper runs `m heckeros`; Android
chooses parallelism. A positive explicit override remains supported, and the
existing RBE environment is passed through.

## Pixel Launcher weather diagnostics

On the affected phone, first verify that At a Glance weather is enabled in Pixel
Launcher, Google weather has a location, and the Google app is enabled. Capture
these read-only commands while the weather is missing:

```powershell
adb shell am get-current-user
adb shell cmd role get-role-holders android.app.role.HOME
adb shell pm list packages -d
adb shell dumpsys smartspace > smartspace.txt
adb shell dumpsys package com.google.android.googlequicksearchbox > google-package.txt
adb shell dumpsys package com.google.android.apps.nexuslauncher > launcher-package.txt
adb shell dumpsys package com.google.android.as > intelligence-package.txt
adb shell cmd appops get com.google.android.googlequicksearchbox COARSE_LOCATION
adb shell cmd appops get com.google.android.googlequicksearchbox FINE_LOCATION
adb logcat -d -v threadtime -s SmartspaceService SmartspaceSession SmartspaceView GoogleWeatherProvider BcSmartspaceView > weather-logcat.txt
```

Review captures for account/location details before sharing. These commands do
not clear app data, alter permissions, restart services or change the phone's OTA
state. If the Google provider is healthy but weather is absent, the logs are needed
before deciding whether a ROM adapter is appropriate.

## Validation and device acceptance

The 164 Python host tests pass, including 111,330 clock frames, 75,789 media
placements matching the reviewed geometry and 72,072 continuous media frames.
The native checks cover opt-in/reset and core UIDs, invalid pointers, errno,
fortify entry points, read-only and close-on-exec handling, short readlink buffers,
long property names/values, service domains and generated syscall ownership for
all five ABIs. Fresh apply, upgrade from the published November 1 adapters, repeat and
byte-exact reversion pass. The package,
installer and Settings production-policy check passes 2,590 assertions.

Suite Java/resources/AIDL, the independent Settings dashboard, QS Kotlin/Java
and the Settings homepage adapter compile against the locked inputs. The native
SystemUI adapter checks also cover the real legacy QS callbacks and blur gates.
These are bounded host checks; full Android/Soong/APEX/SELinux linking and phone
acceptance are still required.

On Coral, check all clock styles and AOD sizes, Russian text and large fonts;
fast QS open/close/reopen in both panel styles with playing/paused media; blur at
0/55/100 under normal and Battery Saver conditions; Settings selection/ripples;
GameSpace icon on a fresh install and after an update; all compatibility toggles
independently and in combinations, profile disable, app-zygote restart and a
secondary user. Capture actual frame timing before claiming a performance gain.
Retest UBL without assuming that any one software flag explains its crash.

# 25 October device review

> This is the earlier review record. The [later phone follow-up](device-followup-2026-10-25.md) removes Memory Boost and adds the QS/media, GameSpace and native launcher changes.

This change is based on Suite commit `586695c646b5d2eafec7b5f4321eb31cc85dfc67` and the Pixel 4 XL review/log recording `25_10-04-36-35_811`. The reported build booted. The changes below still require a new ROM build and phone regression.

| Review item | Source change |
| --- | --- |
| App Lock looks like a smiley | Shield/iris scanner with moving beam and rotating segments, success/error states, and matching Suite Lottie. Hide only the native decorative sensor image; native authentication/accessibility stays in control. |
| Full Hecker/One UI QS crashes | Repair the prefix-first historical adapter upgrade that left a second header fade behind. Upgrade the known broken prepared sources and select the longest historical adapter first. |
| Blank QS previews | Correct Lottie shape-group paint order. The opaque card was covering its own controls. Fix the generator as well as the bundled previews. |
| Remove TouchWiz/One UI volume | Remove the toggle, restore the native volume-dialog factory and remove reviewed generated volume adapters. |
| Lyrics page animation | Add an original phone/music/lyrics-highlight Lottie at the top. |
| Battery size needs restart | Add **Status bar → Restart System UI**, with an explicit confirmation. The receiver requires WRITE_SECURE_SETTINGS. |
| Remove status-bar popup | Restore native touch dispatch and remove the generated popup adapter. Keep Memory Boost callable independently from QS. |
| In-game scrollbar | Hide both scrollbars while retaining touch scrolling. |
| Game profiles unavailable | In native GameManagerService, obtain manifest FPS/downscale defaults when an OEM tuning configuration is absent. Previously that case returned a builder whose flags defaulted to false. Explicit game opt-outs remain respected. |
| GameSpace artwork | Replace the generic bubble/controller artwork with an animated dashboard, FPS pill, chart, controls, sliders and gamepad. |
| Memory Boost | Refresh the per-user snapshot before queries/cleanup, isolate unavailable profiles, default corrupt rules safely, and ensure a failed role listener does not prevent initialization. Report service failure separately from protected roles/shared UIDs. Add a QS tile that checks service availability and uses the same AMS cleanup policy. |
| Pixel Launcher enhancements | Remove the page/router/activity, framework hooks, service startup, generated adapters and service registry entries. The recording confirms incompatible reflected StateManager methods. |
| Own Launcher3-based launcher | Add a Hecker Launcher HOME module from the pinned Android 17 AOSP Launcher3, with caracal branding, native widgets/folders/drawer/settings and an empty-workspace double-tap-to-sleep preference. Keep Pixel installed and retain Pixel as the only Recents/Quickstep provider. Choose HOME through Android settings; there is no Recents overlay switch or reboot transaction. |
| Motion Sense glow crashes | Give Oslo one stable non-touchable trusted window. Wait for attachment before plugin setup. Do not detach/reparent from its animation callback, and cancel pending setup on disconnect. |
| Bypass charging access | Add the device's ueventd attribute ownership rules and a narrowly scoped init property action that repairs the fixed node's DAC ownership after late/rebound probing. Log the canonical node/access failure once per retry interval. Keep the leased kernel inhibitor, low-battery threshold, cable checks and normal charging/thermal control. |

## Recorded causes and limits

The recording has one fatal Oslo `GlowFeedbackView.registerLumaSampling()` null-ViewRootImpl crash and two fatal QS `HeckerExpandedQuickSettingsHeader ... multiple alpha transformations` crashes. It also records Pixel enhancement reflection failures. It does not include a Memory Boost or charging-access reproduction with the necessary service/SELinux details, so those changes must be confirmed on the phone; the screenshot establishes charging state 5 (node exists, access denied), not its exact permission cause.

AOSP Hecker Launcher deliberately uses the standard third-party HOME path with Pixel providing Recents. Native Launcher3 customization is the starting feature set; proprietary Pixel glass modifications and the retired vendor launcher's controls are not carried over.

## Validation

- 81 host policy/adapter tests and the pure Java regressions; 77 Coral integration tests.
- Suite Java, independent Settings UI module, resource linking and generated Binder interfaces.
- QS Kotlin/Java/Compose/resources; App Lock drawing code.
- Native memory/control adapters, gaming service, and Oslo/face/headphone Java/resources.
- 77 real pinned platform files: 171 adapter/copy changes apply idempotently and revert exactly, including the new AOSP Launcher3 integration, GameModeInfo defaults and native status-bar dispatch.
- Lottie previews rendered with rlottie, including both QS layouts, gaming and lyrics.
- Coral integration tests and manifest/pin validation.

These checks do not substitute for services/SystemUI/Launcher Soong compilation, SELinux policy compilation or hardware testing.

## Phone acceptance for the next build

1. Open/collapse full QS repeatedly in both layouts, including reverse/partial gestures. Confirm no SystemUI restart and an immediate header fade.
2. Trigger Motion Sense glow on the lock screen, HOME and an app; unlock and repeat. Confirm no return to the lock screen/crash.
3. Change battery icon size, use Restart System UI, then verify the size and normal unlock.
4. Verify the removed controls are absent and the stock volume panel and normal status-bar touch behavior work.
5. Check all four new/repaired animations, plus reduced-motion behavior.
6. In GameSpace switch Performance/Battery saver/Standard for a real game. Check readback and FPS; respect games that opt out of interventions.
7. Test Memory Boost on an ordinary app; verify protected roles/shared UIDs stay protected. Exercise the four controls and QS tile, including no eligible background processes.
8. With wired power and battery above 20%, retry charging pause after any access repair. Capture HeckerGame and SELinux messages if it stays unavailable; measure charging/current behavior and restoration on exit, lock, disconnection and service death.
9. Choose Hecker Launcher through Default home, exercise widgets/folders/app drawer/Recents/gestures and the native double-tap setting, reboot, then switch back to Pixel.

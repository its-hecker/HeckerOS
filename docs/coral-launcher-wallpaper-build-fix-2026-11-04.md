# Coral Launcher wallpaper Kotlin build fix — 4 November 2026

The supplied `error_log_its_hecker_coral_20261104-044557.txt` stops while compiling
`HeckerLauncher`, at `WorkspaceLongPressOptions.kt:209`. The newly added fallback
used `android.provider.Settings.ACTION_WALLPAPER_SETTINGS`, which is not declared
in the Android SDK. The earlier checks compiled owned Launcher Java, resources
and other native hooks, but did not compile this patched Kotlin method.

The fallback now uses `"android.settings.WALLPAPER_SETTINGS"`, the action declared
by the exported `WallpaperSuggestionActivity` in the pinned Settings manifest
(`3831282d93ecda4d52a6894077482fe01ecea903`). It retains the configured-picker and
installed-picker checks, Settings package restriction, admin policy gate, launch
flags and wallpaper extras.

The adapter recognizes the exact previously published fallback and replaces it
during preparation. No hand edits or deletion of build outputs are required.
Unrelated edits remain protected by the existing guarded source adapter.

Validation includes:

- Reproduce the supplied unresolved-reference error by compiling the previously
  published native method against the locked Android SDK.
- Compile the corrected, actual prepared `startWallpaperPicker` method. Only
  Launcher-owned contracts use facades; Android API classes come from the SDK.
- Verify that the fallback has an exported default handler in pinned Settings.
- Check upgrade, repeat application and revert, preserving unrelated edits.
- Pass all 188 Python host tests and native preparation/upgrade/revert checks
  against 136 pinned upstream files.

The Kotlin and Settings-handler checks now run in the existing native-source CI
stage. These checks do not establish that the complete ROM finishes compiling or
that the wallpaper picker works on the physical phone.

## Incremental rebuild

Keep `out/` and update the matching Coral branch from the Android source root:

```bash
git -C .repo/local_manifests fetch origin fix/coral-ui-compatibility-20261102 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

This preserves RBE and Android's default parallelism. The remaining build should
be continued before another flash; physical wallpaper-picker acceptance remains
pending.

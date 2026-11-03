# Coral Settings homepage crash — 4 November 2026

The supplied build boots, but every recorded Settings launch fails in the same
homepage callback. The crash buffer contains twelve occurrences of:

```text
java.lang.NullPointerException: RecyclerView.invalidateItemDecorations() on a null object reference
    at androidx.preference.PreferenceFragmentCompat$DividerDecoration.setDivider
    at androidx.preference.PreferenceFragmentCompat.setDivider
    at com.android.settings.homepage.TopLevelSettings.onCreateRecyclerView(TopLevelSettings.java:249)
```

The homepage styling adapter introduced `setDivider(null)` inside
`onCreateRecyclerView()`. AndroidX assigns its internal `mList` only after that
callback returns. Its divider setter immediately invalidates decorations on
`mList`, so a newly allocated local RecyclerView does not make that call safe.
Inspection of the checksum-locked Preference 1.2.1 bytecode confirms this order.

Both the Settings patch catalogue and the active framework preparation plan now
place divider suppression in `onViewCreated(View, Bundle)`, after the superclass
callback. Background colour and the Hecker item decoration remain in the
RecyclerView creation callback. No delayed task or nullable early-call workaround
is used. The preparation plan recognizes the exact published crashing block as
well as the two earlier styling variants, and upgrades them in place.

Validation completed:

- The host Java lifecycle probe reproduces the published null-list exception.
- The extracted corrected native callbacks pass first open, restored view and
  repeated recreation, retaining exactly one decoration and divider update per view.
- The offline callback fixture matches the real pinned native Settings source at
  `3831282d93ecda4d52a6894077482fe01ecea903`.
- Ten customization-plan tests cover fresh application, all published homepage
  upgrades, repeat preparation, reversion and preservation of unrelated edits.
- Fourteen preparation tests and the Suite source checks pass.
- Settings resources and the native preference/helper/highlight adapters compile
  with the existing checksum-locked SDK and AndroidX inputs.

The lifecycle probe models AndroidX list ownership on the host. It does not run
an Android Fragment or install a Settings APK. Full ROM compilation and testing
the rebuilt image on Coral remain pending.

## Incremental rebuild

From the existing ServerHive checkout, keep `out/` and run:

```bash
cd ~/heckeros-coral &&
git -C .repo/local_manifests fetch origin fix/coral-ui-compatibility-20261102 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

The matching local manifest must select this corrected Suite revision; updating
only the top-level manifest checkout does not move a pinned Suite project.
The build retains RBE and Android's default parallelism.

After flashing the rebuilt image, check Settings from the launcher and QS gear,
enter a subpage and return, reopen Settings, and exercise a restored/recreated
homepage. Confirm that grouped cards still have their intended separators and
the crash buffer has no new homepage null-list exceptions.

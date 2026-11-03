# Coral font configuration boot failure — 3 November 2026

The supplied `hecker-testing.txt` shows Zygote failing during
`android.graphics.Typeface$NoImagePreloadHolder` initialization:

```
java.lang.IllegalArgumentException: lang cannot be used in FontFamily inside  family or family-list with name attribute.
    at android.graphics.FontListParser.readNamedFamilyList(...)
    at android.graphics.fonts.FontCustomizationParser.readFamilyList(...)
```

The Cyrillic adapter at Suite `335d398d9fe3a0e2f487424be667b1d8cad10f7f`
inserted `<family lang="und-Cyrl">` into 48 named font lists. Android 17 rejects
that attribute before `system_server` can start. At 17:48:16.110 Zygote reports
its fatal exception; init kills `cameraserver` at 17:48:16.155; the face HAL then
aborts with `DEAD_OBJECT` at 17:48:16.158. The face crashes are downstream of the
Zygote restart in this capture. FaceID should be tested again after boot works.

The adapter now inserts attribute-free families. Their bundled Cyrillic-only
glyph maps provide coverage without a language attribute. Existing Latin/digit
faces, aliases, clock families, weights and variable axes are retained. Preparation
accepts the immutable original, the corrected output, or the exact old output
(SHA-256 `e606e8315cad959520194c7d23e5665065a96f2058b63fd96d6088b78467ce61`)
for automatic migration/revert. It refuses unfamiliar local edits.

Validation includes the unchanged, checksum-locked `FontListParser.java` and
`FontCustomizationParser.java` from the pinned framework revision
`4af276bc5ceea071ef0da5ea440cda4470c08df6`. With host XML/data adapters, the
parsers reproduce the exact legacy exception and parse the corrected XML while
retaining all named families. Regression cases cover forbidden attributes,
valid locale-fallback language selection, upgrade/repeat/revert, local-edit
refusal, installed-output checks and the actual Russian glyph maps. These host
checks do not test native shaping, a full Android image or physical boot.

From the existing Coral source directory, keeping its build outputs:

```bash
git -C .repo/local_manifests fetch origin fix/coral-ui-compatibility-20261102 &&
git -C .repo/local_manifests switch --detach FETCH_HEAD &&
repo sync -c --fail-fast --no-clone-bundle --no-tags &&
env -u HECKER_BUILD_JOBS USE_RBE=true HECKER_DEVICE=coral HECKER_BUILD_VARIANT=userdebug bash .repo/local_manifests/build.sh
```

The helper uses Android's default build parallelism with RBE enabled. The changed
font XML is an input to product-image packaging and should rebuild incrementally.
The final output check rejects the old XML, missing/corrupt subsets, or mismatched
configuration. Full ServerHive compilation and physical Coral boot remain pending.

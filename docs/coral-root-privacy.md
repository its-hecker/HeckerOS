# Coral root privacy test

Branch: `test/coral-root-privacy-20261027` in HeckerOS, KernelSU-Next, the Floral kernel, frameworks/base and the Coral local manifest repositories. Stable branches and the Flame manifest are unchanged.

## Corrected Suite Polish baseline — 27 October 2026

The initial test used Coral's pinned `main` snapshot. That was the wrong baseline for the active development track: it omitted the newer Suite Polish source set. This correction merges the complete feature baseline into the test branches and retains the reviewed root privacy changes. History is preserved without force pushes.

| Project | Retained feature baseline |
| --- | --- |
| Coral local manifest — `feat/suite-polish` | `b09a85d3401a0f568f2e6568a370cc81d722ff63` |
| Hecker Suite — `feat/suite-polish` | `a51eacc198efe0a79a1f5e5e455c422064206e95` |
| Floral kernel — `feat/game-booster-reference` | `48d6881fb8537f62811209927022e467d37e3a98` |
| Framework — `heckeros-17` | `854e6dbf73ae1cd61095085a9655c8d9136104eb` |
| Settings — `feat/suite-polish` | `3831282d93ecda4d52a6894077482fe01ecea903` |
| Vendor custom — `feat/about-phone-official` | `302ac1f722258085326e38d45e2742a067316d59` |

This retains the Suite Polish UI, NetLab, Spoofing Hub, Pakistan IMS integration and matching build helpers and dependency graph. Settings and vendor custom remain at their feature baseline pins. The current immutable test revisions are recorded in the Coral manifest and `source-revisions.json`. The Suite's own integration manifest follows its test branch; the outer Coral manifest pins the exact Suite commit.

Use the Coral local manifest's matching test branch to sync the complete set. Selecting only the Suite branch does not enable kernel or framework changes. Start from the test branch build recipe in the Coral local manifest README, preferably in a fresh source directory.

## Scope

The test connects the already-imported Coral mount allocator to the legacy manual-hook KernelSU driver. Mount metadata filtering follows KernelSU's non-root unmount profiles. Supported root manager packages can also be hidden from user-selected apps through **Hecker Suite → Root manager privacy**. Selection is scoped to the Android user. Existing root grants are not replaced.

For UBL and Native Detector, keep root access disabled and enable their **Umount modules** profiles in KernelSU. Select those apps on the privacy page, then close and reopen them. Preserve YouTube's existing module-enabled/custom profile.

The driver option is disabled by default and enabled in the experimental Coral kernel configuration. The framework feature is enabled only by the Coral product property. This is a limited mount and package-visibility change; it does not provide a complete SUSFS integration or guarantee banking-app compatibility. Native Detector can still report the framework compatibility-resource names. Keybox and attestation configuration are not changed by this patch.

## Validation and rollout

The corrected Suite passes all 112 Python host tests, the additional Java/native host regressions, locked-SDK Java compilation, Android resource linking, AIDL permission generation, the separate NetLab controller checks and independent native Settings dashboard compilation. The framework root-visibility host checks pass. The correction also checks that non-root kernel configuration values and unrelated feature manifest pins are retained.

The original main-based test generated `floral_defconfig`, but its kernel object build stopped at compiler/toolchain validation. No complete production kernel or ROM build, Coral boot, detector acceptance or UBL test is claimed for this corrected source set. Strong Integrity is reported by the user; the provided Play screenshot establishes Play Protect certification rather than a complete Integrity verdict.

Coral is the only rollout target. Carry the test pins to Flame only after Coral testing succeeds.

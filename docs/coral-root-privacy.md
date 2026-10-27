# Coral root privacy test

Branch: `test/coral-root-privacy-20261027` in HeckerOS, KernelSU-Next, the Floral kernel, frameworks/base and the Coral local manifest repositories. The stable branches and the Flame manifest are not part of this change.

Use the Coral local manifest's matching test branch to sync the complete set. It pins the Suite, framework, kernel and KernelSU driver together; selecting only the Suite branch does not enable kernel or framework changes.

The first test connects the already-imported Coral mount allocator to the legacy manual-hook driver. Mount metadata filtering follows KernelSU's non-root unmount profiles. Supported root manager packages can also be hidden from user-selected apps through **Hecker Suite → Root manager privacy**. Selection is scoped to the Android user. Existing root grants are not replaced.

For UBL and Native Detector, keep root access disabled and enable their **Umount modules** profiles in KernelSU. Select those apps on the new privacy page, then close and reopen them. Preserve YouTube's existing module-enabled/custom profile.

Suite Java/resource compilation, host policy tests and real `floral_defconfig` generation passed. The local kernel object build stopped at compiler/toolchain validation; a complete production kernel/ROM build and phone tests are still required. UBL compatibility is not confirmed. Native Detector's framework compatibility-resource warning remains expected; this patch does not alter keybox or attestation configuration.

The driver option is disabled by default and enabled in the experimental Coral kernel configuration. The framework feature is enabled only by the Coral product property. Do not carry the test pins to Flame until Coral testing succeeds.

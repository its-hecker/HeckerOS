# Coral Bionic build fix — 3 November 2026

The attached `error_log_its_hecker_coral_20261103-015638.txt` stops while
compiling the new `custom_rom_hide.cpp` helper. It reports the same three
`-Werror,-Wframe-larger-than` errors in ARM and ARM64 platform/APEX variants.
The path checks used multiple `PATH_MAX` arrays, producing stack frames of
roughly 12 KiB, 8 KiB and 4 KiB. Bionic's limit is 2,048 bytes. The original
host boundary check did not enforce that production compiler rule.

The helper now uses per-call 256-byte buffers for common paths, with bounded
temporary anonymous mappings for long paths. Path checks do not enter heap
allocator locks. Long-path normalization, directory-fd resolution
and long working directories keep their existing coverage. Allocation failures
retain the original kernel result or descriptor and preserve errno. Ordinary
short path checks do not allocate. Scratch buffers are never shared between
threads or nested calls. The readlink decoder uses a buffer sized for the
bounded memfd name instead of a full `PATH_MAX` array.

The fix keeps the compiler's stack limit and all visibility options. It does
not change RBE, build parallelism, the bootloader setting or `addon.d` policy.

The native preparation tool recognizes the exact published helper by Git blob
SHA and replaces it automatically. Unrecognized local changes are preserved
and rejected. `native-compat/copy-history.json` records that migration; the
published input and its SHA-256 are locked in `native-compat/source-lock.json`.

Validation:

- Production-helper host compilation enforces the 2,048-byte frame limit with
  optimization, stack protection and automatic local-variable initialization.
- Clang 18.1.3 compiles the helper and all three owned public wrappers against
  the real pinned PixelOS Bionic headers for ARM and ARM64, at both `-O2` and
  `-Os`, for platform and APEX variants: 32 object compilations.
- Maximum measured static frames across those variants are 928 bytes on ARM64
  and 856 bytes on ARM. The same checks reject the exact previously published
  oversized helper on both architectures.
- Executed boundary checks cover invalid pointers, kernel error ordering,
  fortify, long and ordinary paths, long working directories, allocation failure,
  scratch-buffer cleanup, read-only/CLOEXEC handles, short readlink buffers and
  property API consistency. The existing service and syscall-generator checks pass.
- Fresh preparation, upgrade from the published copy, idempotence, rejection of
  unfamiliar local edits and exact reversion pass.

CI now runs the Android header cross-compilation checks. These object checks
do not replace the full Soong/APEX link or the resumed ServerHive image build,
which remain pending. ServerHive's logged compiler is clang-r596125; the local
cross-compilation check uses Clang 18.1.3 with the relevant libc warning flags.

To resume, update the existing local manifest on
`fix/coral-ui-compatibility-20261102`, run `repo sync`, then use the normal build
helper. Retain `out/`; the changed helper will be recopied and recompiled.

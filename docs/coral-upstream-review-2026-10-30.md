# Coral upstream review — 30 October 2026

Scope is Pixel 4 XL (`coral`). New source imports are isolated on
`feat/coral-upstream-20261030`; the Suite and Coral build manifest use
`feat/living-flow-polish-20261030`. No Flame manifest branch was advanced.

| Input | Reviewed result |
| --- | --- |
| PixelOS framework | Five commits through `044b965aa2d30d056b930e5ff013ba5303cde9fa`: MMS bounds/OOM hardening, coarse-location privacy indication, flashlight availability and complete maximum-refresh-rate request plumbing. Imported as `c72dce10290956bd3c4bfd23e787800d1340c46e`. |
| PixelOS vendor/custom | Native face-HAL opt-out through `77bcd52fc22f6fdb792dea52b5ccde3be39ef250`, imported as `82457873ad4f7c75195c21ddf9da35903a0d975f`. Coral explicitly declares its native face HAL before inheritance and retains software face-unlock exclusion. |
| PixelOS vendor/lineage | 21 commits through `049661a85f5b47893fed9104e644a625b2a47840`, imported as `a8286e3a3edc3aed7e774ca57476477f178a0c46`. Includes October platform release flag, phone DocumentsUI styling, mobile freeform declaration, setup/build changes and conditionally inactive tablet/foldable changes. |
| PixelOS manifest | Two commits through `1f53b335fdbf44bc04906248056f878eb52588fc`: follows Lineage certificate/blocklist sources and PixelOS libmeminfo. All three additions and their AOSP removals are included. |
| Lineage device | Current `android_device_google_coral` head `6a4ad9f39145ba7c6ff927041b6e9ff453edc1f8` is an ancestor of the imported Nahida device baseline `6fd94b97c7af048b064859c6d6f282a8646eead2`. No device tree replacement is needed. Existing Hecker device pin remains `4829092f7b7b988fc5fb2efe57c481f266364c71`. |
| Muppets vendor | Current Coral lineage-24.0 head `c957fead4960a897ff75132f3acc5ec64d492ada` matches the imported vendor base. No blob replacement is needed; existing Hecker vendor pin is retained. |
| Lineage kernel | Five commits after common ancestor `28f9290ae067d3e7857126eb6312720dbe9c7c80` through `bb17b7de4b570f2c0c32ddba0543cac03c58dd84`. Cgroup parsing/freezer updates are applied over the exact CNB/Hecker tree, published as `5303303fd0138a719c0077570975fdce6ace661f`. |

The other 87 previously locked PixelOS project branch heads match their recorded
commits. Adding libmeminfo gives 92 PixelOS-derived platform entries: four owned
customization forks and 88 pinned upstream projects. The two new Lineage trust
projects are independently pinned in `snippets/lineage.xml`.

## Security release and preservation

The inherited `cp2a` release flag changes platform SPL from **2026-09-01** to
**2026-10-01** as part of the vendor source import. No local patch-date override
was added. This does not advance Coral's vendor/firmware SPL, replace modem or
bootloader firmware, or establish that an unbuilt ROM has passed security tests.

New trust pins are Conscrypt `98b5842746b92120e30ba3ddc8b1f975b8b7f897`
(eMudhra intermediate blocklist) and ca-certificates
`3eafa7527b96dee31eb8afd7586f3e4ca8c07de0` (emSign root removals).
Libmeminfo `e970d14536c482bc08f46b93ed2a6185f83a165c` avoids opening a missing
GPU BPF memory map, relevant to older kernels lacking that tracing facility.

Framework and vendor three-way merges are clean. The low-brightness display
director changes remain identical apart from the upstream's additive refresh-rate
entrypoint; root visibility, FaceID and other Hecker helper files are unchanged.
Kernel changes touch only five documented cgroup/signal paths; KSU/SUSFS,
root-privacy and leased charging code are unchanged. The imported kernel's real
history was used for the merge base, since the Hecker repository originally
imported its source as a tree snapshot rather than retaining Lineage ancestry.

Source validation covers immutable pin consistency, exact published tree hashes,
native adapter upgrade/reversion, display hysteresis methods, and the imported MMS
length/skip guards under a bounded heap. It does not replace the upstream Android
test suites, a complete kernel/ROM build, or Coral boot/runtime validation.

## Future reviews

Keep explicit commit pins. Compare PixelOS `seventeen` plus its manifest additions,
Lineage Coral device/kernel `lineage-24.0`, and Muppets Coral vendor `lineage-24.0`
against this record before advancing them. Review against imported ancestry and
keep Hecker deltas, then rebuild and test Coral. `tools/freeze_upstream.py --check`
checks lock consistency offline; it does not discover or merge new upstream work.
Flame requires a separate user-requested update and validation pass.

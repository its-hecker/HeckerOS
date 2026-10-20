The Pixel enrollment grid and native integrity controls

The HeckerOS Settings fork now draws the original Pixel 25-cell multi-angle grid.
The small rendering-only JAR is pinned and hashed; Google enrollment activities,
sidecars and camera controllers are excluded. Native Settings forwards actual
vendor pose acquisition codes 1101–1125. The existing HAL-owned preview surface,
fixed buffer dimensions, credential flow and remaining==0 completion are retained.
Filling the grid cannot enroll a face or finish enrollment. Pause, error,
completion and detach stop UI handlers and animations. Generic preview devices
keep their existing progress ring. There is no extra launcher application.

Play Integrity uses Axion's October 19, 2026 framework change:
https://github.com/AxionAOSP/android_frameworks_base/commit/a04d222e97d60c03e28479adf1cf39dd5002827a

That is the newest PIF commit verified on the public lineage-23.2 branch. Axion's
public manifest branch named lineage-24.0 still points to Android 16 / lineage-23.2
sources at this checkpoint. This is an adaptation of the verified change to our
pinned Android 17 framework, not a claim about a tested Axion Android 17 release.
Exact source, dates and adaptations are recorded in the framework fork's
core/java/android/security/pif/HECKEROS-ORIGIN.json.

Open Settings > Hecker's Suite > Play Integrity. Fetch a Google beta profile or
import a JSON/prop profile, then restart the phone. The page can also disable the
profile. A stale hardcoded fingerprint is not enabled by default. Fetching reads
public Google metadata and at most the first 4 KB of an OTA; it does not download
or install the OTA. Profile writes use privileged native Settings. The framework
reads public build metadata from the hecker_pif_config Global setting and applies
it only to the attestation process and Play Store. Native property virtualization,
optional provider/signature hooks run only in the attestation process. No Photos,
game, keybox or TrickyStore hooks are installed. No private attestation keys are
included. Other processes retain the ROM's real build properties.

Validation performed: locked SDK Java/resource compilation for the new Settings
classes, standalone Suite UI compilation, face pose state regressions, profile
and partial OTA metadata parsing, framework PIF parser/process-scope regressions,
and native mapping/rule/write-bound tests under address/undefined-behavior
sanitizers. Source adapters apply idempotently and revert against pinned native
files. The standalone migration helper refuses manifest activation when any owned
repository or reviewed source revision is missing.

A full Soong/Settings/framework build has not been run. Coral testing must still
confirm the preview, pose directions, cancel/retry, screen off/on, dark theme,
reduced motion, removal/re-enrollment and the actual Play Integrity verdict.
Passing a host test does not establish a Google attestation verdict.

The platform source migration is partial: four of 91 PixelOS-derived projects
have verified owned branches. Follow migration/README.md for the remaining 87.
Owned forks do not automatically receive upstream changes or phone updates.

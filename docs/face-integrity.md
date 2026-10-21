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
optional provider/signature hooks run only in the attestation process. Photos
retains its existing PixelOS implementation; no Axion Photos hook is added.

The same native page now includes Axion GameProps and TrickyStore support from
framework revision `2017624b588861735445868ca809260bf2499f58`:

* Game profiles import an Axion JSON object with `enabled` and a `games` map.
  Each package maps to device identity strings, for example
  `{"enabled":true,"games":{"com.example.game":{"MODEL":"Custom model"}}}`.
  Only that package's real UID receives its profile. Android API-level fields
  are not exposed by the game editor.
* TrickyStore targets accept JSON arrays of `package`/`mode` objects, or one
  package per line. No suffix selects AUTO; `?` selects LEAF_HACK; `!` selects
  GENERATE. Targets are explicitly selected and limited to the primary user.
* Keybox import accepts XML or base64 XML. No private attestation keys are bundled
  or downloaded. The parser validates key/certificate pairing and rejects XML
  entities and oversized input. Import your own keybox and choose its target apps.
* Optional patch dates accept a JSON object with `system`, `vendor`, `boot` or
  `all`, or text such as `all=2026-10-05`. These are attestation values, not an
  installed Android security update. Each configuration can be removed separately.

Game/target/keybox/patch configurations are stored atomically in private 0600
system files and managed through native Settings's system UID. Private keys are
never stored in SettingsProvider. Binder reads scope targets and game profiles
to packages owned by the caller's real UID; only selected app UIDs receive a
keybox. System/secondary/isolated UIDs do not use the app certificate hooks.
The earlier PixelOS PIF/keybox paths defer when the corresponding Axion profile
owns the request. The optional PIF blocking provider is skipped for a configured
TrickyStore keybox, so it cannot block that certificate path.

Both leaf replacement and software generation are implemented. Software generation
requires an attestation challenge and an imported keybox. Authentication-bound,
device-ID, explicit attestation-key, ML-DSA and Curve25519 generation keep their
native Android 17 behavior. The real keystore still stores imported app keys.
The port fixes EC signing with Android 17's repackaged Bouncy Castle, preserves
certificate metadata after import, and reads the app signing identity from
PackageManager even before Application creation. Unconfigured apps retain their
normal build properties and certificate handling.

Validation performed: locked SDK Java/resource compilation for the new Settings
classes, standalone Suite UI compilation, face pose state regressions, profile
and partial OTA metadata parsing, framework PIF parser/process-scope regressions,
and native mapping/rule/write-bound tests under address/undefined-behavior
sanitizers. The expanded port type-checks the modified keystore classes and
support classes against a checksum-locked Android 17 implementation JAR; stubs
cover generated flags and the added/private PixelOS API surfaces. Host tests run
the production configuration and RSA/EC certificate code with Android 17's JSON
and repackaged Bouncy Castle, replacing OS/Binder boundaries with fakes.
Source adapters apply idempotently and revert against pinned native files.

A full Soong/Settings/framework build has not been run. Coral testing must still
confirm the preview, pose directions, cancel/retry, screen off/on, dark theme,
reduced motion, removal/re-enrollment and the actual Play Integrity verdict.
Passing a host test does not establish a Google attestation verdict.

Four of 91 PixelOS-derived projects have owned branches. The remaining 87 stay
upstream with exact source pins. Full repository migration is optional and is not
a build prerequisite. Neither upstream fixes nor phone updates arrive
automatically; updates require explicit source changes, another build and testing.

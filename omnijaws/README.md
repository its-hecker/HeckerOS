# OmniJaws on the PixelOS base

The pinned RisingOS OmniJaws app references `ax_compose` and
`com.crdroid.apps.resources`, which are absent from this PixelOS manifest. It also
imports a RisingOS framework `OmniJawsClient` that PixelOS does not supply.

`tools/prepare.py` applies the guarded replacements in `patches.json` and copies
the native Material components in `HeckerWeatherUi.kt` into the app. The screens
retain their existing callbacks, providers, location permission handling, API key
settings, dashboard and widgets. The Pebble widget uses the existing Material
dynamic-color theme. The launcher weather client is reused with an app-local
package; its provider URIs, projections and data types remain the same. See
`launcher/OmniJaws.ORIGIN.json` for that client's upstream provenance and retained
license.

No Axion vendor modules or replacement weather services are added. All edits
join the preparation tool's validated, backed-up transaction. Reapplying is safe;
reverting restores the original source. Changed anchors or edited generated
helpers stop preparation for review rather than overwriting local work.

`python3 tools/check_native_ports.py` checks application, reapplication and exact
reversion against the immutable revision in `ORIGIN.json`.
`python3 tools/check_omnijaws.py --sdk-cache generated/sdk` compiles the adapter
with locked Kotlin/Compose libraries, compiles the shared Java client and links
the added resources. This portable API check does not compile the full app. Full Android/Soong
compilation and weather testing on coral remain required.

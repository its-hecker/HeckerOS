# Russian translations for HeckerOS custom resources

Added Russian coverage for all 1,120 translatable custom resources found in the
audited source groups, including four string arrays with their original item
counts. Existing Russian entries are preserved. Product names, protocol names,
format-only values and identifiers retain their intended spelling.

| Resource group | Russian entries |
| --- | ---: |
| Hecker Launcher3 custom UI | 170 |
| Hecker Suite and native dashboard UI | 790 |
| Retained launcher settings resources | 11 |
| NetLab controller | 7 |
| Native Settings cards and About phone | 53 |
| SystemUI Quick Settings/media/volume | 56 |
| Face unlock island | 3 |
| Headphone connection cards | 10 |
| IMS accessibility labels | 2 |
| Motion Island | 18 |

Covers Launcher Studio, clock messages, home/search/icon controls, GameSpace,
NetLab controls/status, themes, lyrics, status rows, root-free sensor recovery,
Settings/About phone, headphone cards, charging and motion controls. Gaming
metric labels and their accessibility description now use localized resources.
The separately maintained Android and Google applications keep their upstream
language packs; this change does not replace those projects.

SystemUI and Settings translations are registered in the native copy plan.
Launcher3 custom resources are copied through its existing generated-resource
port. App/controller locale directories are packaged by their existing Soong
resource directories. Locale support is selected by the Android system language;
this does not force a language change or alter the English defaults.

Validation checks full default/Russian resource coverage by type/name, ordered
array sizes and printf arguments (including literal percentage signs). Suite
resource linking and native source preparation/upgrade checks passed. Actual
phone screenshots at Russian language and large font/display sizes remain
required for final typography acceptance.

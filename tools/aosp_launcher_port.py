# SPDX-License-Identifier: Apache-2.0
"""Build an original Android 17 Launcher3 HOME; retain Pixel's Quickstep provider."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('packages/apps/Launcher3')

def aosp_launcher_plan(rom, replace, revert=False):
    source=rom/SOURCE
    if not source.is_dir():return {}
    patches=json.loads((ROOT/'aosp-launcher/patches.json').read_text())
    changes={}
    for relative,pairs in patches.items():
        path=source/relative;current=path.read_text();target=current
        for pair in reversed(pairs) if revert else pairs:
            target=replace(target,pair['old'],pair['new'],str(path),revert)
        if current!=target:changes[path]=target
    manifest=source/'AndroidManifest.xml'
    expected=manifest.read_text().replace('android:debuggable="true"','android:debuggable="false"')
    expected=expected.replace('    <application','    <uses-permission android:name="android.permission.DEVICE_POWER" />\n    <application',1)
    copies={'generated/hecker-launcher/AndroidManifest.xml':expected}
    for path in (ROOT/'aosp-launcher/src').rglob('*.java'):
        copies['src/'+str(path.relative_to(ROOT/'aosp-launcher/src'))]=path.read_text()
    for path in (ROOT/'aosp-launcher/res').rglob('*.xml'):
        copies['generated/hecker-launcher/res/'+str(path.relative_to(ROOT/'aosp-launcher/res'))]=path.read_text()
    for relative,expected in copies.items():
        path=source/relative;current=path.read_text() if path.exists() else None
        if current is not None and current!=expected:
            raise ValueError('Preserving unfamiliar Hecker Launcher source: '+str(path))
        if revert:
            if path.exists():changes[path]=None
        elif current!=expected:changes[path]=expected
    return changes

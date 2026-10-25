# SPDX-License-Identifier: Apache-2.0
"""Build an original Android 17 Launcher3 HOME; retain Pixel's Quickstep provider."""
import json
import hashlib
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
            if not revert:
                for previous in sorted(pair.get('previous',[]),key=len,reverse=True):
                    if previous in target and (pair['new'] not in target or len(previous)>len(pair['new'])):
                        if target.count(previous)!=1:raise ValueError('Duplicate old Hecker Launcher adapter: '+str(path))
                        target=target.replace(previous,pair['new'],1);break
            target=replace(target,pair['old'],pair['new'],str(path),revert)
        if current!=target:changes[path]=target
    manifest=source/'AndroidManifest.xml'
    expected=manifest.read_text().replace('android:debuggable="true"','android:debuggable="false"')
    expected=expected.replace('    <application','    <uses-permission android:name="android.permission.DEVICE_POWER" />\n    <application',1)
    previous_manifest=expected
    expected=expected.replace('    <application','    <uses-permission android:name="android.permission.EXPAND_STATUS_BAR" />\n    <uses-permission android:name="android.permission.READ_WALLPAPER_INTERNAL" />\n    <application',1)
    copies={'generated/hecker-launcher/AndroidManifest.xml':expected}
    for path in (ROOT/'aosp-launcher/src').rglob('*.java'):
        copies['src/'+str(path.relative_to(ROOT/'aosp-launcher/src'))]=path.read_text()
    for path in (ROOT/'aosp-launcher/res').rglob('*.xml'):
        copies['generated/hecker-launcher/res/'+str(path.relative_to(ROOT/'aosp-launcher/res'))]=path.read_text()
    history_file=ROOT/'aosp-launcher/copy-history.json'
    history=json.loads(history_file.read_text()) if history_file.exists() else {}
    for relative,expected in copies.items():
        path=source/relative;current=path.read_text() if path.exists() else None
        identity=hashlib.sha1(b'blob '+str(len(current.encode())).encode()+b'\0'+current.encode()).hexdigest() if current is not None else None
        known_manifest=relative=='generated/hecker-launcher/AndroidManifest.xml' and current==previous_manifest
        if current is not None and current!=expected and identity not in history.get(relative,[]) and not known_manifest:
            raise ValueError('Preserving unfamiliar Hecker Launcher source: '+str(path))
        if revert:
            if path.exists():changes[path]=None
        elif current!=expected:changes[path]=expected
    return changes

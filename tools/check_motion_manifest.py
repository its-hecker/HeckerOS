#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise Motion Sense manifest integration with Android 17's real merger.

This checks the pinned plugin, resource dependency and Settings host boundary.
It does not replace a full Settings/Soong build or device tests.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from compile_sdk import fetch
from prepare import (ANDROID, MOTION, MOTION_ACTIVITY, MOTION_MANIFEST,
                     MOTION_NATIVE_MANIFEST, MOTION_RESOURCES_MANIFEST,
                     motion_plan)

ROOT = Path(__file__).resolve().parents[1]


def snapshot(folder):
    return {str(path.relative_to(folder)): path.read_bytes()
            for path in folder.rglob('*') if path.is_file()}


def write(changes):
    for path, content in changes.items():
        if content is None:
            path.unlink()
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)


def fixed(source, target):
    # Soong's manifest_fixer supplies SDK declarations before the merger. Use
    # the plugin's existing min/target SDK for this isolated dependency check.
    manifest = ET.fromstring(source)
    if manifest.find('uses-sdk') is None:
        ET.SubElement(manifest, 'uses-sdk', {ANDROID + 'minSdkVersion': '30',
                                         ANDROID + 'targetSdkVersion': '30'})
    ET.register_namespace('android', ANDROID[1:-1])
    target.write_text(ET.tostring(manifest, encoding='unicode'))
    return target


def check_private_ui(manifest, count):
    activities = manifest.findall('application/activity')
    assert len(activities) == count, 'Native controls were lost during merge'
    assert all(node.get(ANDROID + 'exported') == 'false' for node in activities)
    assert all(node.get(ANDROID + 'name').startswith('org.hecker.floral.motionsense.')
               for node in activities)
    assert not manifest.findall('application/service'), 'Standalone tile leaked into Settings'
    assert not manifest.findall('application/activity/intent-filter'), 'Standalone entry leaked into Settings'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / 'generated/manifest-check')
    args = parser.parse_args()
    cache = args.cache.resolve()
    lock = json.loads((ROOT / 'tests/manifest-merger-inputs.lock.json').read_text())
    project = next(node for node in ET.parse(ROOT / 'snippets/heckeros.xml').getroot().findall('project')
                   if node.get('path') == str(MOTION.parent))
    prefix = 'https://raw.githubusercontent.com/{}/{}/'.format(project.get('name'), project.get('revision'))
    assert all(item['url'].startswith(prefix) for name, item in lock.items() if name.startswith('source/'))
    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(lambda item: fetch(cache, *item), lock.items()))
    classpath = os.pathsep.join(str(cache / name) for name in lock if name.endswith('.jar'))
    with tempfile.TemporaryDirectory(prefix='hecker-motion-merge-') as temporary:
        out = Path(temporary)
        rom = out / 'rom'
        plugin = rom / MOTION.parent
        shutil.copytree(cache / 'source', plugin)
        settings = rom / MOTION
        shutil.copyfile(settings / 'Android.bp.in', settings / 'Android.bp')
        before = snapshot(rom)
        write(motion_plan(rom))
        assert not motion_plan(rom), 'Motion adapter is not idempotent'
        source = (settings / 'AndroidManifest.xml').read_text()
        count = len(ET.fromstring(source).findall('application/activity'))
        native = fixed((settings / MOTION_NATIVE_MANIFEST).read_text(), out / 'native.xml')
        resources = fixed((settings / MOTION_RESOURCES_MANIFEST).read_text(), out / 'resources.xml')
        original = fixed(source, out / 'original-resources.xml')

        def merge(main, library, name, conflict=False):
            target = out / (name + '.xml')
            result = subprocess.run(['java', '-cp', classpath, 'com.android.manifmerger.Merger',
                '--main', str(main), '--libs', str(library), '--out', str(target)],
                text=True, capture_output=True)
            if conflict:
                assert result.returncode != 0, 'Original exported conflict did not reproduce'
                assert 'MotionSenseActivity@exported' in result.stdout + result.stderr, result.stdout + result.stderr
                print('Original resource-library activity/exported merge conflict: reproduced')
                return None
            if result.returncode:
                raise SystemExit(result.stdout + result.stderr)
            return target

        merge(native, original, 'original-conflict', conflict=True)
        merged_native = merge(native, resources, 'merged-native')
        manifest = ET.parse(merged_native).getroot()
        check_private_ui(manifest, count)
        assert not manifest.find('application').attrib
        assert ANDROID + 'sharedUserId' not in manifest.attrib
        host = fixed('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="com.android.settings" android:sharedUserId="android.uid.system">'
            '<application android:name="com.android.settings.SettingsApplication"/></manifest>', out / 'host.xml')
        merged_host = ET.parse(merge(host, merged_native, 'merged-settings-host')).getroot()
        check_private_ui(merged_host, count)
        assert merged_host.get('package') == 'com.android.settings'
        assert merged_host.find('application').attrib == {ANDROID + 'name': 'com.android.settings.SettingsApplication'}
        standalone = fixed((settings / MOTION_MANIFEST).read_text(), out / 'standalone.xml')
        merged_standalone = ET.parse(merge(standalone, resources, 'merged-standalone')).getroot()
        activities = merged_standalone.findall('application/activity')
        assert len(activities) == count
        assert next(node for node in activities if node.get(ANDROID + 'name') == MOTION_ACTIVITY).get(ANDROID + 'exported') == 'true'
        original_services = ET.fromstring(source).findall('application/service')
        assert len(merged_standalone.findall('application/service')) == len(original_services)
        assert merged_standalone.find('application/service').get(ANDROID + 'permission') == 'android.permission.BIND_QUICK_SETTINGS_TILE'
        assert merged_standalone.find('application').get(ANDROID + 'persistent') == 'true'
        write(motion_plan(rom, True))
        assert before == snapshot(rom), 'Motion adapter did not revert exactly'
    print('Android 17 merger: native controls/private activities, Settings host and standalone tile: PASS')
    print('Pinned Motion Sense adapters apply, reapply and revert exactly; full ROM/phone checks remain required.')


if __name__ == '__main__':
    main()

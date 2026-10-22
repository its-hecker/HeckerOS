#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check adapters against immutable upstream source files; this is not a ROM build."""
import argparse
import base64
import json
from pathlib import Path
import re
import shutil
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from customization_port import customization_plan
from launcher_port import launcher_plan
from prepare import replace_guarded
from privapp_permissions import check_privapp_permissions

ROOT = Path(__file__).resolve().parents[1]


def write(changes):
    for path, value in changes.items():
        if value is None:
            path.unlink()
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(value if isinstance(value, bytes) else value.encode())


def snapshot(folder):
    return {str(p.relative_to(folder)): p.read_bytes() for p in folder.rglob('*') if p.is_file()}


def check_bootanimation_owner(folder):
    # Check the real module definitions, independently of PRODUCT_PACKAGES: Soong
    # exports install actions even for the unselected PixelOS prebuilt.
    owners = []
    for path in (ROOT / 'Android.bp', folder / 'vendor/custom/bootanimation/Android.bp'):
        for block in re.findall(r'(?ms)^prebuilt_media\s*\{(.*?)^\}', path.read_text()):
            if not re.search(r'(?m)^\s*filename:\s*"bootanimation\.zip",', block):
                continue
            assert re.search(r'(?m)^\s*product_specific:\s*true,', block), 'Boot animation partition changed'
            if not re.search(r'(?m)^\s*enabled:\s*false,', block):
                owners.append(re.search(r'(?m)^\s*name:\s*"([^"]+)"', block).group(1))
    assert owners == ['bootanimation_heckeros'], 'Conflicting boot animation install owners: ' + str(owners)
    print('product/media/bootanimation.zip has exactly one enabled prebuilt owner: HeckerOS')


def check_launcher_weather(folder):
    launcher = folder / 'packages/apps/Launcher3'
    controller = (launcher / 'src/com/android/launcher3/quickspace/QuickspaceController.java').read_text()
    assert 'switchProvider(WeatherProvider.SERAPHIX)' in controller, 'Google weather was removed'
    assert 'mProvider = WeatherProvider.OMNIJAWS' not in controller, 'OmniJaws fallback remains'
    assert 'OmniJawsClient.get(' not in controller, 'OmniJaws client startup remains'
    assert 'addOmniJawsIfEnabled(' not in controller, 'OmniJaws startup remains'
    preferences = ET.parse(launcher / 'res/xml/launcher_home_screen_preferences.xml').getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    keys = {node.get(android + 'key') for node in preferences.iter()}
    assert 'pref_quickspace_weather' in keys, 'Google weather toggle was removed'
    assert not keys.intersection({'pref_quickspace_weather_provider',
                                  'pref_quickspace_weather_city',
                                  'pref_quickspace_weather_text'}), 'OmniJaws preferences remain'
    print('Launcher keeps Google weather; OmniJaws startup, fallback and controls are absent')


def check_launcher_resource_scope(folder):
    launcher = folder / 'packages/apps/Launcher3'
    bp = (launcher / 'Android.bp').read_text()
    libraries = re.findall(r'(?ms)^android_library\s*\{(.*?)^\}', bp)
    library = next(b for b in libraries if re.search(
        r'name:\s*"Launcher3QuickStepLib"', b))
    resources = re.search(r'resource_dirs:\s*\[(.*?)\]', library, re.S).group(1)
    directories = re.findall(r'"([^"]+)"', resources)
    view = (launcher / 'src/com/android/launcher3/hecker/HeckerLottieView.java').read_text()
    for name in re.findall(r'R\.raw\.(\w+)', view):
        assert any(list((launcher / directory / 'raw').glob(name + '.*'))
                   for directory in directories), 'Raw resource missing from Java library: ' + name
    print('Lottie raw resources belong to Launcher3QuickStepLib, where the view compiles')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / 'out/native-source-cache')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    sources = json.loads((ROOT / 'integration/source-revisions.json').read_text())['pixelos_inputs']
    launcher = json.loads((ROOT / 'launcher/ORIGIN.json').read_text())
    sources['packages/apps/Launcher3'] = launcher
    paths = set(json.loads((ROOT / 'framework/customization-patches.json').read_text()))
    paths.add('frameworks/base/core/java/android/content/res/Resources.java')
    paths.add('frameworks/base/core/res/AndroidManifest.xml')
    paths.update('packages/apps/Launcher3/' + p for p in json.loads((ROOT / 'launcher/patches.json').read_text()))
    def cached_source(relative):
        project = max((p for p in sources if relative.startswith(p + '/')), key=len)
        # A pin update must never reuse content fetched for an older revision.
        return args.cache / sources[project]['revision'] / relative

    for relative in sorted(paths):
        cached = cached_source(relative)
        if cached.is_file():
            continue
        if args.offline:
            raise SystemExit('Missing immutable source: ' + relative)
        project = max((p for p in sources if relative.startswith(p + '/')), key=len)
        origin = sources[project]
        repository = origin['repository']
        googlesource = repository.startswith('https://android.googlesource.com/')
        if googlesource:
            url = '{}/+/{}/{}?format=TEXT'.format(
                repository, origin['revision'], relative[len(project) + 1:])
        else:
            url = 'https://raw.githubusercontent.com/{}/{}/{}'.format(
                repository, origin['revision'], relative[len(project) + 1:])
        with urllib.request.urlopen(url, timeout=45) as response:
            content = response.read(8 * 1024 * 1024 + 1)
        if len(content) > 8 * 1024 * 1024:
            raise SystemExit('Unexpected source size: ' + relative)
        if googlesource:
            content = base64.b64decode(content, validate=True)
        cached.parent.mkdir(parents=True, exist_ok=True)
        cached.write_bytes(content)
    with tempfile.TemporaryDirectory(prefix='hecker-native-check-') as temporary:
        folder = Path(temporary)
        for relative in paths:
            target = folder / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(cached_source(relative), target)
        before = snapshot(folder)
        required = check_privapp_permissions(folder / 'frameworks/base/core/res/AndroidManifest.xml', ROOT)
        print(f'Suite allowlist covers {len(required)} privileged permissions in the pinned Android framework')
        plans = (customization_plan, launcher_plan)
        count = 0
        for plan in plans:
            changes = plan(folder, replace_guarded)
            count += len(changes)
            write(changes)
            assert not plan(folder, replace_guarded), 'Adapter is not idempotent'
        check_bootanimation_owner(folder)
        check_launcher_weather(folder)
        check_launcher_resource_scope(folder)
        for plan in reversed(plans):
            write(plan(folder, replace_guarded, True))
        assert before == snapshot(folder), 'Revert changed upstream content'
    print(f'{len(paths)} immutable upstream files checked; {count} source adapters/copies apply idempotently and revert exactly.')
    print('Full Android/Soong compilation and coral testing are still required.')


if __name__ == '__main__':
    main()

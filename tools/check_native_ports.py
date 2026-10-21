#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check adapters against immutable upstream source files; this is not a ROM build."""
import argparse
import json
from pathlib import Path
import re
import shutil
import tempfile
import urllib.request
from customization_port import customization_plan
from launcher_port import launcher_plan
from omnijaws_port import omnijaws_plan
from prepare import replace_guarded

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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / 'out/native-source-cache')
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()
    sources = json.loads((ROOT / 'integration/source-revisions.json').read_text())['pixelos_inputs']
    launcher = json.loads((ROOT / 'launcher/ORIGIN.json').read_text())
    sources['packages/apps/Launcher3'] = launcher
    sources['packages/services/OmniJaws'] = json.loads((ROOT / 'omnijaws/ORIGIN.json').read_text())
    paths = set(json.loads((ROOT / 'framework/customization-patches.json').read_text()))
    paths.add('frameworks/base/core/java/android/content/res/Resources.java')
    paths.update('packages/apps/Launcher3/' + p for p in json.loads((ROOT / 'launcher/patches.json').read_text()))
    paths.update('packages/services/OmniJaws/' + p for p in json.loads((ROOT / 'omnijaws/patches.json').read_text()))
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
        url = 'https://raw.githubusercontent.com/{}/{}/{}'.format(
            origin['repository'], origin['revision'], relative[len(project) + 1:])
        with urllib.request.urlopen(url, timeout=45) as response:
            content = response.read(8 * 1024 * 1024 + 1)
        if len(content) > 8 * 1024 * 1024:
            raise SystemExit('Unexpected source size: ' + relative)
        cached.parent.mkdir(parents=True, exist_ok=True)
        cached.write_bytes(content)
    with tempfile.TemporaryDirectory(prefix='hecker-native-check-') as temporary:
        folder = Path(temporary)
        for relative in paths:
            target = folder / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(cached_source(relative), target)
        before = snapshot(folder)
        plans = (customization_plan, launcher_plan, omnijaws_plan)
        count = 0
        for plan in plans:
            changes = plan(folder, replace_guarded)
            count += len(changes)
            write(changes)
            assert not plan(folder, replace_guarded), 'Adapter is not idempotent'
        check_bootanimation_owner(folder)
        for plan in reversed(plans):
            write(plan(folder, replace_guarded, True))
        assert before == snapshot(folder), 'Revert changed upstream content'
    print(f'{len(paths)} immutable upstream files checked; {count} source adapters/copies apply idempotently and revert exactly.')
    print('Full Android/Soong compilation and coral testing are still required.')


if __name__ == '__main__':
    main()

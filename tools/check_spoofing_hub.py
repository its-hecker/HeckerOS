#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the real Spoofing hub parsers and certificate diagnostics without network or keys."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from check_platform_features import source
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--settings-source', type=Path)
    parser.add_argument('--cache', type=Path, default=ROOT/'out/spoof-deps')
    parser.add_argument('--java', default=os.environ.get('HECKER_JAVA', 'java'))
    args = parser.parse_args()
    lock = json.loads((ROOT/'tests/spoof-dependencies.lock.json').read_text())
    jar = fetch(args.cache, next(iter(lock)), next(iter(lock.values())))
    project = json.loads((ROOT/'integration/platform-features.json').read_text())['settings']
    settings = args.settings_source or source(project, ROOT/'out/platform-feature-sources')
    android = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(settings/'AndroidManifest.xml').getroot()
    assert manifest.get(android+'sharedUserId') == 'android.uid.system'
    for name in ['HeckerIntegrityActivity', 'HeckerSpoofingHubActivity']:
        component = manifest.find(f"./application/activity[@{android}name='com.android.settings.hecker.{name}']")
        assert component is not None and component.get(android+'exported') == 'false', name
    service = manifest.find(f"./application/service[@{android}name='com.android.settings.hecker.SpoofingMaintenance']")
    assert service is not None and service.get(android+'permission') == 'android.permission.BIND_JOB_SERVICE'
    assert (settings/'res/raw/hecker_spoofing_hub.json').read_bytes() == (ROOT/'app/res/raw/suite_spoofing.json').read_bytes()
    names = ['SpoofingTargets', 'SpoofingHttp', 'PifProfile', 'PixelCanaryProfileFetcher',
             'KeyboxStatus', 'SpoofingData', 'SpoofingMaintenance']
    files = [settings/f'src/com/android/settings/hecker/{name}.java' for name in names]
    files += [ROOT/'tests/platform-api-stubs/android/security/spoof/HeckerSpoofConfig.java',
              ROOT/'tests/spoofing/SpoofingHubTest.java']
    with tempfile.TemporaryDirectory(prefix='hecker-hub-check-') as directory:
        subprocess.run([args.java, '-m', 'jdk.compiler/com.sun.tools.javac.Main',
                        '-cp', str(jar), '-d', directory, *map(str, files)], check=True)
        subprocess.run([args.java, '-cp', directory+os.pathsep+str(jar),
                        'com.android.settings.hecker.SpoofingHubTest'], check=True)
    print('Spoofing hub imports, Canary metadata, HTTPS policy and synthetic certificate checks: PASS')


if __name__ == '__main__':
    main()

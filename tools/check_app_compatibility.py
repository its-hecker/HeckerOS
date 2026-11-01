#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Run app compatibility production-policy tests from immutable framework inputs."""
import argparse
import json
from pathlib import Path
import subprocess
from check_platform_features import source

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--framework-source',type=Path)
    parser.add_argument('--cache',type=Path,default=ROOT/'out/app-compatibility-sources')
    args=parser.parse_args()
    lock=json.loads((ROOT/'integration/app-compatibility.lock.json').read_text())
    framework=args.framework_source or source(lock,args.cache)
    # A supplied checkout also has to match the declared immutable source contents.
    import hashlib
    for name,digest in lock['files'].items():
        assert hashlib.sha256((framework/name).read_bytes()).hexdigest()==digest,name
    for test in ['test_root_visibility.py','test_app_compatibility.py']:
        subprocess.run(['python3',str(framework/'tests/hecker'/test)],check=True)
    print('Pinned app compatibility source and production policy checks: PASS')
    print('Full Android/SELinux build and on-phone app compatibility remain pending.')

if __name__=='__main__':main()

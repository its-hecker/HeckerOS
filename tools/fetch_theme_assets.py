#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fetch the 91 exact Lunaris theme APK resources before Soong scans the product."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def valid(path, item):
    return path.is_file() and not path.is_symlink() and path.stat().st_size == item['size'] and hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256']

def prepare(offline=False, source=None):
    lock = json.loads((ROOT/'themes/payload.lock.json').read_text())
    directory=ROOT/'themes/prebuilt';directory.mkdir(exist_ok=True)
    base='https://raw.githubusercontent.com/'+lock['repository']+'/'+lock['revision']+'/apks/'
    def fetch(pair):
        name,item=pair;target=directory/name
        if valid(target,item):return
        if target.is_symlink():raise ValueError('Refusing symlinked theme APK: '+name)
        if offline:raise ValueError('Missing or damaged theme asset: '+name)
        with tempfile.TemporaryDirectory(dir=directory) as temporary:
            staged=Path(temporary)/name
            if source is not None:shutil.copyfile(source/name,staged)
            else:
                request=urllib.request.Request(base+name,headers={'User-Agent':'HeckerOS-pinned-themes'})
                with urllib.request.urlopen(request,timeout=30) as response,staged.open('wb') as output:
                    count=0
                    while block:=response.read(65536):
                        count+=len(block)
                        if count>item['size']:raise ValueError('Theme asset exceeds locked size: '+name)
                        output.write(block)
            if not valid(staged,item):raise ValueError('Theme asset differs from locked bytes: '+name)
            staged.replace(target)
    with ThreadPoolExecutor(max_workers=4) as workers:list(workers.map(fetch,lock['files'].items()))
    print('91 Lunaris theme resource APKs match their pinned hashes.')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--offline',action='store_true')
    parser.add_argument('--source',type=Path,help='Optional existing copy of the pinned upstream apks directory')
    args=parser.parse_args();prepare(args.offline,args.source)

#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Rebuild the offline MAC table from a hash-locked, BSD-licensed IEEE data snapshot."""
import argparse
import collections
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import unicodedata
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
LOCK=ROOT/'netlab/vendor-registry.lock.json'

def compile_registry(raw):
    entries={}
    for prefix,value in json.loads(raw).items():
        if not re.fullmatch(r'[0-9A-F]{6}|[0-9A-F]{7}|[0-9A-F]{9}',prefix):
            raise ValueError('Unexpected prefix in locked source')
        if int(prefix[:2],16)&3:continue
        name=''.join(c for c in value.splitlines()[0] if not unicodedata.category(c).startswith('C')).strip()
        if not name or len(name)>256:raise ValueError('Invalid manufacturer name')
        entries[prefix]=name
    text='# IEEE public assignments via oui-data; see NOTICE.txt\n'+''.join(f'{key}\t{value}\n' for key,value in sorted(entries.items()))
    output=io.BytesIO()
    with gzip.GzipFile(fileobj=output,filename='',mode='wb',mtime=0) as zipped:zipped.write(text.encode('utf-8'))
    return output.getvalue(),dict(sorted(collections.Counter(map(len,entries)).items()))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    lock=json.loads(LOCK.read_text());asset=ROOT/lock['asset']
    if args.check and args.source is None:
        assert hashlib.sha256(asset.read_bytes()).hexdigest()==lock['asset_sha256']
        rows=gzip.decompress(asset.read_bytes()).decode('utf-8').splitlines()
        assert len([line for line in rows if line and not line.startswith('#')])==lock['assignments']
        print('NetLab offline registry: pinned asset hash and assignment count PASS');return
    if args.source is not None:raw=args.source.read_bytes()
    else:
        with urllib.request.urlopen(lock['url'],timeout=40) as response:raw=response.read(12*1024*1024)
    if hashlib.sha256(raw).hexdigest()!=lock['source_sha256']:raise ValueError('Source differs from the locked snapshot')
    result,counts=compile_registry(raw)
    if args.check:assert result==asset.read_bytes(),'Bundled data differs from its pinned source'
    else:
        asset.parent.mkdir(parents=True,exist_ok=True);asset.write_bytes(result)
        lock.update(asset_sha256=hashlib.sha256(result).hexdigest(),assignments=sum(counts.values()),prefix_counts=counts)
        LOCK.write_text(json.dumps(lock,indent=2)+'\n')
    print(f'NetLab registry: {sum(counts.values())} assignments; {len(result)} compressed bytes; source reproducibility PASS')

if __name__=='__main__':main()

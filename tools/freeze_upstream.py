#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Pin upstream platform sources without forking them; default is a read-only check."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT/'integration/upstream-platform-lock.json'
INVENTORY = ROOT/'migration/platform-projects.json'
SNIPPET = ROOT/'snippets/pixelos.xml'


def upstreams():
    return [p for p in json.loads(INVENTORY.read_text()) if not p.get('destination_revision')]


def verify():
    projects = ET.parse(SNIPPET).getroot().findall('project')
    actual = {p.get('path'): p for p in projects}
    records = json.loads(LOCK.read_text())['projects']
    expected = {p['path'] for p in upstreams()}
    if len(records) != len(expected) or {p['path'] for p in records} != expected:
        raise ValueError('Upstream lock inventory differs')
    for item in records:
        node = actual[item['path']]
        if node.get('name') != item['repository'] or node.get('remote') != item['remote'] \
                or node.get('revision') != item['revision'] \
                or node.get('upstream') != 'refs/heads/'+item['branch'] \
                or not re.fullmatch('[0-9a-f]{40}', item['revision']):
            raise ValueError('Unpinned upstream project: '+item['path'])
    print(f'{len(records)} upstream platform sources pinned; no additional forks required.')


def resolve(item):
    revision = item.get('source_revision')
    if not revision:
        host = 'gitlab.com' if item['remote'] == 'gitlab' else 'github.com'
        branch = 'refs/heads/'+item['branch']
        result = subprocess.run(['git','ls-remote','https://'+host+'/'+item['name'],branch],
                                text=True,capture_output=True,timeout=90,check=True)
        matches = [line.split()[0] for line in result.stdout.splitlines() if line.split()[-1] == branch]
        if len(matches) != 1: raise ValueError('Missing upstream branch: '+item['name'])
        revision = matches[0]
    if not re.fullmatch('[0-9a-f]{40}', revision): raise ValueError('Invalid source SHA')
    return dict(path=item['path'],repository=item['name'],remote=item['remote'],
                branch=item['branch'],revision=revision)


def freeze():
    entries = upstreams()
    with ThreadPoolExecutor(max_workers=6) as pool:
        records = list(pool.map(resolve, entries))
    # All remote reads must succeed before changing the manifest.
    pins = {p['path']: p for p in records}
    text = SNIPPET.read_text()
    seen = set()
    def update(match):
        tag = match.group(0)
        path = re.search(r'\bpath="([^"]+)"',tag).group(1)
        if path not in pins: return tag
        seen.add(path)
        for key,value in [('revision',pins[path]['revision']),('upstream','refs/heads/'+pins[path]['branch'])]:
            attribute = key+'="'+value+'"'
            if re.search(r'\b'+key+'="[^"]*"',tag): tag=re.sub(r'\b'+key+'="[^"]*"',attribute,tag)
            else: tag=tag[:-2]+' '+attribute+' />' if tag.endswith('/>') else tag[:-1]+' '+attribute+'>'
        return tag
    updated = re.sub(r'<project\b[^>]*>',update,text)
    if seen != set(pins): raise ValueError('Manifest paths differ; no files written')
    data = json.loads(INVENTORY.read_text())
    for item in data:
        if item['path'] in pins: item['source_revision']=pins[item['path']]['revision']
    LOCK.write_text(json.dumps({'checked_on':str(datetime.date.today()),
        'policy':'Pinned upstream sources with owned HeckerOS customization forks; bulk migration is optional.',
        'projects':records},indent=2)+'\n')
    INVENTORY.write_text(json.dumps(data,indent=2)+'\n')
    SNIPPET.write_text(updated)
    verify()


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--freeze',action='store_true',help='Resolve and freeze the remaining upstream sources')
    parser.add_argument('--check',action='store_true',help='Verify an existing lock without networking')
    arguments=parser.parse_args()
    freeze() if arguments.freeze else verify()

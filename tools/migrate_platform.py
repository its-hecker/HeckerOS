#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Provision owned platform repositories, then verify all of them before manifest activation.

Requires authenticated GitHub CLI (`gh auth login`, `gh auth setup-git`), git and
git-lfs for any GitLab payloads using LFS. No tokens are written by this tool.
Default mode prints the plan. --provision creates missing public forks/mirrors;
--activate rewrites local manifests only after every destination is verified.
No existing branch is overwritten, force-pushed, or automatically synced upstream.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def command(*args, cwd=None):
    return subprocess.run([str(a) for a in args], cwd=cwd, check=True,
                          text=True, stdout=subprocess.PIPE).stdout.strip()


class GitHub:
    def api(self, path, method='GET', **fields):
        args = ['gh', 'api', '--method', method, path]
        for key, value in fields.items():
            args += ['-f', key+'='+str(value)]
        return json.loads(command(*args))

    def optional(self, path):
        result = subprocess.run(['gh','api',path], text=True, capture_output=True)
        if result.returncode == 0: return json.loads(result.stdout)
        if 'HTTP 404' in result.stderr: return None
        raise RuntimeError(result.stderr.strip())

    def head(self, repository, branch):
        value = self.optional('repos/'+repository+'/git/ref/heads/'+branch)
        return value['object']['sha'] if value else None

    def contains(self, repository, ancestor, head):
        result = self.api('repos/'+repository+'/compare/'+ancestor+'...'+head)
        return result['merge_base_commit']['sha'] == ancestor


def inventory(path):
    entries = json.loads(path.read_text())
    for item in entries:
        for key in ['name', 'destination']:
            if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', item[key]):
                raise ValueError('Invalid repository name: '+item[key])
        if not item['destination'].startswith('its-hecker/'):
            raise ValueError('Unapproved destination owner')
    if len({x['path'] for x in entries}) != len(entries):
        raise ValueError('Duplicate platform path')
    return entries


def provision(entries, github, cache, lock_path):
    frozen = json.loads(lock_path.read_text()) if lock_path.exists() else {}
    for item in entries:
        dest = item['destination']
        branch = item['destination_branch']
        revision = item.get('source_revision') or frozen.get(dest)
        if revision is None:
            if item['remote'] == 'github':
                revision = github.head(item['name'], item['branch'])
            else:
                line = command('git','ls-remote','--heads','https://gitlab.com/'+item['name']+'.git',
                               'refs/heads/'+item['branch'])
                revision = line.split()[0] if line else None
            if not revision: raise ValueError('Upstream branch missing: '+item['name'])
            frozen[dest] = revision
            lock_path.parent.mkdir(parents=True, exist_ok=True)
            lock_path.write_text(json.dumps(frozen, indent=2)+'\n')
        exists = github.optional('repos/'+dest)
        if not exists:
            if item['remote'] == 'github':
                github.api('repos/'+item['name']+'/forks', 'POST', name=dest.split('/')[1])
                # GitHub's fork endpoint is asynchronous; don't spin or resubmit it.
                for attempt in range(12):
                    if github.optional('repos/'+dest): break
                    time.sleep(5)
                else: raise RuntimeError('Fork still provisioning; rerun later: '+dest)
            else:
                command('gh','repo','create',dest,'--public','--description',
                        'HeckerOS independent platform snapshot; based on '+item['name'])
        if github.head(dest, branch):
            print('Preserving existing owned branch:',dest,branch)
            continue
        if item['remote'] == 'github':
            github.api('repos/'+dest+'/git/refs','POST',ref='refs/heads/'+branch,sha=revision)
        else:
            checkout = cache/dest.split('/')[1]
            if not checkout.exists():
                command('git','clone','--bare','--single-branch','--branch',item['branch'],
                        'https://gitlab.com/'+item['name']+'.git',checkout)
            # Freeze the mirrored branch at the resolved commit, preserving its history.
            command('git','cat-file','-e',revision+'^{commit}',cwd=checkout)
            pointers = command('git','grep','-l','version https://git-lfs.github.com/spec/v1',
                               revision, '--',cwd=checkout) if has_lfs(checkout,revision) else ''
            destination = 'https://github.com/'+dest+'.git'
            if pointers:
                command('git','lfs','version')
                command('git','lfs','fetch','--all','origin',revision,cwd=checkout)
                command('git','lfs','push','--all',destination,revision,cwd=checkout)
            command('git','push',destination,revision+':refs/heads/'+branch,cwd=checkout)
        print('Provisioned:',dest,branch)


def has_lfs(checkout, revision):
    result = subprocess.run(['git','grep','-l','version https://git-lfs.github.com/spec/v1',
                             revision,'--'], cwd=checkout, capture_output=True)
    if result.returncode not in (0,1): raise RuntimeError('Cannot inspect GitLab LFS pointers')
    return result.returncode == 0


def verified_revisions(entries, github, frozen):
    revisions = {}
    failures = []
    for item in entries:
        dest = item['destination']
        head = github.head(dest,item['destination_branch'])
        required = item.get('destination_revision') or item.get('source_revision') or frozen.get(dest)
        if not head:
            failures.append('Missing owned branch: '+dest)
        elif not required:
            failures.append('Missing frozen source revision: '+dest+' (run --provision)')
        elif not github.contains(dest,required,head):
            failures.append('Owned branch does not contain reviewed revision: '+dest)
        else:
            revisions[item['path']] = required
    if failures: raise ValueError('\n'.join(failures))
    return revisions


def rewrite_snippet(text, entries, revisions):
    by_path = {x['path']:x for x in entries}
    def project(match):
        tag = match.group()
        node = ET.fromstring(tag.rstrip('/>')+' />')
        item = by_path.get(node.get('path'))
        if item is None: return tag
        if node.get('name') not in (item['name'],item['destination']):
            raise ValueError('Unfamiliar platform project: '+node.get('path'))
        for key,value in [('name',item['destination']),('remote','heckeros'),
                          ('revision',revisions[item['path']]),
                          ('upstream','refs/heads/'+item['destination_branch'])]:
            if re.search(r'\b'+key+r'="[^"]*"',tag):
                tag = re.sub(r'\b'+key+r'="[^"]*"',key+'="'+value+'"',tag)
            else:
                close = tag.rfind('/>') if tag.endswith('/>') else tag.rfind('>')
                tag = tag[:close]+' '+key+'="'+value+'"'+tag[close:]
        return tag
    rewritten = re.sub(r'<project\b[^>]*>',project,text)
    actual = {x.get('path'):x for x in ET.fromstring(rewritten).findall('project')}
    if not set(by_path) <= set(actual): raise ValueError('Manifest inventory is incomplete')
    return rewritten


def activation_plan(root, entries, revisions, local_manifest=None):
    changes = {}
    old = root/'snippets/pixelos.xml'
    new = root/'snippets/heckeros.xml'
    source = old if old.exists() else new
    changes[new] = rewrite_snippet(source.read_text(),entries,revisions)
    if old.exists(): changes[old] = None
    default = (root/'default.xml').read_text().replace('snippets/pixelos.xml','snippets/heckeros.xml')
    if not any(remote.get('name')=='heckeros' for remote in ET.fromstring(default).findall('remote')):
        default = default.replace('<manifest>','<manifest>\n  <remote name="heckeros" fetch="https://github.com/" revision="refs/heads/heckeros-17" />',1)
    default = default.replace('review="review.pixelos.net"','')
    default = default.replace('after the PixelOS base','after the HeckerOS platform')
    changes[root/'default.xml'] = default
    by_path = {x['path']:x for x in entries}
    folders = [root/'integration']+([local_manifest] if local_manifest else [])
    for folder in folders:
        path = folder/'floral.xml'
        text = path.read_text()
        for item in entries:
            # Existing reviewed extend-project records keep their immutable content revision.
            pattern = r'<extend-project\b[^>]*\bpath="'+re.escape(item['path'])+r'"[^>]*/>'
            def extend(match):
                tag = match.group()
                tag = re.sub(r'\bname="[^"]*"','name="'+item['destination']+'"',tag)
                tag = re.sub(r'\brevision="[^"]*"','revision="'+revisions[item['path']]+'"',tag)
                tag = re.sub(r'\bupstream="[^"]*"','upstream="refs/heads/'+item['destination_branch']+'"',tag)
                return tag
            text = re.sub(pattern,extend,text)
        changes[path] = text
        path = folder/'source-revisions.json'
        lock = json.loads(path.read_text())
        for path_key,item in lock['pixelos_inputs'].items():
            if path_key in by_path:
                item.update(repository=by_path[path_key]['destination'],
                            branch=by_path[path_key]['destination_branch'],revision=revisions[path_key])
        lock['platform_ownership'] = {'status':'owned','projects':len(entries),
            'updates':'Upstream updates require explicit review/merge and a new build.'}
        changes[path] = json.dumps(lock,indent=2)+'\n'
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--provision',action='store_true')
    parser.add_argument('--activate',action='store_true')
    parser.add_argument('--local-manifest',type=Path)
    parser.add_argument('--cache',type=Path,default=ROOT/'out/platform-migration')
    args = parser.parse_args()
    entries = inventory(ROOT/'migration/platform-projects.json')
    if not args.provision and not args.activate:
        for item in entries: print(item['name'],'->',item['destination'],item['destination_branch'])
        print('Plan only. Use --provision, then --activate after all destinations are available.')
        return
    github = GitHub()
    account = github.api('user')['login']
    if account != 'its-hecker': raise ValueError('Authenticate gh as its-hecker before provisioning')
    args.cache.mkdir(parents=True,exist_ok=True)
    lock_path = args.cache/'frozen-upstream.json'
    if args.provision: provision(entries,github,args.cache,lock_path)
    if args.activate:
        frozen = json.loads(lock_path.read_text()) if lock_path.exists() else {}
        revisions = verified_revisions(entries,github,frozen)
        changes = activation_plan(ROOT,entries,revisions,args.local_manifest)
        # Plan and validate every XML/JSON before the first file is written.
        for path,content in changes.items():
            if content is not None:
                if path.suffix=='.xml': ET.fromstring(content)
                if path.suffix=='.json': json.loads(content)
        backup = args.cache/('before-activation-'+time.strftime('%Y%m%d-%H%M%S'))
        for index,(path,content) in enumerate(changes.items()):
            if path.exists():
                backup.mkdir(parents=True,exist_ok=True)
                (backup/(str(index)+'-'+path.name)).write_bytes(path.read_bytes())
            if content is None: path.unlink()
            else:
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(content)
        print('Activated',len(entries),'verified owned projects. Review and commit both manifests.')


if __name__ == '__main__': main()

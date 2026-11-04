#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile HeckerVPN with pinned platform SELinux policy and all neverallows enabled.

This is a standalone platform-policy smoke check, not the complete device/Soong policy.
Release feature flags are enabled for a consistent, fully-declared platform type set.
"""
import argparse, json, os, re, subprocess, tarfile
from pathlib import Path
from compile_sdk import fetch

ROOT=Path(__file__).resolve().parents[1]
POLICY_SHA='99c58a7cc9a44fe33c93808452d018e756d84753'
COMPILER_SHA='a8c5e74f85f869bf236a01ba24181b78f5e39a52'
def run(*args,**kw):subprocess.run(list(map(str,args)),check=True,**kw)
def checkout(path,url,revision):
    if not (path/'.git').exists():
        path.mkdir(parents=True,exist_ok=True)
        run('git','init','-q',path);run('git','-C',path,'remote','add','origin',url)
        run('git','-C',path,'fetch','--depth','1','origin',revision)
        run('git','-C',path,'checkout','--detach','-q','FETCH_HEAD')
    actual=subprocess.check_output(['git','-C',str(path),'rev-parse','HEAD'],text=True).strip()
    if actual!=revision:raise ValueError('Unexpected policy/compiler source revision: '+str(path))
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--cache',type=Path,default=ROOT/'generated/vpn-policy-check')
    p.add_argument('--sepolicy-source',type=Path);p.add_argument('--selinux-source',type=Path);p.add_argument('--tool-dir',type=Path)
    args=p.parse_args();cache=args.cache.resolve();cache.mkdir(parents=True,exist_ok=True)
    tools=(args.tool_dir or cache/'tools').resolve();tools.mkdir(parents=True,exist_ok=True)
    for name,item in json.loads((ROOT/'tests/vpn-policy-tools.lock.json').read_text()).items():fetch(tools,name,item)
    for name in ['m4','bison','flex']:(tools/name).chmod(0o755)
    data=tools/'share/bison';data.mkdir(parents=True,exist_ok=True)
    with tarfile.open(tools/'bison-data.tar.gz','r:gz') as archive:archive.extractall(data,filter='data')
    policy=(args.sepolicy_source or cache/'sepolicy-source').resolve()
    compiler=(args.selinux_source or cache/'selinux-source').resolve()
    checkout(policy,'https://github.com/PixelOS-AOSP/android_system_sepolicy.git',POLICY_SHA)
    checkout(compiler,'https://android.googlesource.com/platform/external/selinux',COMPILER_SHA)
    env=dict(os.environ,M4=str(tools/'m4'),BISON_PKGDATADIR=str(data))
    with (cache/'compiler-build.log').open('w') as log:
        run('make','-C',compiler/'libsepol/src','libsepol.a','DISABLE_CIL=y',env=env,stdout=log,stderr=subprocess.STDOUT)
        run('make','-C',compiler/'checkpolicy','checkpolicy','LEX='+str(tools/'flex'),'YACC='+str(tools/'bison')+' -y',
            'CPPFLAGS=-I../libsepol/include','LIBSEPOLA='+str(compiler/'libsepol/src/libsepol.a'),env=env,stdout=log,stderr=subprocess.STDOUT)
    order=['security_classes','initial_sids','access_vectors','global_macros','neverallow_macros','mls_macros','mls_decl','mls','policy_capabilities',
        'te_macros','ioctl_defines','ioctl_macros','nlmsg_defines','nlmsg_macros','te','roles_decl','roles','users','initial_sid_contexts','fs_use','genfs_contexts','port_contexts']
    paths=[policy/'flagging/flagging_macros']
    for name in order:
        for folder in [policy/'public',policy/'private']:
            candidates=(([folder/'attributes'] if (folder/'attributes').exists() else [])+sorted(folder.glob('*.te'))) if name=='te' else [folder/name]
            paths.extend(path for path in candidates if path.is_file())
        if name=='te':paths.append(ROOT/'rom/sepolicy/private/hecker_vpn.te')
    text='\n'.join(path.read_text()+'\n' for path in paths)
    source=cache/'source.m4';source.write_text(text)
    flags={flag for flag in re.findall(r'is_flag_(?:enabled|disabled)\((\w+)',text) if flag.startswith('RELEASE_')}
    defs={'mls_num_sens':'1','mls_num_cats':'1024','target_arch':'arm64','target_with_asan':'false','target_with_dexpreopt':'true',
        'target_with_native_coverage':'false','target_full_treble':'true','target_compatible_property':'true','target_treble_sysprop_neverallow':'true',
        'target_enforce_sysprop_owner':'true','target_exclude_build_test':'false','target_requires_insecure_execmem_for_swiftshader':'false',
        'target_enforce_debugfs_restriction':'true','target_recovery':'false','target_restricts_ashmem_usage':'false','target_board_api_level':'202604'}
    defs.update({'target_flag_'+flag:'true' for flag in flags})
    for variant in ['user','userdebug']:
        defs['target_build_variant']=variant;conf=cache/(variant+'.conf')
        with conf.open('w') as target:run(tools/'m4','--fatal-warnings',*[f'-D{k}={v}' for k,v in defs.items()],'-s',source,stdout=target)
        run(compiler/'checkpolicy/checkpolicy','-M','-c','30','-o',cache/(variant+'.bin'),conf)
        print('HeckerVPN + pinned platform SELinux policy, '+variant+', neverallows enforced: PASS',flush=True)
    print('Standalone policy checks do not replace a full device policy build or enforcing phone validation.')
if __name__=='__main__':main()

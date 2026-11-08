#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise the native protocol, actual WireGuard tools and new Android controller/UI."""
import argparse, hashlib, json, os, re, shutil, subprocess, tempfile, zipfile
from pathlib import Path
from compile_sdk import fetch
ROOT=Path(__file__).resolve().parents[1]
def run(*command, **kwargs): subprocess.run(list(map(str,command)),check=True,**kwargs)
def host(out):
    run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',out/'classes',*sorted((ROOT/'vpn/common').rglob('*.java')),ROOT/'tests/HeckerVpnConfigTest.java')
    run('java','-cp',out/'classes','HeckerVpnConfigTest')
    run('g++','-std=c++17','-Wall','-Wextra','-Werror','-I',ROOT/'vpn/native',ROOT/'vpn/native/protocol.cpp',ROOT/'tests/vpn_protocol_test.cpp','-o',out/'protocol')
    run(out/'protocol')
    run('g++','-std=c++17','-Wall','-Wextra','-Werror',ROOT/'tests/vpn_lease_test.cpp',ROOT/'vpn/native/protocol.cpp',ROOT/'vpn/native/proxy_config.cpp','-o',out/'lease')
    run(out/'lease')
    run('g++','-std=c++17','-Wall','-Wextra','-Werror',ROOT/'vpn/native/protocol.cpp',ROOT/'vpn/native/daemon.cpp',ROOT/'vpn/native/proxy_config.cpp','-o',out/'daemon')
    run('g++','-std=c++17','-Wall','-Wextra','-Werror',ROOT/'vpn/native/proxy_config.cpp',ROOT/'vpn/native/proxy_core.cpp','-ldl','-pthread','-o',out/'tcp-core')
    source=ROOT/'third_party/wireguard-tools'
    run('gcc','-std=gnu11','-O2','-D_GNU_SOURCE','-DRUNSTATEDIR="/data/misc/heckervpn"','-I',source/'src/uapi/linux',*sorted((source/'src').glob('*.c')),'-o',out/'wg')
    private=subprocess.check_output([out/'wg','genkey']); public=subprocess.check_output([out/'wg','pubkey'],input=private)
    assert len(private.strip())==len(public.strip())==44 and private!=public
    (out/'sys').mkdir();(out/'sys/system_properties.h').write_text('#define PROP_VALUE_MAX 92\nstatic inline int __system_property_get(const char *k,char *v){(void)k;v[0]=0;return 0;}\n')
    run('gcc','-std=gnu11','-Wall','-Wextra','-Werror','-Wno-sign-compare','-I',out,source/'src/wg-quick/android.c','-ldl','-o',out/'quick')
    assert b'IncludedApplications' in subprocess.check_output([out/'quick','--help'])
    reply_test=out/'cleanup_reply.c'
    reply_test.write_text('#define main wireguard_quick_main\n#include "'+str(source/'src/wg-quick/android.c')+'"\n#undef main\n#include <assert.h>\n'
        'int main(void){char reply[256];uint32_t mark=0;'
        'assert(hecker_parse_underlying_mark("101",&mark)&&mark==0xf0065);'
        'assert(hecker_parse_underlying_mark("65535",&mark)&&mark==0xfffff);'
        'assert((mark&0xc0000U)==0xc0000U);'  # Kernel-created UDP carries system permission.
        'assert(!hecker_parse_underlying_mark("99",&mark));assert(!hecker_parse_underlying_mark("65536",&mark));'
        'assert(!hecker_parse_underlying_mark("101;id",&mark));assert(!hecker_parse_underlying_mark("101 102",&mark));'
        'assert(!hecker_parse_underlying_mark("",&mark));assert(!hecker_parse_underlying_mark(NULL,&mark));'
        'assert(hecker_destroy_reply_ok("200 0 success"));'
        'assert(!hecker_destroy_reply_ok(NULL));assert(!hecker_destroy_reply_ok("500 0 destroyNetwork() failed (Permission denied)"));'
        'assert(!hecker_destroy_reply_ok("400 0 syntax error"));'
        'snprintf(reply,sizeof(reply),"400 0 destroyNetwork() failed (%s)",strerror(ENONET));assert(hecker_destroy_reply_ok(reply));'
        'snprintf(reply,sizeof(reply),"400 0 destroyNetwork() failed (%s)",strerror(ENOENT));assert(hecker_destroy_reply_ok(reply));'
        'snprintf(reply,sizeof(reply),"500 0 destroyNetwork() failed (%s)",strerror(ENONET));assert(!hecker_destroy_reply_ok(reply));'
        'assert(!hecker_destroy_reply_ok("400 0 destroyNetwork() failed (Permission denied)"));return 0;}\n')
    run('gcc','-std=gnu11','-Wall','-Wextra','-Werror','-Wno-sign-compare','-I',out,reply_test,'-ldl','-o',out/'cleanup-reply')
    run(out/'cleanup-reply')
    lock=json.loads((source/'UPSTREAM.json').read_text())
    for path,digest in lock['files'].items():
        if path=='src/wg-quick/android.c':continue
        assert hashlib.sha256((source/path).read_bytes()).hexdigest()==digest,path
    assert 'execvp("su"' not in (source/'src/wg-quick/android.c').read_text()
    print('WireGuard native key generation, tool compilation, vendored hashes and daemon compilation: PASS')
def android(out,sdk):
    lock=json.loads((ROOT/'tests/sdk-inputs.lock.json').read_text());sdk.mkdir(parents=True,exist_ok=True)
    for name in ['android.jar','android-system.jar','aidl','lib64/libc++.so','aapt2-linux.jar','lottie.aar','appcompat.aar','core.aar']:fetch(sdk,name,lock[name])
    for jar,entry,target in [('aapt2-linux.jar','aapt2','aapt2'),('lottie.aar','classes.jar','lottie.jar'),('appcompat.aar','classes.jar','appcompat.jar'),('core.aar','classes.jar','core.jar')]:
        with zipfile.ZipFile(sdk/jar) as archive:(sdk/target).write_bytes(archive.read(entry))
    (sdk/'aapt2').chmod(0o755);(sdk/'aidl').chmod(0o755)
    env=dict(os.environ,LD_LIBRARY_PATH=str(sdk/'lib64'))
    parcel=out/'aidl/android/os/Bundle.aidl';parcel.parent.mkdir(parents=True);parcel.write_text('package android.os; parcelable Bundle;')
    aidl=ROOT/'vpn/aidl/org/hecker/vpn/IHeckerVpnService.aidl'
    run(sdk/'aidl','--lang=java','--out='+str(out/'generated-aidl'),'-I',out/'aidl','-I',ROOT/'vpn/aidl',aidl,env=env)
    common=[*sorted((ROOT/'vpn/common').rglob('*.java')),*sorted((out/'generated-aidl').rglob('*.java'))]
    for name in ['vpn','app']:
        run(sdk/'aapt2','compile','--dir',ROOT/name/'res','-o',out/(name+'-resources.zip'))
        run(sdk/'aapt2','link','-I',sdk/'android.jar','--manifest',ROOT/name/'AndroidManifest.xml','--java',out/(name+'-r'),'-o',out/(name+'.apk'),out/(name+'-resources.zip'))
    from prepare_proxy_assets import prepare as prepare_proxy_assets
    prepare_proxy_assets()
    cp=os.pathsep.join(map(str,[sdk/'android-system.jar',sdk/'lottie.jar',sdk/'appcompat.jar',sdk/'core.jar',ROOT/'prebuilts/mihomo/snakeyaml.jar']))
    run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',out/'backend',out/'vpn-r/org/hecker/vpn/R.java',*common,*sorted((ROOT/'vpn/src').rglob('*.java')))
    ui=ROOT/'app/src/org/hecker/suite'
    run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',out/'ui',out/'app-r/org/hecker/suite/R.java',ROOT/'app/src/org/hecker/ui/HeckerChoiceSheet.java',*common,
        *[ui/(name+'.java') for name in ['Ui','SuiteAnimation','HeckerVpnClient','HeckerVpnActivity','HeckerVpnTileService']])
    print('HeckerVPN controller, real AIDL, HeckerLab page, QS tile and Android resources: PASS')
    json_lock=json.loads((ROOT/'tests/xmorph-inputs.lock.json').read_text())
    json_jar=fetch(sdk,'xmorph-json-20240303.jar',json_lock['xmorph-json-20240303.jar'])
    run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',json_jar,'-d',out/'provider-test',
        ROOT/'vpn/common/org/hecker/vpn/VpnConfig.java',ROOT/'vpn/src/org/hecker/vpn/MullvadProvider.java',ROOT/'tests/HeckerVpnProviderTest.java')
    run('java','-cp',str(out/'provider-test')+os.pathsep+str(json_jar),'org.hecker.vpn.HeckerVpnProviderTest')
    platform=ROOT/'generated/vpn-platform';platform.mkdir(parents=True,exist_ok=True)
    for name,item in json.loads((ROOT/'tests/vpn-platform-inputs.lock.json').read_text()).items():fetch(platform,name,item)
    assert 'CONFIG_WIREGUARD=y' in (platform/'kernel-floral-defconfig').read_text()
    assert 'source "net/wireguard/Kconfig"' in (platform/'kernel-net-Kconfig').read_text()
    assert 'obj-$(CONFIG_WIREGUARD) += wireguard/' in (platform/'kernel-net-Makefile').read_text()
    fwmark=(platform/'Fwmark.h').read_text()
    assert re.search(r'unsigned netId\s*: 16;\s*bool explicitlySelected\s*:  1;\s*bool protectedFromVpn\s*:  1;',fwmark)
    ndc=(platform/'NdcDispatcher.cpp').read_text()
    for marker in ['network create <netId> vpn <secure>','networkCreateVpn(netId, secure)','network users  add','networkDestroy(netId)']:assert marker in ndc,marker
    dns=(platform/'IDnsResolver.aidl').read_text();dns=re.sub(r'/\*.*?\*/|//[^\n]*','',dns,flags=re.S)
    methods=re.findall(r'\b(?:void|boolean|int|String)\s+(\w+)\s*\(',dns)
    assert methods.index('setResolverConfiguration')==2 and methods.index('createNetworkCache')==7 and methods.index('destroyNetworkCache')==8,methods
    assert 'android_get_control_socket' in (platform/'sockets.h').read_text()
    policy=(ROOT/'rom/sepolicy/private/hecker_vpn.te').read_text()
    assert 'domain_auto_trans(hecker_vpnd, hecker_vpn_tool_exec, netutils_wrapper)' in policy
    assert 'allow hecker_vpnd netd_service' not in policy and 'binder_call(hecker_vpnd, netd)' not in policy
    print('Pinned Floral kernel enablement, Android 17 ndc syntax, DNS Binder transactions, cutils declaration and netd policy boundary: PASS')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk-cache',type=Path);args=p.parse_args()
    with tempfile.TemporaryDirectory(prefix='hecker-vpn-') as tmp:
        out=Path(tmp);host(out)
        if args.sdk_cache:android(out,args.sdk_cache.resolve())
    print('Full ROM build, SELinux enforcement, account provisioning and phone traffic tests remain required.')
if __name__=='__main__':main()

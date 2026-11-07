#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile/test real root TCP parsers; verify the pinned native ABI and Android-free boundary."""
import argparse
import hashlib
import gzip
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from compile_sdk import fetch
from prepare_proxy_assets import prepare

ROOT = Path(__file__).resolve().parents[1]
def run(*args): subprocess.run(list(map(str, args)), check=True)
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-cache', type=Path, default=ROOT/'generated/sdk')
    parser.add_argument('--catalog', type=Path)
    parser.add_argument('--core-config', action='store_true', help='Validate owned config with pinned upstream Mihomo 1.19.30 Linux parser')
    args = parser.parse_args(); prepare(offline=True)
    lock = json.loads((ROOT/'prebuilts/mihomo/assets.lock.json').read_text())
    core = ROOT/'prebuilts/mihomo/arm64/libclash.so'
    symbols = subprocess.check_output(['readelf', '-Ws', core], text=True)
    for name in ['invokeAction', 'startTUN', 'stopTun', 'release_object_func', 'free_string_func', 'protect_func', 'resolve_process_func', 'result_func']:
        assert any(line.split()[-1] == name for line in symbols.splitlines() if line.split()), name
    assert hashlib.sha256(core.read_bytes()).hexdigest() == lock['core']['sha256']
    manifest = ET.parse(ROOT/'vpn/AndroidManifest.xml').getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    assert all(n.get(ns+'name') != 'android.permission.BIND_VPN_SERVICE' for n in manifest.iter('uses-permission'))
    assert not any(n.get(ns+'name') == 'android.net.VpnService' for n in manifest.iter('action'))
    assert not any('android.net.VpnService' in p.read_text() for p in (ROOT/'vpn/src').rglob('*.java'))
    json_lock = json.loads((ROOT/'tests/xmorph-inputs.lock.json').read_text())
    jar = fetch(args.sdk_cache, 'xmorph-json-20240303.jar', json_lock['xmorph-json-20240303.jar'])
    with tempfile.TemporaryDirectory(prefix='hecker-tcp-check-') as temporary:
        out = Path(temporary)
        run('g++','-std=c++17','-Wall','-Wextra','-Werror','-I',ROOT/'vpn/native',ROOT/'vpn/native/proxy_config.cpp',ROOT/'tests/vpn_proxy_test.cpp','-o',out/'proxy-test')
        run(out/'proxy-test', out/'config.yaml')
        run('g++','-std=c++17','-Wall','-Wextra','-Werror','-fPIC','-shared',ROOT/'tests/proxy_fake_core.cpp','-pthread','-o',out/'libfixture.so')
        run('g++','-std=c++17','-Wall','-Wextra','-Werror',ROOT/'tests/proxy_bridge_test.cpp',ROOT/'vpn/native/proxy_config.cpp','-ldl','-pthread','-o',out/'bridge-test')
        run(out/'bridge-test', out/'libfixture.so')
        if args.core_config:
            checks = json.loads((ROOT/'tests/proxy-tools.lock.json').read_text())
            item = next(iter(checks)); archive = fetch(ROOT/'generated/proxy-check', item, checks[item])
            binary = out/'mihomo'; binary.write_bytes(gzip.decompress(archive.read_bytes())); binary.chmod(0o755)
            run(binary, '-t', '-d', out, '-f', out/'config.yaml')
        cp = os.pathsep.join(map(str,[jar,ROOT/'prebuilts/mihomo/snakeyaml.jar',args.sdk_cache/'android-system.jar']))
        run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',out/'classes',ROOT/'vpn/common/org/hecker/vpn/VpnConfig.java',
            ROOT/'vpn/src/org/hecker/vpn/ProxyConfig.java',ROOT/'vpn/src/org/hecker/vpn/TcpCatalog.java',ROOT/'tests/HeckerProxyTest.java')
        run('java','-cp',str(out/'classes')+os.pathsep+cp,'org.hecker.vpn.HeckerProxyTest',*([args.catalog] if args.catalog else []))
    print('Pinned arm64 native ABI, parser regressions and no Android VPN service: PASS')
    print('Full ROM, enforcing-device root TUN and ONIC traffic validation remain required.')
if __name__ == '__main__': main()

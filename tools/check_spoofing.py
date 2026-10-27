#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Type-check the Evolution X port against Android 17 and test private configuration/crypto.

This is a javac/host check, not a Soong build or an attestation verdict.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from check_platform_features import source
from compile_sdk import fetch

ROOT = Path(__file__).resolve().parents[1]


def run(java, *args):
    subprocess.run([str(java), *map(str, args)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--framework-source', type=Path)
    parser.add_argument('--cache', type=Path, default=ROOT/'out/spoof-deps')
    parser.add_argument('--java', default=os.environ.get('HECKER_JAVA', 'java'))
    args = parser.parse_args()
    project = json.loads((ROOT/'integration/platform-features.json').read_text())['framework']
    framework = args.framework_source or source(project, ROOT/'out/platform-feature-sources')
    args.cache.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT/'tests/spoof-dependencies.lock.json').read_text())
    jar = fetch(args.cache, next(iter(lock)), next(iter(lock.values())))
    with tempfile.TemporaryDirectory(prefix='hecker-spoof-check-') as temporary:
        out = Path(temporary)
        java = sorted((framework/'core/java/android/security').rglob('*.java'))
        java = [p for p in java if str(p.relative_to(framework)) in project['files']]
        keystore = [framework/p for p in project['files'] if p.startswith('keystore/') and p.endswith('.java')]
        store = framework/'services/core/java/com/android/server/spoof/HeckerSpoofConfigStore.java'
        # These stubs cover the six added private Binder methods, generated aconfig
        # Flags and the pre-existing PixelOS keybox hooks absent from AOSP's jar.
        stubs = sorted((ROOT/'tests/spoofing/type-stubs').rglob('*.java'))
        run(args.java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', jar,
            '-d', out/'types', *java, *keystore, store,
            framework/'core/java/com/android/internal/util/PropImitationHooks.java', *stubs)

        aidl = (framework/'core/java/android/app/IActivityManager.aidl').read_text()
        ams = (framework/'services/core/java/com/android/server/am/ActivityManagerService.java').read_text()
        for name in ['getSpoofGamePropsConfig','getSpoofTrickyStoreTarget','getSpoofTrickyStoreKeyBox',
                     'getSpoofTrickyStorePatch','getHeckerSpoofConfig','setHeckerSpoofConfig']:
            assert aidl.count(name+'(') == 1 and ams.count(name+'(') == 1, name
        props = (framework/'core/java/com/android/internal/util/PropImitationHooks.java').read_text()
        assert 'if (sIsGms || sIsFinsky) return;' in props
        assert 'sPixelOneProps.forEach((PropImitationHooks::setPropValue))' in props
        assert 'setPlayIntegrityProps(' not in props and 'onEngineGetCertificateChain(' not in props
        for file in ['keystore/java/android/security/KeyStore2.java',
                     'keystore/java/android/security/KeyStoreSecurityLevel.java']:
            assert 'KeyboxImitationHooks' not in (framework/file).read_text()
        assert 'PlayIntegritySpoofService.shouldBlockCertificateChain()' in (
            framework/'keystore/java/android/security/keystore2/AndroidKeyStoreSpi.java').read_text()
        thread = (framework/'core/java/android/app/ActivityThread.java').read_text()
        assert 'GamePropsSpoofService.getInstance()' in thread
        assert thread.index('GamePropsSpoofService.getInstance()') < thread.index('app = data.info.makeApplicationInner')
        generator = (framework/'keystore/java/android/security/keystore2/AndroidKeyStoreKeyPairGeneratorSpi.java').read_text()
        assert '!mSpec.isUserAuthenticationRequired()' in generator
        assert '!isCurve25519(mEcCurveName)' in generator
        assert 'metadata.certificate = userCert;' in generator
        assert 'mSpec.getAttestationIds().length == 0' in generator
        assert 'mSpec.getAttestKeyAlias() == null' in generator
        assert 'mKeymasterAlgorithm == KeymasterDefs.KM_ALGORITHM_RSA' in generator

        # Run the production parsers, policy, keybox importer and certificate code
        # with Android 17's own JSON and repackaged Bouncy Castle implementations.
        # OS/Binder/filesystem boundaries are replaced by small host fakes.
        runtime = sorted((ROOT/'tests/spoofing/runtime-stubs').rglob('*.java'))
        logic = [p for p in java if '/security/pif/' not in str(p)]
        run(args.java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-cp', jar,
            '-d', out/'runtime', *logic, store, *runtime, framework/'tests/hecker/SpoofingConfigTest.java')
        run(args.java, '-cp', str(out/'runtime')+os.pathsep+str(jar),
            'com.android.server.spoof.SpoofingConfigTest', out/'data-system')
    print('Android 17 keystore/crypto type checks and scoped configuration regressions: PASS')
    print('Full Android/Soong build and on-device attestation/enrollment testing remain required.')


if __name__ == '__main__': main()

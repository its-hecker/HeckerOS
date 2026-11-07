#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Run production Vault archive/transport tests, Android SDK compilation and exact platform wiring audits."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from compile_sdk import fetch
from privapp_permissions import check_package_permissions
from vault_storage_port import vault_storage_plan, MEDIA
from prepare import replace_guarded

ROOT=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'

def run(*args):subprocess.run([str(a) for a in args],check=True)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--sdk-cache',type=Path,default=ROOT/'generated/sdk');args=p.parse_args()
    cache=args.sdk_cache.resolve();cache.mkdir(parents=True,exist_ok=True)
    lock=json.loads((ROOT/'tests/sdk-inputs.lock.json').read_text())
    for name in ['android.jar','android-system.jar','aapt2-linux.jar','lottie.aar','appcompat.aar','core.aar']:fetch(cache,name,lock[name])
    for name in ['lottie','appcompat','core']:
        with zipfile.ZipFile(cache/(name+'.aar')) as z:(cache/(name+'.jar')).write_bytes(z.read('classes.jar'))
    with zipfile.ZipFile(cache/'aapt2-linux.jar') as z:
        (cache/'aapt2').write_bytes(z.read('aapt2'));(cache/'aapt2').chmod(0o755)
    api=ROOT/'out/vault-api';api.mkdir(parents=True,exist_ok=True)
    for name,item in json.loads((ROOT/'tests/vault-platform-inputs.lock.json').read_text()).items():fetch(api,name,item)
    app=ET.parse(ROOT/'vault/AndroidManifest.xml').getroot()
    requested={p.get(A+'name') for p in app.findall('uses-permission')}
    check_package_permissions(api/'AndroidManifest.xml',app.get('package'),requested,ROOT/'configs/privapp-permissions-hecker-vault.xml','HeckerVault')
    assert 'android.permission.INTERNET' not in requested and 'android.permission.MANAGE_EXTERNAL_STORAGE' not in requested
    assert 'android.permission.ACCESS_MTP' in requested
    application=app.find('application');assert application.get(A+'allowBackup')=='false' and not app.get(A+'sharedUserId')
    activity=application.find('activity');assert activity.get(A+'permission')=='org.hecker.vault.permission.OPEN'
    transport=application.find("service[@android:name='.VaultTransportService']",{'android':A[1:-1]})
    assert transport.get(A+'permission')=='android.permission.BACKUP' and transport.get(A+'exported')=='true'
    for service in application.findall('service'):
        if service.get(A+'exported')=='true':assert service.get(A+'permission') in {'android.permission.BACKUP','android.permission.BIND_JOB_SERVICE'}
    assert application.find("service[@android:name='.VaultService']",{'android':A[1:-1]}).get(A+'exported')=='false'
    config=ET.parse(ROOT/'configs/hecker-vault-transport.xml').getroot()
    assert config.find('backup-transport-whitelisted-service').get('service')=='org.hecker.vault/.VaultTransportService'
    native=(api/'SystemConfig.java').read_text();assert 'case "backup-transport-whitelisted-service"' in native and 'getAttributeValue(null, "service")' in native
    native=(api/'TransportManager.java').read_text();assert 'mTransportWhitelist.contains(transport)' in native and 'PRIVATE_FLAG_PRIVILEGED' in native
    blueprint=(ROOT/'Android.bp').read_text();module=(ROOT/'vault/Android.bp').read_text()
    for required in ['platform_apis: true','certificate: "platform"','privileged: true','system_ext_specific: true']:assert required in module
    for name,sub in [('HeckerVaultPermissions','permissions'),('HeckerVaultDefaultPermissions','default-permissions'),('HeckerVaultTransportConfig','sysconfig')]:
        matches=[b for b in re.findall(r'prebuilt_etc\s*\{([^{}]*)\}',blueprint) if f'name: "{name}"' in b]
        assert len(matches)==1 and f'sub_dir: "{sub}"' in matches[0] and 'system_ext_specific: true' in matches[0]
        assert f'"{name}"' in module
    assert 'HeckerVault' in (ROOT/'suite.mk').read_text()
    assert 'org.hecker.vault.permission.OPEN' in (ROOT/'app/AndroidManifest.xml').read_text()
    # Verify the omitted metadata behavior against the exact pinned platform, and
    # apply/revert the FUSE adapter against actual upstream source, not only fixtures.
    native=(api/'KeyValueBackupTask.java').read_text()
    assert 'boolean backupPm = mQueue.remove(PM_PACKAGE) || !mNonIncremental;' in native
    native=(api/'UserBackupManagerService.java').read_text()
    assert 'if (PACKAGE_MANAGER_SENTINEL.equals(packageName))' in native
    assert 'kvBackupList.add(packageName);' in native
    with tempfile.TemporaryDirectory(prefix='vault-provider-port-') as tmp:
        rom=Path(tmp);provider=rom/MEDIA/'MediaProvider.java';provider.parent.mkdir(parents=True)
        original=(api/'MediaProvider.java').read_text();provider.write_text(original)
        for path,value in vault_storage_plan(rom,replace_guarded).items():path.write_text(value)
        assert not vault_storage_plan(rom,replace_guarded)
        for path,value in vault_storage_plan(rom,replace_guarded,True).items():
            if value is None:path.unlink()
            else:path.write_text(value)
        assert provider.read_text()==original
    with tempfile.TemporaryDirectory(prefix='heckervault-check-') as tmp:
        out=Path(tmp)
        javac=['java','-m','jdk.compiler/com.sun.tools.javac.Main']
        core=sorted((ROOT/'vault/core').rglob('*.java'))
        run(*javac,'-d',out/'host',*core,ROOT/'vault/tests/VaultArchiveTest.java',
            ROOT/'vault/tests/VaultSelectionTest.java',ROOT/'vault/tests/VaultExternalTest.java',ROOT/'vault/tests/VaultWifiTest.java',ROOT/'vault/tests/VaultDataOutcomeTest.java')
        run('java','-cp',out/'host','org.hecker.vault.VaultArchiveTest')
        run('java','-cp',out/'host','org.hecker.vault.VaultSelectionTest')
        run('java','-cp',out/'host','org.hecker.vault.VaultExternalTest')
        run('java','-cp',out/'host','org.hecker.vault.VaultWifiTest')
        run('java','-cp',out/'host','org.hecker.vault.VaultDataOutcomeTest')
        run(*javac,'-d',out/'transport',*sorted((ROOT/'vault/tests/host').rglob('*.java')),*core,
            ROOT/'vault/src/org/hecker/vault/VaultSession.java',ROOT/'vault/src/org/hecker/vault/VaultTransport.java',ROOT/'vault/tests/VaultTransportTest.java')
        run('java','-cp',out/'transport','org.hecker.vault.VaultTransportTest')
        run(cache/'aapt2','compile','--dir',ROOT/'vault/res','-o',out/'resources.zip')
        run(cache/'aapt2','link','-I',cache/'android.jar','--manifest',ROOT/'vault/AndroidManifest.xml','--java',out/'generated','-o',out/'vault.apk',out/'resources.zip')
        cp=os.pathsep.join(str(cache/name) for name in ['android-system.jar','lottie.jar','appcompat.jar','core.jar'])
        run(*javac,'-cp',cp,'-d',out/'classes',out/'generated/org/hecker/vault/R.java',
            ROOT/'app/src/org/hecker/ui/HeckerChoiceSheet.java',ROOT/'rom/media/HeckerVaultMediaAccess.java',*core,*sorted((ROOT/'vault/src').rglob('*.java')))
    print('HeckerVault complete controller Java/resources + exact Android transport/permission wiring: PASS')
    print('Full ROM compilation and physical backup/wipe/restore acceptance remain required. Swift import is deferred.')

if __name__=='__main__':main()

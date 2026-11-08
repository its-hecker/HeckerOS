#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check guarded real-platform integration and exercise the private-file service/native transaction.

Host Android boundaries are simulated; this does not replace a Soong build or phone restore.
"""
import argparse, json, subprocess, tempfile
from pathlib import Path
from compile_sdk import fetch
from prepare import replace_guarded
from vault_files_port import vault_files_plan, COPIES
from vault_files_host import SOURCES

ROOT = Path(__file__).resolve().parents[1]
def run(*args): subprocess.run(list(map(str, args)), check=True)
def function(source, marker):
    start=source.index(marker)
    return source[start:source.index('\n}\n',start)+3]
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache',type=Path,default=ROOT/'out/vault-files-platform')
    args=parser.parse_args();cache=args.cache.resolve();cache.mkdir(parents=True,exist_ok=True)
    for name,item in json.loads((ROOT/'tests/vault-files-platform-inputs.lock.json').read_text()).items():fetch(cache,name,item)
    installer=(cache/'Installer.java').read_text();freezer=(cache/'PackageFreezer.java').read_text()
    aidl=(cache/'IInstalld.aidl').read_text();pms=(cache/'PackageManagerService.java').read_text()
    assert 'public void copyAppDataPath(String uuid, String fromPath, String toPath, int userId, int appId,' in installer
    assert 'IInstalld.IAppDataOperationCallback callback' in installer
    assert 'PackageFreezer(String packageName, @CanBeALL @UserIdInt int userId, String killReason,' in freezer
    assert 'boolean waitAppKilled, boolean waitAppStopped)' in freezer
    assert 'final Installer mInstaller;' in pms and 'final PackageManagerTracedLock mInstallLock' in pms
    assert 'public PackageManagerTracedLock acquireLock()' in (cache/'PackageManagerTracedLock.java').read_text()
    assert 'oneway interface IAppDataOperationCallback' in aidl and 'oneway void copyAppDataPath(' in aidl
    for name,value in [('RUNNING',1),('SUCCESS',2),('FAILURE',3)]:assert f'STATUS_{name} = {value};' in aidl
    original=(cache/'InstalldNativeService.cpp').read_text()
    boundary=function(original,'binder::Status InstalldNativeService::copyAppDataPath(')
    assert 'ENFORCE_UID(AID_SYSTEM);' in boundary and 'CHECK_ARGUMENT_PATH(fromPath);' in boundary and 'CHECK_ARGUMENT_PATH(toPath);' in boundary
    with tempfile.TemporaryDirectory(prefix='vault-files-platform-') as tmp:
        out=Path(tmp);rom=out/'rom'
        native=rom/'frameworks/native/cmds/installd/InstalldNativeService.cpp'
        service=rom/'frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java'
        for file,source in [(native,original),(service,pms)]:file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source)
        changes=vault_files_plan(rom,replace_guarded)
        for file,content in changes.items():file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content)
        assert not vault_files_plan(rom,replace_guarded),'adapter must be idempotent'
        patched=native.read_text()
        extracted='\n'.join(function(patched,marker) for marker in [
            'static bool hecker_vault_transient(', 'static bool verify_app_data_recursive(', 'static void copy_app_data_recursive('])
        (out/'vault_installd_copy.inc').write_text(extracted)
        run('g++','-std=c++17','-Wall','-Wextra','-Werror','-Wno-unused-function','-fno-exceptions',
            '-I',out,'-I',ROOT/'rom/vault',ROOT/'tests/vault_files_native_test.cpp','-o',out/'native')
        run(out/'native')
        for file,content in vault_files_plan(rom,replace_guarded,True).items():
            if content is None:file.unlink()
            else:file.write_text(content)
        assert native.read_text()==original and service.read_text()==pms
        assert not any((rom/path).exists() for path in COPIES.values())
        # Guard failures must leave platform source untouched.
        native.write_text(original.replace('static void copy_app_data(', 'static void changed_upstream_copy_app_data(',1))
        try:vault_files_plan(rom,replace_guarded);raise AssertionError('unfamiliar source accepted')
        except ValueError:pass
        host=out/'host';host.mkdir()
        for path,content in SOURCES.items():
            file=host/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(content)
        run('java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',out/'classes',*sorted(host.rglob('*.java')),
            ROOT/'rom/vault/HeckerVaultFilesService.java',ROOT/'vault/core/org/hecker/vault/VaultFilesProtocol.java',ROOT/'tests/HeckerVaultFilesServiceTest.java')
        run('java','-cp',out/'classes','com.android.server.pm.HeckerVaultFilesServiceTest')
    print('Vault private-file adapter: pinned Android APIs, apply/idempotence/revert/guard refusal: PASS')
    print('Full platform compilation and physical backup/restore acceptance remain required.')
if __name__=='__main__':main()

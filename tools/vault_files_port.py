# SPDX-License-Identifier: Apache-2.0
"""Guarded private-file broker integration using Android 17's ownership-checked installd API."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
COPIES={
 'rom/vault/HeckerVaultFilesService.java':'frameworks/base/services/core/java/com/android/server/pm/HeckerVaultFilesService.java',
 'vault/core/org/hecker/vault/VaultFilesProtocol.java':'frameworks/base/services/core/java/org/hecker/vault/VaultFilesProtocol.java',
 'rom/vault/HeckerVaultFiles.inc':'frameworks/native/cmds/installd/HeckerVaultFiles.inc',
}
def vault_files_plan(rom,replace,revert=False,pending=None):
 if not (rom/'frameworks/native').exists():return {} # Small pre-existing integration fixtures.
 changes={};pending=pending or {}
 for item in json.loads((ROOT/'framework/vault-files-patches.json').read_text()):
  file=rom/item['path']
  if not file.is_file():raise ValueError('Missing private-file platform input: '+str(file))
  current=pending.get(file,file.read_text());target=replace(current,item['old'],item['new'],item['path'],revert)
  if target!=current:changes[file]=target;pending={**pending,file:target}
 for source,destination in COPIES.items():
  file=rom/destination;expected=(ROOT/source).read_text()
  if file.exists() and file.read_text()!=expected:raise ValueError('Preserving unfamiliar private-file helper: '+str(file))
  if revert:
   if file.exists():changes[file]=None
  elif not file.exists():changes[file]=expected
 return changes

# SPDX-License-Identifier: Apache-2.0
"""Guarded owner-media access for the platform-signed local Vault, through MediaProvider FUSE."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MEDIA=Path('packages/providers/MediaProvider/src/com/android/providers/media')
ANCHOR='''    private boolean shouldBypassFuseRestrictions(boolean forWrite, String filePath) {
'''
PATCH=ANCHOR+'''        // HeckerVault: opt-in package-media snapshots through the existing FUSE path.
        if (HeckerVaultMediaAccess.allowed(getContext(), mCallingIdentity.get().uid,
                getCallingPackage(), filePath)) {
            return true;
        }

'''

def vault_storage_plan(rom,replace,revert=False):
    provider=rom/MEDIA/'MediaProvider.java'
    if not provider.is_file():return {}
    changes={};current=provider.read_text();target=replace(current,ANCHOR,PATCH,str(provider),revert)
    if target!=current:changes[provider]=target
    helper=rom/MEDIA/'HeckerVaultMediaAccess.java';expected=(ROOT/'rom/media/HeckerVaultMediaAccess.java').read_text()
    if helper.exists() and helper.read_text()!=expected:raise ValueError('Preserving unfamiliar Vault media adapter: '+str(helper))
    if revert:
        if helper.exists():changes[helper]=None
    elif not helper.exists():changes[helper]=expected
    return changes

# SPDX-License-Identifier: Apache-2.0
"""Guarded package-folder access for the platform-signed local Vault, through MediaProvider FUSE."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
MEDIA=Path('packages/providers/MediaProvider/src/com/android/providers/media')
ANCHOR='''    private boolean shouldBypassFuseRestrictions(boolean forWrite, String filePath) {
'''
PATCH=ANCHOR+'''        // HeckerVault: opt-in package-folder snapshots through the existing FUSE path.
        // FUSE requests carry LocalCallingIdentity, not ContentProvider Binder attribution.
        if (HeckerVaultMediaAccess.allowed(getContext(), mCallingIdentity.get().uid,
                mCallingIdentity.get().getPackageName(), filePath)) {
            return true;
        }

'''
PRIVATE_ANCHOR='''        try {
            return isCallingIdentityAllowedAccessToDataOrObbPath(
                    extractRelativePathWithDisplayName(path));
'''
PRIVATE_PATCH='''        try {
            // FUSE lookup, readdir and directory operations check private paths before
            // shouldBypassFuseRestrictions. Grant the same narrowly scoped access here.
            if (HeckerVaultMediaAccess.allowed(getContext(), uid,
                    mCallingIdentity.get().getPackageName(), path)) {
                return true;
            }
            return isCallingIdentityAllowedAccessToDataOrObbPath(
                    extractRelativePathWithDisplayName(path));
'''

def vault_storage_plan(rom,replace,revert=False):
    provider=rom/MEDIA/'MediaProvider.java'
    if not provider.is_file():return {}
    changes={};current=provider.read_text();target=replace(current,ANCHOR,PATCH,str(provider),revert)
    target=replace(target,PRIVATE_ANCHOR,PRIVATE_PATCH,str(provider),revert)
    if target!=current:changes[provider]=target
    helper=rom/MEDIA/'HeckerVaultMediaAccess.java';expected=(ROOT/'rom/media/HeckerVaultMediaAccess.java').read_text()
    if helper.exists() and helper.read_text()!=expected:raise ValueError('Preserving unfamiliar Vault media adapter: '+str(helper))
    if revert:
        if helper.exists():changes[helper]=None
    elif not helper.exists():changes[helper]=expected
    return changes

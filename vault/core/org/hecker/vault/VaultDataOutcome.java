// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

/** Android can report success for an agent that exports zero bytes; that is not saved app data. */
enum VaultDataOutcome {
    SAVED, EMPTY, DISALLOWED, FAILED, METADATA_MISSING;

    static VaultDataOutcome classify(boolean allowsData,boolean passComplete,boolean metadata,
            int packageResult,boolean hasPayload) {
        if(!allowsData)return DISALLOWED;
        if(!passComplete||packageResult!=0)return FAILED;
        if(!metadata)return METADATA_MISSING;
        return hasPayload?SAVED:EMPTY;
    }
}

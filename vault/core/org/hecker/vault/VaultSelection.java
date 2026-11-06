// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

/** Selection rules shared by quick actions and the verified restore preview. */
public final class VaultSelection {
    private VaultSelection(){}
    public static boolean backup(String scope,long installed,Long saved){
        switch(scope){
            case "pending":return saved==null;
            case "updated":return saved!=null&&installed>saved;
            case "existing":return saved!=null;
            default:return true;
        }
    }
    public static boolean restore(String scope,long saved,Long installed){
        switch(scope){
            case "missing":return installed==null;
            case "newer":return installed==null||saved>installed;
            default:return true;
        }
    }
}

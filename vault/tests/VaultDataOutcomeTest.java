// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

public final class VaultDataOutcomeTest {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError();}
    public static void main(String[] args){
        // Plus Messenger in the device log: @pm@ saved, package result SUCCESS,
        // backup_package reports 0 bytes and there is no transport payload.
        check(VaultDataOutcome.classify(true,true,true,0,false)==VaultDataOutcome.EMPTY);
        check(VaultDataOutcome.classify(true,true,true,0,true)==VaultDataOutcome.SAVED);
        check(VaultDataOutcome.classify(true,true,false,0,true)==VaultDataOutcome.METADATA_MISSING);
        check(VaultDataOutcome.classify(true,false,true,0,true)==VaultDataOutcome.FAILED);
        check(VaultDataOutcome.classify(true,true,true,-1003,false)==VaultDataOutcome.FAILED);
        check(VaultDataOutcome.classify(true,true,true,-1003,true)==VaultDataOutcome.FAILED);
        check(VaultDataOutcome.classify(false,true,true,0,true)==VaultDataOutcome.DISALLOWED);
        for(int code:new int[]{-1000,-1002,-1003,-1005,-2001})
            check(VaultDataOutcome.classify(true,true,true,code,true)!=VaultDataOutcome.SAVED);
        System.out.println("Vault app-data outcomes: "+checks+" checks PASS (empty-agent success is not saved data)");
    }
}

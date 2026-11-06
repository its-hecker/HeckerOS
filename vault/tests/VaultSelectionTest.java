// SPDX-License-Identifier: Apache-2.0
package org.hecker.vault;

public final class VaultSelectionTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(VaultSelection.backup("pending",10,null));check(!VaultSelection.backup("pending",10,10L));
        check(VaultSelection.backup("updated",11,10L));check(!VaultSelection.backup("updated",10,10L));
        check(!VaultSelection.backup("updated",9,10L));check(!VaultSelection.backup("updated",11,null));
        check(VaultSelection.backup("existing",9,10L));check(!VaultSelection.backup("existing",10,null));
        check(VaultSelection.backup("all",10,null));check(VaultSelection.backup("all",10,11L));
        check(VaultSelection.restore("missing",10,null));check(!VaultSelection.restore("missing",10,9L));
        check(VaultSelection.restore("newer",10,9L));check(!VaultSelection.restore("newer",10,10L));
        check(!VaultSelection.restore("newer",10,11L));check(VaultSelection.restore("newer",10,null));
        check(VaultSelection.restore("all",10,11L));
        System.out.println("HeckerVault quick backup/restore scope selection: 17 PASS");
    }
}

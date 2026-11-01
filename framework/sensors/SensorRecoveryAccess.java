// SPDX-License-Identifier: Apache-2.0
package com.android.server.hecker;

/** Package identity, platform signature and foreground user are all required. */
public final class SensorRecoveryAccess {
    public static boolean suite(int uid,int foreground,int packageUid,boolean signed){
        return uid>=10000&&uid/100000==foreground&&uid==packageUid&&signed;
    }
    public static boolean shell(int uid){return uid==0||uid==2000;}
    private SensorRecoveryAccess(){}
}

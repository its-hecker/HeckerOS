// SPDX-License-Identifier: Apache-2.0
package com.android.internal.hecker;

/** @hide Shared policy values. Resident apps are always excluded from cleanup. */
public final class HeckerMemoryRules {
    public static final String SETTING = "hecker_memory_rules";
    public static final int BLOCK_START = 1, BLOCK_BACKGROUND = 2, CLEAN_TASK = 4, RESIDENT = 8;
    public static final int ALL = 15;
    private HeckerMemoryRules() {}
    public static int validated(int flags) { return flags >= 0 && (flags & ~ALL) == 0 ? flags : 0; }
    public static boolean clean(int flags) { return (validated(flags) & RESIDENT) == 0; }
    public static boolean shouldClean(int flags,int adj,int minAdj,boolean home,boolean backgroundOnly) {
        int valid=validated(flags);
        return clean(valid)&&adj>=minAdj&&!home&&(!backgroundOnly||(valid&BLOCK_BACKGROUND)!=0);
    }
    public static boolean blockStart(int flags, boolean foregroundLaunch) {
        return !foregroundLaunch && (validated(flags) & BLOCK_START) != 0;
    }
    public static boolean protectedPackage(String pkg) {
        return pkg == null || pkg.equals("android") || pkg.equals("org.hecker.suite")
                || pkg.equals("com.android.systemui") || pkg.equals("com.android.settings")
                || pkg.equals("com.google.android.apps.nexuslauncher")
                || pkg.equals("com.android.permissioncontroller")
                || pkg.equals("com.google.android.permissioncontroller")
                || pkg.equals("com.android.packageinstaller")
                || pkg.equals("com.google.android.packageinstaller");
    }
}

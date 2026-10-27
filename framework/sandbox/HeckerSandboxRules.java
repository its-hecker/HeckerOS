// Copyright (C) 2025-2026 AxionOS
// Copyright (C) 2026 HeckerOS
// SPDX-License-Identifier: Apache-2.0
package com.android.internal.hecker;

import java.util.Arrays;

/**
 * Axion/Infinity-X GID restrictions and native app-data isolation policy.
 * @hide
 */
public final class HeckerSandboxRules {
    public static final String SETTING = "hecker_sandbox_apps";
    public static final int ISOLATE_DATA = 1;
    public static final int RESTRICT_NETWORK = 2;
    public static final int RESTRICT_STORAGE = 4;
    // AxionAOSP/android_axion_sdk, AxSandboxManager.STORAGE_GIDS.
    private static final int[] STORAGE_GIDS = {1015, 1023, 1077, 1078, 1079};

    private HeckerSandboxRules() {}

    public static boolean valid(int mask) { return mask >= 0 && mask <= 7; }

    public static int[] restrictGids(int[] gids, int mask) {
        if (gids == null || mask == 0 || !valid(mask)) return gids;
        int[] filtered = new int[gids.length];
        int count = 0;
        for (int gid : gids) {
            if ((mask & RESTRICT_NETWORK) != 0 && gid == 3003) continue;
            if ((mask & RESTRICT_STORAGE) != 0 && storage(gid)) continue;
            filtered[count++] = gid;
        }
        return count == gids.length ? gids : Arrays.copyOf(filtered, count);
    }

    private static boolean storage(int gid) {
        for (int restricted : STORAGE_GIDS) if (restricted == gid) return true;
        return false;
    }
}

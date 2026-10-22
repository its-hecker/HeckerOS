// SPDX-License-Identifier: Apache-2.0
package com.android.internal.hecker;
import java.util.Arrays;

public final class HeckerSandboxRulesTest {
    private static void expect(int mask, int[] expected) {
        int[] original = {1000, 3003, 1015, 1023, 1077, 1078, 1079, 9999};
        int[] before = original.clone();
        int[] actual = HeckerSandboxRules.restrictGids(original, mask);
        if (!Arrays.equals(expected, actual)) throw new AssertionError("Mask " + mask);
        if (!Arrays.equals(before, original)) throw new AssertionError("Changed PackageManager-owned GIDs");
    }
    public static void main(String[] args) {
        int[] original = {1000, 3003, 1015, 1023, 1077, 1078, 1079, 9999};
        expect(0, original); expect(1, original);
        expect(2, new int[]{1000, 1015, 1023, 1077, 1078, 1079, 9999});
        expect(4, new int[]{1000, 3003, 9999});
        expect(7, new int[]{1000, 9999});
        expect(8, original); expect(-1, original);
        if (HeckerSandboxRules.restrictGids(null, 7) != null) throw new AssertionError("Isolated process GIDs");
        if (HeckerSandboxRules.restrictGids(new int[0], 7).length != 0) throw new AssertionError("Empty GIDs");
        System.out.println("Sandbox restrictions: independent flags, system GIDs and input ownership PASS");
    }
}

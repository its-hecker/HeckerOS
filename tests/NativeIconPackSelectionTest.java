// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeIconPackSelectionTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Native icon selection check " + checks);
    }
    public static void main(String[] args) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("android.theme.customization.system_palette", "aabbcc");
        before.put("android.theme.customization.font", "font.package");
        before.put("_applied_timestamp", 1234L);
        before.put(NativeIconPackSelection.ANDROID, "old.android");
        before.put(NativeIconPackSelection.SYSTEM_UI, "old.systemui");
        Map<String, String> selected = Map.of(NativeIconPackSelection.ANDROID, "new.android",
                NativeIconPackSelection.SYSTEM_UI, "new.systemui");
        Map<String, Object> applied = NativeIconPackSelection.apply(before, selected);
        check(applied.get(NativeIconPackSelection.ANDROID).equals("new.android"));
        check(applied.get(NativeIconPackSelection.SYSTEM_UI).equals("new.systemui"));
        check(applied.get("android.theme.customization.system_palette").equals("aabbcc"));
        check(applied.get("android.theme.customization.font").equals("font.package"));
        check(applied.get("_applied_timestamp").equals(1234L));
        check(before.get(NativeIconPackSelection.ANDROID).equals("old.android"));
        // Default removes both previous icon packs, while retaining native color/font metadata.
        Map<String, Object> cleared = NativeIconPackSelection.apply(applied, Map.of());
        check(!cleared.containsKey(NativeIconPackSelection.ANDROID));
        check(!cleared.containsKey(NativeIconPackSelection.SYSTEM_UI));
        check(cleared.size() == 3);
        // A theme cannot replace unrelated customization categories through this action.
        try {
            NativeIconPackSelection.apply(before, Map.of("android.theme.customization.font", "other"));
            throw new AssertionError("Unexpected category was accepted");
        } catch (IllegalArgumentException expected) { check(before.size() == 5); }
        System.out.println("Native icon persistence, default reset and color/font preservation: " + checks + " checks passed");
    }
}

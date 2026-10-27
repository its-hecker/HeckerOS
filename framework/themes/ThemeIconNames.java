/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

/** Maps Android 17's native symbols onto the pinned five-level theme assets. */
public final class ThemeIconNames {
    private ThemeIconNames() {}

    public static String wifi(String nativeName) {
        if (nativeName == null) return null;
        if (nativeName.matches("ic_wifi_signal_[0-4]")) return nativeName;
        if (nativeName.matches("ic_wifi_[0-3]")) {
            // NewStatusBarIcons uses three bars: its ic_wifi_3 means full strength.
            int level = nativeName.charAt(nativeName.length() - 1) - '0';
            return "ic_wifi_signal_" + Math.round(level * 4f / 3f);
        }
        // Error, connecting, hotspot and off icons carry information packs may not supply.
        return null;
    }

    public static String signal(int level, int levels) {
        if ((levels != 5 && levels != 6) || level < 0 || level >= levels) return null;
        return "ic_signal_cellular_" + level + "_" + (levels - 1) + "_bar";
    }
}

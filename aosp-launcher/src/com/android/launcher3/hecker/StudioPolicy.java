// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** Android-free bounds and formatting shared by the editors and renderers. */
public final class StudioPolicy {
    public static final String[] CLOCKS = {"glass", "minimal", "mono", "signature", "compact", "thermal", "digital", "analog", "stacked", "caracal"};
    public static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    public static String clock(String style) {
        for (String known : CLOCKS) if (known.equals(style)) return known; return "signature";
    }
    public static String badge(int count) { return count <= 0 ? "" : count > 99 ? "99+" : Integer.toString(count); }
    public static float battery(int level, int scale) { return scale <= 0 || level < 0 ? -1 : Math.min(1f, (float) level / scale); }
    public static long nextMinute(long now) { return 60000 - Math.floorMod(now, 60000); }
    public static boolean matches(String query, String label, String packageName) {
        String haystack = (label + " " + packageName).toLowerCase(java.util.Locale.ROOT);
        for (String token : query.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+")) if (!haystack.contains(token)) return false;
        return true;
    }
    private StudioPolicy() {}
}

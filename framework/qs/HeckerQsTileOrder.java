// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import java.util.LinkedHashSet;

/** One-time reference layout migration; keeps every other user-selected tile. */
public final class HeckerQsTileOrder {
    public static final String DOLBY = "custom(org.lunaris.dolby/.tile.DolbyTileService)";
    private static final String DOLBY_FULL =
            "custom(org.lunaris.dolby/org.lunaris.dolby.tile.DolbyTileService)";
    private static final String[] REFERENCE = {"wifi", "bt", "rotation", "airplane",
            "flashlight", "cell", "cast", "battery", "location", "hotspot"};
    private HeckerQsTileOrder() {}
    public static boolean isDolby(String spec) {
        return spec != null && (DOLBY.equals(spec.trim()) || DOLBY_FULL.equals(spec.trim()));
    }
    /** Bind the already-installed native tile without duplicating an existing selection. */
    public static String withDolby(String current) {
        if (current == null || current.trim().isEmpty()) return DOLBY;
        for (String spec : current.split(",")) if (isDolby(spec)) return current;
        return current + (current.endsWith(",") ? "" : ",") + DOLBY;
    }
    public static String withoutMemoryBoost(String current) {
        if (current == null) return null;
        java.util.ArrayList<String> retained = new java.util.ArrayList<>();
        for (String spec : current.split(",")) {
            String tile = spec.trim();
            if (!tile.equals("custom(org.hecker.suite/.MemoryBoostTileService)")
                    && !tile.equals("custom(org.hecker.suite/org.hecker.suite.MemoryBoostTileService)")) retained.add(spec);
        }
        return String.join(",", retained);
    }
    public static String migrate(String current) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String spec : REFERENCE) ordered.add(spec);
        if (current != null) for (String spec : current.split(",")) {
            spec = spec.trim();
            if (!spec.isEmpty() && !spec.equals("default")) ordered.add(spec);
        }
        return String.join(",", ordered);
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import java.util.LinkedHashSet;

/** One-time reference layout migration; keeps every other user-selected tile. */
public final class HeckerQsTileOrder {
    private static final String[] REFERENCE = {"wifi", "bt", "rotation", "airplane",
            "flashlight", "cell", "cast", "battery", "location", "hotspot"};
    private HeckerQsTileOrder() {}
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

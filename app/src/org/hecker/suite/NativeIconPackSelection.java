// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Native ThemeOverlayController categories; unrelated font/color choices survive. */
final class NativeIconPackSelection {
    static final String ANDROID = "android.theme.customization.icon_pack.android";
    static final String SYSTEM_UI = "android.theme.customization.icon_pack.systemui";
    private static final Set<String> CATEGORIES = Set.of(ANDROID, SYSTEM_UI);

    static Map<String, Object> apply(Map<String, Object> current, Map<String, String> selected) {
        for (Map.Entry<String, String> entry : selected.entrySet()) {
            if (!CATEGORIES.contains(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("Unsupported native icon overlay selection");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(current);
        for (String category : CATEGORIES) result.remove(category);
        result.putAll(selected);
        return result;
    }

    private NativeIconPackSelection() {}
}

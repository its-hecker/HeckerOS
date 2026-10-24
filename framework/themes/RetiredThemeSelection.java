/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.server.theme;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/** Remove only the retired bundled resource packages; keep colors, fonts and other choices. */
public final class RetiredThemeSelection {
    private static final Set<String> PACKAGES = Set.of(
        "com.android.systemui.charging_animation.moto",
        "com.android.systemui.charging_animation.nothing",
        "com.android.systemui.signalGradicon",
        "com.android.systemui.signalNothingDot",
        "com.android.systemui.signalbar_a",
        "com.android.systemui.signalbar_aa",
        "com.android.systemui.signalbar_ab",
        "com.android.systemui.signalbar_ac",
        "com.android.systemui.signalbar_ad",
        "com.android.systemui.signalbar_ae",
        "com.android.systemui.signalbar_af",
        "com.android.systemui.signalbar_ag",
        "com.android.systemui.signalbar_ah",
        "com.android.systemui.signalbar_ai",
        "com.android.systemui.signalbar_aj",
        "com.android.systemui.signalbar_ak",
        "com.android.systemui.signalbar_al",
        "com.android.systemui.signalbar_am",
        "com.android.systemui.signalbar_an",
        "com.android.systemui.signalbar_ao",
        "com.android.systemui.signalbar_ap",
        "com.android.systemui.signalbar_aq",
        "com.android.systemui.signalbar_ar",
        "com.android.systemui.signalbar_as",
        "com.android.systemui.signalbar_at",
        "com.android.systemui.signalbar_au",
        "com.android.systemui.signalbar_b",
        "com.android.systemui.signalbar_c",
        "com.android.systemui.signalbar_d",
        "com.android.systemui.signalbar_f",
        "com.android.systemui.signalbar_g",
        "com.android.systemui.signalbar_h",
        "com.android.systemui.signalbar_j",
        "com.android.theme.icon_pack.acherus.android",
        "com.android.theme.icon_pack.acherus.systemui",
        "com.android.theme.icon_pack.aurora.android",
        "com.android.theme.icon_pack.aurora.systemui",
        "com.android.theme.icon_pack.circular.android",
        "com.android.theme.icon_pack.circular.systemui",
        "com.android.theme.icon_pack.filled.android",
        "com.android.theme.icon_pack.filled.systemui",
        "com.android.theme.icon_pack.gradicon.android",
        "com.android.theme.icon_pack.gradicon.systemui",
        "com.android.theme.icon_pack.kai.android",
        "com.android.theme.icon_pack.kai.systemui",
        "com.android.theme.icon_pack.lorn.android",
        "com.android.theme.icon_pack.lorn.systemui",
        "com.android.theme.icon_pack.nostalgic.android",
        "com.android.theme.icon_pack.nostalgic.systemui",
        "com.android.theme.icon_pack.oos.android",
        "com.android.theme.icon_pack.oos.systemui",
        "com.android.theme.icon_pack.outline.android",
        "com.android.theme.icon_pack.outline.systemui",
        "com.android.theme.icon_pack.plumpy.android",
        "com.android.theme.icon_pack.plumpy.systemui",
        "com.android.theme.icon_pack.pui.android",
        "com.android.theme.icon_pack.pui.systemui",
        "com.android.theme.icon_pack.rounded.android",
        "com.android.theme.icon_pack.rounded.systemui",
        "com.android.theme.icon_pack.sam.android",
        "com.android.theme.icon_pack.sam.systemui",
        "com.android.theme.icon_pack.victor.android",
        "com.android.theme.icon_pack.victor.systemui",
        "com.android.theme.icon_pack.xperia.android",
        "com.android.theme.icon_pack.xperia.systemui",
        "com.crdroid.systemui.signalbar_e",
        "com.custom.overlay.systemui.signalAurora",
        "com.custom.overlay.systemui.signalFaintUI",
        "com.custom.overlay.systemui.signalForlorn",
        "com.custom.overlay.systemui.signalPlumpy");
    public static Map<String, Object> apply(Map<String, Object> current) {
        Map<String, Object> result = new LinkedHashMap<>();
        current.forEach((key, value) -> {
            if (!(value instanceof String) || !PACKAGES.contains(value)) result.put(key, value);
        });
        return result;
    }
    private RetiredThemeSelection() {}
}

// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import java.nio.charset.StandardCharsets;

/** Bluetooth metadata uses UTF-8 values. Unknown data must never become 0%. */
public final class HeadphoneBattery {
    private HeadphoneBattery() {}

    public static int level(byte[] data) {
        if (data == null || data.length == 0) return -1;
        try {
            int value = Integer.parseInt(new String(data, StandardCharsets.UTF_8).trim());
            return valid(value);
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    public static int valid(int value) {
        return value >= 0 && value <= 100 ? value : -1;
    }

    public static boolean hasComponents(int left, int right, int chargingCase) {
        return valid(left) >= 0 || valid(right) >= 0 || valid(chargingCase) >= 0;
    }
}

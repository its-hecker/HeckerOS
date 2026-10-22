package com.android.systemui.hecker;

public final class ThemeIconNamesTest {
    private static void equal(String expected, String value) {
        if (!java.util.Objects.equals(expected, value)) throw new AssertionError(expected + " != " + value);
    }
    public static void main(String[] args) {
        for (int level = 0; level <= 4; level++) {
            equal("ic_wifi_signal_" + level, ThemeIconNames.wifi("ic_wifi_signal_" + level));
            equal("ic_signal_cellular_" + level + "_4_bar", ThemeIconNames.signal(level, 5));
        }
        equal("ic_wifi_signal_4", ThemeIconNames.wifi("ic_wifi_3"));
        equal("ic_wifi_signal_3", ThemeIconNames.wifi("ic_wifi_2"));
        equal("ic_wifi_signal_0", ThemeIconNames.wifi("ic_wifi_0"));
        for (String name : new String[]{null, "ic_wifi_3_error", "ic_no_internet_wifi_signal_4",
                "ic_wifi_off", "ic_wifi_connecting", "ic_hotspot_phone", "ic_wifi_signal_5"}) {
            equal(null, ThemeIconNames.wifi(name));
        }
        equal("ic_signal_cellular_5_5_bar", ThemeIconNames.signal(5, 6));
        equal(null, ThemeIconNames.signal(6, 6));
        equal(null, ThemeIconNames.signal(-1, 5));
        equal(null, ThemeIconNames.signal(2, 4));
        System.out.println("Theme connectivity symbols, full strength and native error fallbacks: PASS");
    }
}

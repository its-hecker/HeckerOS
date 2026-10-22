// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;
import java.nio.charset.StandardCharsets;

public final class HeadphoneBatteryTest {
    private static void expect(int expected, String text) {
        int actual = HeadphoneBattery.level(text == null ? null : text.getBytes(StandardCharsets.UTF_8));
        if (actual != expected) throw new AssertionError(text + ": " + actual);
    }
    public static void main(String[] args) {
        expect(-1, null); expect(-1, ""); expect(-1, "unknown");
        expect(-1, "255"); expect(-1, "-1"); expect(-1, "101");
        expect(0, "0"); expect(100, "100"); expect(56, " 56 ");
        if (HeadphoneBattery.valid(-100) != -1) throw new AssertionError("Bluetooth-off sentinel");
        if (HeadphoneBattery.hasComponents(-1, -1, -1)) throw new AssertionError("Invented components");
        if (!HeadphoneBattery.hasComponents(-1, 0, -1)) throw new AssertionError("Real empty battery hidden");
        System.out.println("Headphone metadata: unknown, empty, invalid and real zero readings PASS");
    }
}

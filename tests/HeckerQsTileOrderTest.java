// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
public final class HeckerQsTileOrderTest {
    public static void main(String[] args) {
        String expected = "wifi,bt,rotation,airplane,flashlight,cell,cast,battery,location,hotspot";
        check(expected.equals(HeckerQsTileOrder.migrate(null)), "new user's reference order");
        check(expected.equals(HeckerQsTileOrder.migrate("default, ,wifi,wifi")), "sentinels and duplicates");
        String custom = "custom(example/.Tile),nfc,controls,dark,custom(org.hecker.suite/.SuiteTileService)";
        String migrated = HeckerQsTileOrder.migrate("bt,location," + custom);
        check(migrated.equals(expected + "," + custom), "custom tiles retained in their order");
        check(migrated.equals(HeckerQsTileOrder.migrate(migrated)), "repeat migration is stable");
        Set<String> selected = new LinkedHashSet<>(Arrays.asList(migrated.split(",")));
        for (String spec : custom.split(",")) check(selected.contains(spec), "selection lost: " + spec);
        System.out.println("QS reference migration and custom selection preservation: PASS");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

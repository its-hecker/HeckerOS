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
        String dolby = HeckerQsTileOrder.DOLBY;
        check(dolby.equals(HeckerQsTileOrder.withDolby(null)), "fresh fixed-slot binding");
        check(dolby.equals(HeckerQsTileOrder.withDolby("  ")), "empty user selection");
        String existing = "bt,custom(example/.Tile),nfc";
        check((existing + "," + dolby).equals(HeckerQsTileOrder.withDolby(existing)), "retain custom tile order");
        String bound = HeckerQsTileOrder.withDolby(existing);
        check(bound.equals(HeckerQsTileOrder.withDolby(bound)), "repeat Dolby migration is stable");
        String full = "custom(org.lunaris.dolby/org.lunaris.dolby.tile.DolbyTileService)";
        check(HeckerQsTileOrder.isDolby(dolby) && HeckerQsTileOrder.isDolby(full), "both native component forms");
        check((existing + "," + full).equals(HeckerQsTileOrder.withDolby(existing + "," + full)), "reuse full component selection");
        check((existing + "," + dolby).equals(HeckerQsTileOrder.withDolby(existing + ",")), "trailing delimiter");
        check(!HeckerQsTileOrder.isDolby("custom(other/.DolbyTileService)"), "unrelated tile is retained");
        check(!HeckerQsTileOrder.isDolby(null), "missing component is not Dolby");
        check(existing.equals(HeckerQsTileOrder.withoutMemoryBoost(existing)), "unrelated selection unchanged");
        check(existing.equals(HeckerQsTileOrder.withoutMemoryBoost("custom(org.hecker.suite/.MemoryBoostTileService),"+existing+",custom(org.hecker.suite/org.hecker.suite.MemoryBoostTileService)")), "both retired tile forms removed");
        check(HeckerQsTileOrder.withoutMemoryBoost(null)==null, "fresh default remains unset");
        System.out.println("QS reference migration and custom selection preservation: PASS");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

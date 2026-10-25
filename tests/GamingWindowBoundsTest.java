// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

public final class GamingWindowBoundsTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        GamingWindowBounds portrait = new GamingWindowBounds(40, 110, 1040, 2200, 800, 1200);
        check(portrait.x(-500) == 40 && portrait.y(-100) == 110);
        check(portrait.x(9000) == 240 && portrait.y(9000) == 1000);
        check(portrait.fromX(1) == 240 && portrait.fromY(.5f) == 555);
        check(portrait.fromX(Float.NaN) == 140 && portrait.fromY(Float.POSITIVE_INFINITY) == 555);
        check(portrait.fromX(-3) == 40 && portrait.fromY(5) == 1000);
        for (int x = 40; x <= 240; x++) check(portrait.fromX(portrait.fractionX(x)) == x);
        for (int y = 110; y <= 1000; y++) check(portrait.fromY(portrait.fractionY(y)) == y);
        GamingWindowBounds enlarged = new GamingWindowBounds(40, 110, 1040, 2200, 950, 1900);
        check(enlarged.x(240) == 90 && enlarged.y(1000) == 300);
        GamingWindowBounds landscape = new GamingWindowBounds(100, 32, 2100, 960, 700, 800);
        check(landscape.fromX(1) == 1400 && landscape.fromY(1) == 160);
        check(portrait.fromX(1) == 240); // Neither orientation overwrites the other geometry.
        GamingWindowBounds tiny = new GamingWindowBounds(20, 30, 21, 31, 800, 1200);
        check(tiny.fromX(1) == 20 && tiny.fromY(1) == 30);
        check(Float.isFinite(tiny.fractionX(20)) && Float.isFinite(tiny.fractionY(30)));
        System.out.println("Game window placement: cutout/nav bounds, resizing, normalized restore and tiny displays PASS");
    }
}

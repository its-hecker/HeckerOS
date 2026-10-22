// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

public final class GamingPanelLayoutTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        GamingPanelLayout portrait = GamingPanelLayout.fit(393, 829);
        check(portrait.width < 393 && portrait.height <= 560);
        GamingPanelLayout landscape = GamingPanelLayout.fit(829, 393);
        check(landscape.width <= 829 * .44 && landscape.height <= 393 - 20);
        for (int width = 120; width <= 1400; width += 37) {
            for (int height = 120; height <= 1400; height += 53) {
                GamingPanelLayout bounds = GamingPanelLayout.fit(width, height);
                check(bounds.width > 0 && bounds.height > 0);
                check(bounds.width <= width-16 && bounds.height <= height-20);
                check(bounds.width <= 360 && bounds.height <= 560);
            }
        }
        GamingPanelLayout tiny = GamingPanelLayout.fit(0, 0);
        check(tiny.width == 1 && tiny.height == 1);
        System.out.println("Game Space: portrait/landscape, safe-area and compact-window bounds PASS");
    }
}

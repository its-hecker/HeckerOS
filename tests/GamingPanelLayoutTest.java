// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

public final class GamingPanelLayoutTest {
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    public static void main(String[] args) {
        GamingPanelLayout portrait = GamingPanelLayout.fit(393, 829);
        check(portrait.width < 393 && portrait.height <= 560);
        GamingPanelLayout landscape = GamingPanelLayout.fit(829, 393);
        check(landscape.width <= 829 * .44 && landscape.height <= 393 - 8);
        for (int width = 120; width <= 1400; width += 37) {
            for (int height = 120; height <= 1400; height += 53) {
                GamingPanelLayout bounds = GamingPanelLayout.fit(width, height);
                check(bounds.width > 0 && bounds.height > 0);
                check(bounds.width <= width-16 && bounds.height <= height-8);
                check(bounds.width <= 360 && bounds.height <= 560);
            }
        }
        GamingPanelLayout tiny = GamingPanelLayout.fit(0, 0);
        check(tiny.width == 1 && tiny.height == 1);
        for(int w:new int[]{0,120,393,829,1400})for(int h:new int[]{0,120,393,829,1400}){
            GamingPanelLayout maximum=GamingPanelLayout.resize(w,h,Integer.MAX_VALUE,Integer.MAX_VALUE);
            GamingPanelLayout minimum=GamingPanelLayout.resize(w,h,1,1);
            check(maximum.width==Math.max(1,w-16)&&maximum.height==Math.max(1,h-8));
            check(minimum.width==Math.min(220,maximum.width)&&minimum.height==Math.min(240,maximum.height));
            GamingPanelLayout saved=GamingPanelLayout.resize(w,h,300,350);
            check(saved.width<=maximum.width&&saved.height<=maximum.height);
            check(saved.width>=minimum.width&&saved.height>=minimum.height);
        }
        check(GamingPanelLayout.resize(829,393,500,350).width==500);
        System.out.println("Game Space: portrait/landscape, safe-area and compact-window bounds PASS");
    }
}

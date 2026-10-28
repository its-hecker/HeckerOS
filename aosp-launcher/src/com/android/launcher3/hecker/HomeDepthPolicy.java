// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** Pure ownership and crop policy; both wallpaper layers always use the same transform. */
public final class HomeDepthPolicy {
    public static boolean eligible(boolean enabled, boolean live, String id, int expected, int current) {
        return enabled && !live && id != null && id.matches("[a-f0-9]{32}") && expected > 0 && expected == current;
    }
    public static void crop(int iw,int ih,int width,int height,float reveal,float[] out) {
        if(iw<=0||ih<=0||width<=0||height<=0||out.length<3)throw new IllegalArgumentException("Empty depth viewport");
        float t=Float.isFinite(reveal)?Math.max(0,Math.min(1,reveal)):1;
        float scale=Math.max((float)width/iw,(float)height/ih)*(1.025f-.025f*t);
        out[0]=scale;out[1]=(width-iw*scale)/2;out[2]=(height-ih*scale)/2;
    }
    private HomeDepthPolicy(){}
}

// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Geometry is bounded to the real status-bar window, never drawn over application content. */
public final class StatusBarRowsPolicy {
    public static int heightDp(int preset) { return new int[]{44, 52, 60}[Math.max(0, Math.min(2, preset))]; }
    public static final class Row {
        public final int top, height, second;
        Row(int top, int height, int second) { this.top=top;this.height=height;this.second=second; }
    }
    public static Row rows(int height, int top, int bottom, int gap, boolean dual) {
        top=Math.max(0,Math.min(height,top));bottom=Math.max(0,Math.min(height-top,bottom));
        int available=Math.max(0,height-top-bottom);
        gap=Math.max(0,Math.min(gap,Math.max(0,available-2)));
        int h=dual?Math.max(0,(available-gap)/2):available;
        return new Row(top,h,dual?top+h+gap:top);
    }
    public static final class Slot {
        public final int lyricLeft, lyricWidth, iconsLeft, iconsWidth;
        Slot(int l,int w,int il,int iw){lyricLeft=l;lyricWidth=w;iconsLeft=il;iconsWidth=iw;}
    }
    public static Slot slot(int left,int right,int requested,int minimum,int reserve,int gap,int alignment) {
        int available=Math.max(0,right-left);
        reserve=Math.max(0,Math.min(available,reserve));gap=Math.max(0,gap);
        int space=available-reserve-(reserve>0?gap:0);
        if(alignment==1&&reserve>0)space=available-2*(reserve+gap);
        int width=Math.max(0,Math.min(requested,space));
        if(width<minimum)return new Slot(left,0,left,available);
        if(alignment==0)return new Slot(left,width,Math.min(right,left+width+gap),Math.max(0,available-width-gap));
        if(alignment==1){int x=left+(available-width)/2;return new Slot(x,width,left,Math.max(0,x-left-gap));}
        return new Slot(right-width,width,left,Math.max(0,available-width-gap));
    }
    private StatusBarRowsPolicy(){}
}

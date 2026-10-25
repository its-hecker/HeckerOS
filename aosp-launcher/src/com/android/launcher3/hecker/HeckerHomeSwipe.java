// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

/** A downward empty-workspace gesture, independent of the double-tap preference. */
public final class HeckerHomeSwipe {
    private boolean eligible;
    private float x,y;
    private long time;
    public void down(float x,float y,long time){this.x=x;this.y=y;this.time=time;eligible=true;}
    public void cancel(){eligible=false;}
    public void move(float x,float y,float slop){if(Math.abs(x-this.x)>slop&&Math.abs(x-this.x)>Math.abs(y-this.y))cancel();}
    public boolean up(float x,float y,long time,float threshold){
        boolean result=eligible&&time>=this.time&&time-this.time<=1000
                &&y-this.y>=threshold&&Math.abs(x-this.x)<(y-this.y)*.5f;
        eligible=false;return result;
    }
}

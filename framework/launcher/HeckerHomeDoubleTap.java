// SPDX-License-Identifier: Apache-2.0
package com.android.internal.hecker;
/** @hide Empty-workspace tap recognizer, independent of Android views for regression testing. */
public final class HeckerHomeDoubleTap {
    private long previous=-1,down=-1;
    private float previousX,previousY,x,y;
    private boolean eligible;
    public void down(long time,float x,float y,boolean empty){this.down=time;this.x=x;this.y=y;eligible=empty;if(!empty)previous=-1;}
    public void move(float x,float y,float slop,boolean multiple){if(multiple||Math.hypot(x-this.x,y-this.y)>slop)cancel();}
    public boolean up(long time,float x,float y,boolean empty,long interval,float slop){
        if(!eligible||!empty||down<0||time<down||time-down>250){cancel();return false;}
        eligible=false;boolean twice=previous>=0&&time>=previous&&time-previous<=interval&&Math.hypot(x-previousX,y-previousY)<=slop;
        previous=twice?-1:time;previousX=x;previousY=y;down=-1;return twice;
    }
    public void cancel(){eligible=false;previous=-1;down=-1;}
}

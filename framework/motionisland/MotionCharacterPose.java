// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Bounded choreography in body units: monster hops, goblin bows, ninja dashes. */
final class MotionCharacterPose {
    static void sample(float[] out,int character,int action,int direction,long age){
        float t=Math.max(0,Math.min(1,age/(float)MotionIslandState.ACTION_DURATION));
        float envelope=(float)Math.sin(Math.PI*t);
        float beat=(float)Math.sin(t*Math.PI*4);
        out[0]=0;out[1]=0;out[2]=0;out[3]=1;out[4]=1;
        if(character==1){
            out[1]=-.12f*Math.abs(beat)*envelope;
            out[3]=1+.06f*beat*envelope;out[4]=1-.06f*beat*envelope;
            out[2]=(action==MotionIslandState.PAUSE?12:5)*beat*envelope;
            out[0]=direction*.20f*envelope;
        }else if(character==2){
            out[2]=(action==MotionIslandState.PAUSE?18:9)*envelope*beat;
            out[1]=action==MotionIslandState.PAUSE?.10f*envelope:-.045f*beat*envelope;
            out[0]=direction*.13f*beat*envelope;
            out[3]=1+.035f*envelope;out[4]=1-.035f*envelope;
        }else if(character==3){
            out[0]=(direction==0?1:direction)*.32f*beat*envelope;
            out[2]=(direction==0?-1:direction)*(action==MotionIslandState.PAUSE?14:26)*envelope;
            out[1]=-.09f*Math.abs(beat)*envelope;
        }
    }
    static void joints(float[] out, int character, int action, long age) {
        float t=Math.max(0,Math.min(1,age/(float)MotionIslandState.ACTION_DURATION));
        float e=(float)Math.sin(Math.PI*t), wave=(float)Math.sin(t*Math.PI*8);
        boolean bye=action==MotionIslandState.PAUSE;
        if(character==1) { out[0]=8*wave*e;out[1]=-24*e;out[2]=(bye?52:35)*wave*e; }
        else if(character==2) { out[0]=(bye?18:12)*(float)Math.sin(t*Math.PI*3)*e;out[1]=18*wave*e;out[2]=-32*e; }
        else { out[0]=-10*e;out[1]=(bye?-38:28)*e;out[2]=42*(float)Math.sin(t*Math.PI*5)*e; }
    }
    private MotionCharacterPose(){}
}

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
    private MotionCharacterPose(){}
}

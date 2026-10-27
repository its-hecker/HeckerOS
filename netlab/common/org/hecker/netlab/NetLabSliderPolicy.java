// SPDX-License-Identifier: Apache-2.0
package org.hecker.netlab;

/** Shared slider meaning: left cuts IPv4, middle limits, right restores; cancelled/no-op gestures do nothing. */
public final class NetLabSliderPolicy {
    public static final int MAX=12;
    public enum Action { NONE, BLOCK, LIMIT, RESTORE }
    public static Action action(int initial,int value,boolean userChanged,boolean cancelled){
        if(cancelled||!userChanged||initial==value||value<0||value>MAX)return Action.NONE;
        return value==0?Action.BLOCK:value==MAX?Action.RESTORE:Action.LIMIT;
    }
    public static int rate(int value){return value<=0||value>=MAX?0:64<<(value-1);}
    public static int ratePosition(int kbps){if(kbps<=0)return MAX;for(int p=1;p<MAX;p++)if(rate(p)>=kbps)return p;return MAX-1;}
    public static boolean canEdit(boolean active,boolean busy,boolean stopped,String state,long now,long expiry){
        return active&&busy&&!stopped&&!"restoring".equals(state)&&now<expiry;
    }
    public static int position(String state,String mode,String requested,int down,int requestedDown){
        boolean pending="applying".equals(state)||"active_unverified".equals(state);
        boolean owned=pending||"blocking".equals(state)||"relaying".equals(state);
        if(!owned)return MAX;
        String policy=pending&&!requested.isEmpty()?requested:mode;
        if(policy.equals("BLOCK"))return 0;
        if(policy.equals("LIMIT")){int value=pending?requestedDown:down;return value==0?MAX-1:ratePosition(value);}
        return "blocking".equals(state)?0:MAX;
    }
    private NetLabSliderPolicy(){}
}

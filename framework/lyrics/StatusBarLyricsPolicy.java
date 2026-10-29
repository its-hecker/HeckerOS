// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

/** Shared playback clock with a one-line status presentation and no polling without synced data. */
public final class StatusBarLyricsPolicy {
    public static final class Frame {
        public final String text;
        public final long delayMs;
        Frame(String text,long delayMs){this.text=text;this.delayMs=delayMs;}
    }
    public static Frame frame(LyricsController.Snapshot state,long now){
        if(state==null||!state.playing||state.lines.isEmpty()||!Float.isFinite(state.speed))
            return new Frame("",-1);
        long position=LyricsPolicy.position(state.position,state.updatedAt,now,state.speed,true,state.duration);
        if(state.duration>0&&position>=state.duration)return new Frame("",-1);
        int index=LyricsPolicy.activeIndex(state.lines,position);
        String text=index>=0?state.lines.get(index).text.trim():"";
        if(text.codePointCount(0,text.length())>256)text=text.substring(0,text.offsetByCodePoints(0,256));
        long delay=1000;
        if(state.speed>0&&index+1<state.lines.size())
            delay=(long)Math.ceil((state.lines.get(index+1).timeMs-position)/state.speed);
        return new Frame(text,Math.max(25,Math.min(1000,delay)));
    }
    private StatusBarLyricsPolicy(){}
}

// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.widget.SeekBar;

/** Uses the same temporary/persistent display API as native BrightnessController. */
public final class HeckerBrightnessControl {
    private final DisplayManager displays;
    private final Display display;
    private float min=0,max=1;
    private boolean tracking;
    public HeckerBrightnessControl(Context context,SeekBar slider){
        displays=context.getSystemService(DisplayManager.class);display=displays.getDisplay(Display.DEFAULT_DISPLAY);
        BrightnessInfo info=display==null?null:display.getBrightnessInfo();
        if(info!=null){min=info.brightnessMinimum;max=info.brightnessMaximum;}
        if(!Float.isFinite(min)||!Float.isFinite(max)||max<=min){min=0;max=1;}
        slider.setMax(1000);
        float current=displays.getBrightness(Display.DEFAULT_DISPLAY);
        slider.setProgress(Math.round((float)Math.pow(Math.max(0,Math.min(1,(current-min)/(max-min))),1/2.2)*1000));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){tracking=true;}
            public void onProgressChanged(SeekBar s,int progress,boolean user){
                if(!user)return;float brightness=level(progress);
                if(tracking)displays.setTemporaryBrightness(Display.DEFAULT_DISPLAY,brightness);
                else displays.setBrightness(Display.DEFAULT_DISPLAY,brightness);
            }
            public void onStopTrackingTouch(SeekBar s){tracking=false;displays.setBrightness(Display.DEFAULT_DISPLAY,level(s.getProgress()));}
        });
    }
    public void close(){if(tracking){tracking=false;displays.setTemporaryBrightness(Display.DEFAULT_DISPLAY,Float.NaN);}}
    private float level(int progress){return min+(max-min)*(float)Math.pow(progress/1000f,2.2);}
}

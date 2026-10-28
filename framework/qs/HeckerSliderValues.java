// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import java.text.NumberFormat;
import java.util.Locale;

/** Display-only values: never converts gamma or writes brightness/volume policy. */
public final class HeckerSliderValues {
    public static float unit(float fraction){return Float.isFinite(fraction)?Math.max(0f,Math.min(1f,fraction)):0f;}
    public static float fraction(float value,float minimum,float maximum){
        if(!Float.isFinite(value)||!Float.isFinite(minimum)||!Float.isFinite(maximum)||maximum<=minimum)return 0f;
        return unit((value-minimum)/(maximum-minimum));
    }
    public static int percent(float fraction){
        // Integer stream ratios can land one float ULP below a half-percent.
        return Math.round(unit(fraction)*100f+0.00001f);
    }
    public static String label(float fraction,Locale locale){
        NumberFormat format=NumberFormat.getPercentInstance(locale==null?Locale.getDefault():locale);
        format.setMinimumFractionDigits(0);format.setMaximumFractionDigits(0);
        return format.format(percent(fraction)/100.0);
    }
    public static int verticalThickness(float fontScale){
        if(!Float.isFinite(fontScale))fontScale=1f;
        return Math.round(64f+16f*(Math.max(1f,Math.min(3f,fontScale))-1f));
    }
    private HeckerSliderValues(){}
}

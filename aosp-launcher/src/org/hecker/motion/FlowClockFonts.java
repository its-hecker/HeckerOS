// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.motion;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;

/** Loads the approved preview's open font once per process, never during drawing. */
public final class FlowClockFonts {
    private static Typeface regular,bold;
    public static synchronized void apply(Context context,FlowClockRenderer renderer) {
        try {
            if(regular==null||bold==null) {
                Resources r=context.getResources(); String pkg=context.getPackageName();
                int a=r.getIdentifier("hecker_living_flow_regular","font",pkg);
                int b=r.getIdentifier("hecker_living_flow_bold","font",pkg);
                if(a==0||b==0)return;
                regular=r.getFont(a); bold=r.getFont(b);
            }
            renderer.setReferenceFaces(regular,bold);
        } catch(RuntimeException unavailable) { /* Native font remains a safe fallback. */ }
    }
    private FlowClockFonts() {}
}

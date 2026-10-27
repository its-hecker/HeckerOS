// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Uses the same sampled wallpaper, blur, tint and border renderer as actual launcher containers. */
public final class HeckerGlassPreview extends FrameLayout {
    public HeckerGlassPreview(Context c) {
        super(c); setTag("hecker_glass_preview");
        TextView sample = HeckerStudio.label(c, HeckerStudio.text(c, "live_preview") + "\n10:08  ·  Hecker", 22);
        sample.setGravity(Gravity.CENTER); addView(sample, new LayoutParams(-1, -1));
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); HeckerLauncherGlass.attach(this); }
    @Override protected void onDetachedFromWindow() { HeckerLauncherGlass.detach(this); super.onDetachedFromWindow(); }
}

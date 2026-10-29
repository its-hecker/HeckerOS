// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.animation.Interpolator;
import org.hecker.motion.FlowClockRenderer;

/** Shared geometry timing for native clipped folder/icon motion. No delayed second animation. */
public final class HeckerLauncherMotion {
    public static final Interpolator CURVE = FlowClockRenderer::settle;
    public static boolean enabled(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return ValueAnimator.areAnimatorsEnabled() && (power == null || !power.isPowerSaveMode())
                && Settings.Secure.getInt(context.getContentResolver(), "hecker_living_flow", 1) != 0;
    }
    private HeckerLauncherMotion() {}
}

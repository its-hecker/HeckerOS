// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.wm.shell.transition;

import android.app.WindowConfiguration;
import android.content.Context;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.window.TransitionInfo;

/** Default fullscreen task motion. Native Shell keeps surfaces, cancellation and completion. */
public final class HeckerFlowWindowAnimation {
    private static final Interpolator SETTLE = t ->
            (float) ((1 - (1 + 7 * t) * Math.exp(-7 * t)) / (1 - 8 * Math.exp(-7)));
    private HeckerFlowWindowAnimation() {}
    private static boolean eligibleTask(int type, TransitionInfo.Change change) {
        if (type != WindowManager.TRANSIT_OPEN && type != WindowManager.TRANSIT_CLOSE
                && type != WindowManager.TRANSIT_TO_FRONT && type != WindowManager.TRANSIT_TO_BACK) return false;
        var task = change.getTaskInfo();
        if (task == null || task.getWindowingMode() != WindowConfiguration.WINDOWING_MODE_FULLSCREEN) return false;
        int activity = task.getActivityType();
        return activity == WindowConfiguration.ACTIVITY_TYPE_STANDARD || activity == WindowConfiguration.ACTIVITY_TYPE_HOME;
    }
    private static boolean enabled(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return (power == null || !power.isPowerSaveMode()) && Settings.Secure.getIntForUser(
                context.getContentResolver(), "hecker_living_flow", 1, UserHandle.USER_CURRENT) != 0;
    }
    /** Keep native icon-origin clip/scale geometry and each child's duration, changing only settling. */
    public static void tuneLaunch(Context context, int type, TransitionInfo.Change change, Animation animation) {
        if (!eligibleTask(type, change) || !enabled(context)) return;
        tune(animation);
    }
    private static void tune(Animation animation) {
        animation.setInterpolator(SETTLE);
        if (animation instanceof AnimationSet) for (Animation child : ((AnimationSet) animation).getAnimations()) tune(child);
    }
    public static Animation create(Context context, int type, TransitionInfo.Change change,
            boolean enter, boolean openingType) {
        if (!eligibleTask(type, change) || !enabled(context)) return null;
        boolean home = change.getTaskInfo().getActivityType() == WindowConfiguration.ACTIVITY_TYPE_HOME;
        long duration = enter ? 460 : 330;
        float from = enter ? (home ? 1.025f : .92f) : 1f;
        float to = enter ? 1f : (openingType ? .975f : .91f);
        AnimationSet result = new AnimationSet(false);
        ScaleAnimation scale = new ScaleAnimation(from, to, from, to,
                Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        scale.setDuration(duration); scale.setInterpolator(SETTLE); result.addAnimation(scale);
        if (!home) {
            TranslateAnimation move = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0,
                    Animation.RELATIVE_TO_SELF, 0, Animation.RELATIVE_TO_SELF, enter ? .035f : 0,
                    Animation.RELATIVE_TO_SELF, enter ? 0 : .018f);
            move.setDuration(duration); move.setInterpolator(SETTLE); result.addAnimation(move);
        }
        AlphaAnimation alpha = new AlphaAnimation(enter && !home ? 0 : 1,
                !enter && !openingType && !home ? 0 : 1);
        alpha.setDuration(enter ? 160 : duration); alpha.setInterpolator(SETTLE); result.addAnimation(alpha);
        result.setFillAfter(true);
        return result;
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import com.airbnb.lottie.LottieAnimationView;
import com.android.launcher3.R;

/** Stops when detached or hidden, and honors Android's disabled-animation preference. */
public final class HeckerLottieView extends LottieAnimationView {
    public HeckerLottieView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAnimation(R.raw.hecker_orbit);
        setRepeatCount(ValueAnimator.INFINITE);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateMotion(); }
    @Override protected void onDetachedFromWindow() { cancelAnimation(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View view, int visibility) {
        super.onVisibilityChanged(view, visibility);
        updateMotion();
    }
    private void updateMotion() {
        if (isAttachedToWindow() && isShown() && ValueAnimator.areAnimatorsEnabled()
                && android.provider.Settings.Secure.getInt(getContext().getContentResolver(), "hecker_lottie_enabled", 1) == 1) playAnimation();
        else { cancelAnimation(); setProgress(0.5f); }
    }
}

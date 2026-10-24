// SPDX-License-Identifier: Apache-2.0
package org.hecker.suite;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.airbnb.lottie.LottieAnimationView;

/** Small, original Lottie assets; system authentication is never driven by animation state. */
final class SuiteAnimation {
    private final AutoAnimationView view;

    SuiteAnimation(Activity activity, LinearLayout parent) { this(activity, parent, 156); }

    SuiteAnimation(Activity activity, LinearLayout parent, int size) {
        view = new AutoAnimationView(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                size == 156 ? ViewGroup.LayoutParams.MATCH_PARENT : Ui.dp(activity, size), Ui.dp(activity, size));
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
        view.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(view);
        show(R.raw.suite_scan, true);
    }

    void show(int animation, boolean repeat) {
        view.cancelAnimation();
        view.setAnimation(animation);
        view.setRepeatCount(repeat ? ValueAnimator.INFINITE : 0);
        view.active = true;
        view.resumeIfVisible();
    }

    void stop() { view.active = false; view.cancelAnimation(); }

    private static final class AutoAnimationView extends LottieAnimationView {
        boolean active;
        AutoAnimationView(Activity activity) { super(activity); }
        void resumeIfVisible() {
            if (!active) return;
            if (!ValueAnimator.areAnimatorsEnabled()) { setProgress(0.5f); return; }
            if (isAttachedToWindow() && getWindowVisibility() == VISIBLE && isShown()) resumeAnimation();
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); resumeIfVisible(); }
        @Override protected void onDetachedFromWindow() { pauseAnimation(); super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) resumeIfVisible(); else pauseAnimation();
        }
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.android.settings.R;

/** Hecker caracal emblem. Animation exists only while this view is visible. */
public final class HeckerPhoneArtView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mOrbit = new RectF(10, 10, 150, 150);
    private final Drawable mMark;
    private final int mAccent;
    private ValueAnimator mAnimator;
    private boolean mActive;
    private float mPhase;

    public HeckerPhoneArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAccent = context.getColor(R.color.hecker_accent);
        mGlow.setShader(new RadialGradient(80, 80, 76,
                new int[] {withAlpha(mAccent, 36), withAlpha(mAccent, 10), Color.TRANSPARENT},
                new float[] {0, 0.7f, 1}, Shader.TileMode.CLAMP));
        mMark = context.getDrawable(R.drawable.hecker_caracal_mark).mutate();
        mMark.setBounds(22, 20, 138, 136);
    }

    public void setActive(boolean active) {
        mActive = active;
        updateAnimation();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }

    private void updateAnimation() {
        if (!mActive || !isAttachedToWindow() || !isShown()
                || getWindowVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            stopAnimation();
            return;
        }
        if (mAnimator != null) return;
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(3200);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mPhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        mAnimator.start();
    }

    private void stopAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
            mAnimator = null;
        }
        mPhase = 0f;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        float scale = Math.min(getWidth() / 160f, getHeight() / 160f);
        canvas.translate((getWidth() - 160f * scale) / 2f,
                (getHeight() - 160f * scale) / 2f);
        canvas.scale(scale, scale);
        canvas.drawCircle(80, 80, 76, mGlow);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(1);
        mPaint.setColor(withAlpha(mAccent, 36));
        canvas.drawCircle(80, 80, 70, mPaint);
        mPaint.setStrokeWidth(2);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(withAlpha(mAccent, 130));
        canvas.drawArc(mOrbit, -72 + 12 * mPhase, 42, false, mPaint);
        canvas.drawArc(mOrbit, 108 + 12 * mPhase, 42, false, mPaint);
        canvas.translate(0, -2f * mPhase);
        mMark.draw(canvas);
        canvas.restoreToCount(save);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }
}

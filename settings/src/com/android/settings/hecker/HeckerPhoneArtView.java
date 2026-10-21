// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.android.settings.R;

/** Small native phone illustration. Animation exists only while this view is visible. */
public final class HeckerPhoneArtView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mShape = new RectF();
    private ValueAnimator mAnimator;
    private boolean mActive;
    private float mPhase;

    public HeckerPhoneArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
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
        float scale = Math.min(getWidth() / 96f, getHeight() / 152f);
        canvas.translate((getWidth() - 96f * scale) / 2f,
                (getHeight() - 152f * scale) / 2f);
        canvas.scale(scale, scale);
        canvas.translate(0, -3f * mPhase);
        int accent = getContext().getColor(R.color.hecker_accent);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(getContext().getColor(R.color.hecker_icon_background));
        canvas.drawCircle(48, 78, 43, mPaint);
        mPaint.setColor(getContext().getColor(R.color.hecker_border));
        mShape.set(18, 8, 81, 143);
        canvas.drawRoundRect(mShape, 12, 12, mPaint);
        mPaint.setColor(getContext().getColor(R.color.hecker_card));
        mShape.set(21, 11, 78, 140);
        canvas.drawRoundRect(mShape, 10, 10, mPaint);
        mPaint.setColor(getContext().getColor(R.color.hecker_background));
        mShape.set(25, 24, 74, 127);
        canvas.drawRoundRect(mShape, 4, 4, mPaint);
        mPaint.setColor(accent);
        mShape.set(39, 17, 60, 19);
        canvas.drawRoundRect(mShape, 1, 1, mPaint);
        canvas.drawCircle(30, 18, 1.5f, mPaint);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2);
        canvas.drawCircle(49.5f, 72, 17 + 2 * mPhase, mPaint);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        mPaint.setTextSize(18);
        canvas.drawText("H", 49.5f, 78, mPaint);
        mShape.set(39, 133, 60, 135);
        canvas.drawRoundRect(mShape, 1, 1, mPaint);
        canvas.restoreToCount(save);
    }
}

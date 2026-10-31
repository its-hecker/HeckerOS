// SPDX-License-Identifier: Apache-2.0
package com.android.settings.hecker;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import org.hecker.motion.MediaGlowSpec;

/** The media player's drifting under-light, clipped behind the About card's content. */
public final class HeckerAboutGlowLayout extends FrameLayout {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mMatrix = new Matrix();
    private final RadialGradient[] mGlow = new RadialGradient[MediaGlowSpec.COUNT];
    private final ContentObserver mSettings = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { updateAnimation(); }
    };
    private final BroadcastReceiver mPower = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateAnimation(); }
    };
    private Shader mUnderlay, mRim;
    private ValueAnimator mAnimator;
    private boolean mActive, mObserving;
    private float mPhase;

    public HeckerAboutGlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClipToOutline(true);
        for (int i = 0; i < mGlow.length; i++) {
            mGlow[i] = new RadialGradient(0, 0, 1,
                    new int[] {MediaGlowSpec.centerColor(i), MediaGlowSpec.middleColor(i), Color.TRANSPARENT},
                    new float[] {0, .5f, 1}, Shader.TileMode.CLAMP);
        }
    }

    public void setActive(boolean active) { mActive = active; updateAnimation(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, mSettings);
        getContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor("hecker_living_flow"), false, mSettings);
        getContext().registerReceiver(mPower, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED);
        mObserving = true;
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        if (mObserving) {
            getContext().getContentResolver().unregisterContentObserver(mSettings);
            getContext().unregisterReceiver(mPower);
            mObserving = false;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View view, int visibility) {
        super.onVisibilityChanged(view, visibility);
        updateAnimation();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        updateAnimation();
    }

    private void updateAnimation() {
        PowerManager power = getContext().getSystemService(PowerManager.class);
        boolean allowed = mActive && isAttachedToWindow() && isShown() && hasWindowFocus()
                && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled()
                && (power == null || !power.isPowerSaveMode())
                && Settings.Secure.getInt(getContext().getContentResolver(), "hecker_living_flow", 1) != 0;
        if (!allowed) { stopAnimation(); return; }
        if (mAnimator != null) return;
        mPhase = MediaGlowSpec.phase(SystemClock.elapsedRealtime());
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(MediaGlowSpec.PERIOD_MILLIS);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.addUpdateListener(animation -> {
            mPhase = MediaGlowSpec.phase(SystemClock.elapsedRealtime());
            invalidate();
        });
        mAnimator.start();
    }

    private void stopAnimation() {
        if (mAnimator == null) return;
        mAnimator.cancel();
        mAnimator.removeAllUpdateListeners();
        mAnimator = null;
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (height <= 0) return;
        mUnderlay = new LinearGradient(0, 0, 0, height, Color.TRANSPARENT,
                MediaGlowSpec.UNDERLAY, Shader.TileMode.CLAMP);
        mRim = new LinearGradient(0, 0, 0, height, Color.TRANSPARENT,
                MediaGlowSpec.RIM, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setShader(mUnderlay);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
        for (int i = 0; i < mGlow.length; i++) {
            float radius = Math.max(1, getWidth() * MediaGlowSpec.radius(i, mPhase));
            mMatrix.setScale(radius, radius);
            mMatrix.postTranslate(getWidth() * MediaGlowSpec.centerX(i, mPhase),
                    getHeight() * MediaGlowSpec.centerY(i, mPhase));
            mGlow[i].setLocalMatrix(mMatrix);
            mPaint.setShader(mGlow[i]);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
        }
        mPaint.setShader(mRim);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
        mPaint.setShader(null);
    }
}

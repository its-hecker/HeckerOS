// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import com.android.systemui.res.R;

/** Decorative window: rope and astronaut never receive touches. No idle animation loop. */
final class MotionAstronautView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap atlas;
    private final float density;
    private long when = -1, sequence = -1;
    private int direction, action;
    private boolean animate;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            invalidate();
            if (when >= 0 && SystemClock.elapsedRealtime() - when < MotionIslandState.ACTION_DURATION)
                postDelayed(this, 33);
        }
    };
    MotionAstronautView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        atlas = BitmapFactory.decodeResource(getResources(), R.drawable.hecker_motion_astronaut, options);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private float dp(float n) { return n * density; }
    void update(MotionIslandState state, boolean animate) {
        direction = state.direction; action = state.action;
        if (sequence != state.sequence || this.animate != animate) {
            stop(); sequence = state.sequence; when = state.when;
            if (animate) post(frame);
        }
        this.animate = animate;
        invalidate();
    }
    void stop() { removeCallbacks(frame); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long age = SystemClock.elapsedRealtime() - when;
        if (when < 0 || age < 0 || age >= MotionIslandState.ACTION_DURATION || atlas == null) return;
        float alpha = animate ? MotionIslandAnimation.alpha(age) : 1;
        int pose = animate ? MotionIslandAnimation.pose(age,
                action == MotionIslandState.PLAY || action == MotionIslandState.PAUSE) : 0;
        int save = canvas.save();
        canvas.translate(getWidth() / 2f, dp(1));
        if (animate) {
            canvas.rotate(MotionIslandAnimation.angle(age, direction));
            canvas.translate(0, -dp(13) * (1 - alpha));
        }
        float rope = dp(47);
        paint.setColor(0xffe7edf2); paint.setAlpha(Math.round(235 * alpha));
        paint.setStrokeWidth(dp(.8f)); paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(0, 0, 0, rope + dp(2), paint);
        // Each source cell is selected directly from the transparent atlas. The raised gripping
        // hand is registered to the rope endpoint across poses, independent of the free waving arm.
        int cell = atlas.getWidth() / 3;
        float size = dp(105);
        float[] gripX = {.762f, .697f, .612f};
        float gripY = .292f;
        Rect source = new Rect(pose * cell, 0, (pose + 1) * cell, atlas.getHeight());
        RectF target = new RectF(-size * gripX[pose], rope - size * gripY,
                size * (1 - gripX[pose]), rope + size * (1 - gripY));
        paint.setAlpha(Math.round(255 * alpha));
        canvas.drawBitmap(atlas, source, target, paint);
        canvas.restoreToCount(save);
    }
}

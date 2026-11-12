// SPDX-License-Identifier: Apache-2.0
package com.android.systemui.hecker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** Bounded, tappable music capsule. Its bars animate only during visible playback. */
final class MotionIslandView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path artworkClip = new Path();
    private final float density;
    private Bitmap artwork;
    private String label = "";
    private String clippedLabel = "";
    private int clippedWidth = -1;
    private boolean moving, playing, animateRequested;
    private final Runnable frame = new Runnable() {
        @Override public void run() { invalidate(); if (moving) postDelayed(this, 33); }
    };
    MotionIslandView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        text.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setClickable(true); setFocusable(true);
    }
    int dp(float value) { return Math.round(value * density); }
    void update(HeckerQsMedia.Snapshot media, String label, boolean animate) {
        this.artwork = media.artwork; this.playing = media.playing;
        if (!this.label.equals(label)) { this.label = label; clippedWidth = -1; }
        animateRequested = animate;
        setContentDescription(label + ". " + getContext().getString(com.android.systemui.res.R.string.hecker_motion_open_player));
        updateMotion();
        invalidate();
    }
    private void updateMotion() {
        boolean next = animateRequested && playing && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && isShown();
        if (next == moving) return;
        stop();
        moving = next;
        if (moving) post(frame);
    }
    void stop() { moving = false; removeCallbacks(frame); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateMotion(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View view, int visibility) {
        super.onVisibilityChanged(view, visibility); updateMotion();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility); updateMotion();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float top = dp(3), bottom = getHeight() - dp(3), radius = (bottom - top) / 2;
        rect.set(dp(2), top, getWidth() - dp(2), bottom);
        paint.setColor(0xff05080c); canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setColor(0xff17252d); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(.6f));
        canvas.drawRoundRect(rect, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
        rect.set(dp(7), top + dp(3), dp(27), bottom - dp(3));
        if (artwork != null && !artwork.isRecycled()) {
            int save = canvas.save();
            artworkClip.rewind();
            artworkClip.addRoundRect(rect, dp(7), dp(7), Path.Direction.CW);
            canvas.clipPath(artworkClip);
            int side = Math.min(artwork.getWidth(), artwork.getHeight());
            int x = (artwork.getWidth() - side) / 2, y = (artwork.getHeight() - side) / 2;
            canvas.drawBitmap(artwork, new Rect(x, y, x + side, y + side), rect, paint);
            canvas.restoreToCount(save);
        } else {
            paint.setColor(0xff163d50); canvas.drawRoundRect(rect, dp(7), dp(7), paint);
            text.setColor(0xffa8e6ff); canvas.drawText("♪", dp(12), bottom - dp(7), text);
        }
        text.setColor(0xfff0f7fa);
        int width = Math.max(dp(8), getWidth() - dp(72));
        if (width != clippedWidth) {
            clippedWidth = width;
            clippedLabel = TextUtils.ellipsize(label, text, width, TextUtils.TruncateAt.END).toString();
        }
        Paint.FontMetrics metrics = text.getFontMetrics();
        canvas.drawText(clippedLabel, dp(33), getHeight() / 2f - (metrics.ascent + metrics.descent) / 2, text);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(dp(1.4f));
        long now = SystemClock.elapsedRealtime();
        for (int i = 0; i < 5; i++) {
            float height = dp(moving ? MotionIslandAnimation.bar(now, i) : playing ? 5 + i % 3 * 2 : 3);
            float x = getWidth() - dp(24) + dp(i * 3f), center = getHeight() / 2f;
            paint.setColor(i % 2 == 0 ? 0xff70d0fb : 0xff439fdb);
            canvas.drawLine(x, center - height / 2, x, center + height / 2, paint);
        }
    }
}

// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package com.android.launcher3.hecker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small original vector illustrations, animated only while visible and when motion is enabled. */
public final class HeckerCategoryArt extends View {
    private final String kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;
    public HeckerCategoryArt(Context c, String kind) { super(c); this.kind = kind; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); motion(); }
    @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); motion(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); motion(); }
    @Override protected void onDetachedFromWindow() { if (animator != null) { animator.cancel(); animator = null; } super.onDetachedFromWindow(); }
    private void motion() {
        if (kind == null) return;
        boolean run = isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled();
        if (!run) { if (animator != null) { animator.cancel(); animator = null; } return; }
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(5200); animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); }); animator.start();
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); int save = c.save(); c.scale(getWidth() / 100f, getHeight() / 100f);
        float wave = (float) Math.sin(phase * Math.PI * 2);
        if ("home".equals(kind)) {
            for (int i = 0; i < 9; i++) {
                float ease = Math.max(0, wave), x = 10 + i % 3 * 29 + (i % 2 == 0 ? 1 : -1) * ease * 6, y = 10 + i / 3 * 29 + ease * (i % 3 - 1) * 5;
                paint.setColor(i % 2 == 0 ? 0xff9fe1d6 : 0xffbba9ee); c.drawRoundRect(x, y, x + 21, y + 21, 6, 6, paint);
            }
        } else if ("privacy".equals(kind)) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(6); paint.setColor(0xffa8dfd5);
            c.drawRoundRect(31, 12 + wave * 4, 69, 63, 18, 18, paint); paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(20, 40, 80, 90, 0xffa6d7e2, 0xffa493db, Shader.TileMode.CLAMP));
            c.drawRoundRect(20, 43, 80, 91, 12, 12, paint); paint.setShader(null); paint.setColor(0xff182c35);
            c.drawCircle(50, 63, 6, paint); c.drawRoundRect(47, 65, 53, 79, 3, 3, paint);
        } else if ("clock".equals(kind)) {
            HeckerClockView.drawCaracal(c, 5, 5, 90, phase, animator != null);
        } else {
            paint.setColor(0x506dbac3); c.drawRoundRect(10, 10, 74, 76, 16, 16, paint);
            paint.setShader(new LinearGradient(28, 28, 94, 94, 0xbbaa9fea, 0x60b4f4ec, Shader.TileMode.CLAMP));
            c.drawRoundRect(24, 25, 93, 94, 14 + wave * 5, 14 + wave * 5, paint); paint.setShader(null);
            paint.setColor(0xccebf6ef); c.drawRoundRect(39, 43, 57, 61, 4 + wave * 3, 4 + wave * 3, paint);
            paint.setColor(0xffbca4ea); c.drawRoundRect(65, 43, 83, 61, 7 - wave * 3, 7 - wave * 3, paint);
            paint.setColor(0x80ffffff); c.drawRoundRect(39, 73, 80, 77, 2, 2, paint);
        }
        c.restoreToCount(save);
    }
}

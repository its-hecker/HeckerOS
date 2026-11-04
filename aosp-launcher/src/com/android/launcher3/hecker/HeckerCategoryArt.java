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
            drawCaracal(c, 5, 5, 90, phase, animator != null);
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
    public static void drawCaracal(Canvas c, float x, float y, float size, float phase, boolean animate) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xfff0f1e9); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeJoin(Paint.Join.ROUND);
        int save = c.save(); c.translate(x, y); c.scale(size / 100, size / 100);
        float wave = animate ? (float) Math.sin(phase * Math.PI * 2) : 0; c.rotate(wave * 3, 50, 65);
        Path head = new Path(); head.moveTo(16, 54); head.cubicTo(8, 70, 17, 87, 50, 98); head.cubicTo(83, 87, 92, 70, 84, 54); head.cubicTo(73, 40, 27, 40, 16, 54); c.drawPath(head, p);
        for (int side = 0; side < 2; side++) { int ear = c.save(); if (side == 1) c.scale(-1, 1, 50, 50); c.rotate(wave * (side == 0 ? 2 : -3), 21, 55);
            Path shape = new Path(); shape.moveTo(17, 57); shape.lineTo(6, 8); shape.quadTo(26, 18, 39, 43); c.drawPath(shape, p);
            p.setColor(0xff687780); Path inside = new Path(); inside.moveTo(20, 48); inside.lineTo(13, 22); inside.lineTo(29, 43); c.drawPath(inside, p); p.setColor(0xfff0f1e9);
            c.drawLine(6, 8, 3, 0, p); c.drawLine(6, 8, 7, -3, p); c.drawLine(9, 12, 11, -1, p); c.restoreToCount(ear); }
        p.setStyle(Paint.Style.FILL); p.setColor(0x995e6b73); c.drawRoundRect(17, 56, 44, 73, 6, 6, p); c.drawRoundRect(56, 56, 83, 73, 6, 6, p);
        p.setColor(0xfff0f1e9); p.setStyle(Paint.Style.STROKE); c.drawRoundRect(17, 56, 44, 73, 6, 6, p); c.drawRoundRect(56, 56, 83, 73, 6, 6, p); c.drawLine(44, 60, 56, 60, p);
        Path nose = new Path(); nose.moveTo(46, 79); nose.quadTo(50, 75, 54, 79); nose.lineTo(50, 83); nose.close(); c.drawPath(nose, p); c.drawArc(41, 78, 50, 89, 0, 180, false, p); c.drawArc(50, 78, 59, 89, 0, 180, false, p);
        c.restoreToCount(save);
    }
}

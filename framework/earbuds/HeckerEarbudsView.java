// SPDX-License-Identifier: Apache-2.0
package org.hecker.earbuds;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.PathInterpolator;
import java.util.Locale;

/** Original vector scene: opening lid, lifting earbuds and a soft landing. */
public final class HeckerEarbudsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator animator;
    private float progress;
    private boolean light, soundcore;

    public HeckerEarbudsView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setDeviceName(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
        light = value.contains("airpods");
        soundcore = value.contains("soundcore") || value.contains("r50i") || value.contains("p20i");
        invalidate();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); play(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) play(); else stop();
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    private void stop() {
        if (animator != null) { animator.cancel(); animator.removeAllUpdateListeners(); animator = null; }
    }
    private void play() {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) { progress = 1; invalidate(); return; }
        progress = 0;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setStartDelay(120); animator.setDuration(2100);
        animator.setInterpolator(new PathInterpolator(.22f, .75f, .24f, 1));
        animator.addUpdateListener(value -> { progress = (float) value.getAnimatedValue(); invalidate(); });
        animator.start();
    }
    private static float phase(float value, float start, float end) {
        float x = Math.max(0, Math.min(1, (value - start) / (end - start)));
        return x * x * (3 - 2 * x);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 360f, getHeight() / 235f);
        if (scale <= 0) return;
        canvas.save(); canvas.translate((getWidth() - 360 * scale) / 2, (getHeight() - 235 * scale) / 2);
        canvas.scale(scale, scale);
        float lid = phase(progress, 0, .58f), lift = phase(progress, .32f, 1);
        paint.setShader(new RadialGradient(180, 125, 145, new int[]{0x353EBEFF, 0x143D82FF, 0}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(180, 125, 145, paint);
        paint.setShader(new RadialGradient(180, 207, 112, new int[]{0x65000000, 0}, null, Shader.TileMode.CLAMP));
        canvas.save(); canvas.scale(1, .22f, 180, 207); canvas.drawCircle(180, 207, 112, paint); canvas.restore();
        fill(light ? 0xFF73818F : 0xFF07101A); round(canvas, 85, 129, 275, 169, 28);
        gradient(129, 169, light ? 0xFFE3E9ED : 0xFF243A4C, light ? 0xFFB4C0C9 : 0xFF071320);
        canvas.drawOval(91, 131, 269, 167, paint);
        fill(0xFF050D16); canvas.drawOval(122, 137, 164, 158, paint); canvas.drawOval(196, 137, 238, 158, paint);
        float top = 113 - 65 * lid, bottom = 151 - 57 * lid;
        gradient(top, bottom, light ? 0xFFFFFFFF : 0xFF516A80, light ? 0xFFCAD6DF : 0xFF162A3D);
        round(canvas, 84, top, 276, bottom, 30);
        if (lid > .02f) {
            fill(light ? 0xFFE9EFF3 : 0xFF102335); paint.setAlpha((int) (255 * lid));
            round(canvas, 94, top + 7, 266, bottom - 7, 23); paint.setAlpha(255);
            fill(light ? 0xFFB1C0CB : 0xFF28455D);
            canvas.drawOval(124, top + 13, 162, bottom - 8, paint); canvas.drawOval(198, top + 13, 236, bottom - 8, paint);
        }
        float bounce = (float) Math.sin(lift * Math.PI) * 6;
        canvas.saveLayerAlpha(0, 0, 360, 235, (int) (255 * phase(lid, .18f, .5f)));
        earbud(canvas, 143 - 19 * lift, 146 - 61 * lift - bounce, -18 * lift, true);
        earbud(canvas, 217 + 19 * lift, 146 - 61 * lift - bounce, 18 * lift, false); canvas.restore();
        gradient(154, 204, light ? 0xFFFFFFFF : 0xFF3A5368, light ? 0xFFC5D2DD : 0xFF122436);
        round(canvas, 85, 151, 275, 205, 27);
        fill(light ? 0x99FFFFFF : 0x666F9AB7); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
        round(canvas, 86, 152, 274, 204, 26); paint.setStyle(Paint.Style.FILL);
        fill(0xFF8DEFD9); canvas.drawCircle(180, 188, 2, paint);
        fill(light ? 0xFF536575 : 0xFFA7BDCE); paint.setTypeface(Typeface.create("sans-serif-medium", 0));
        paint.setTextSize(11); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(soundcore ? "soundcore" : light ? "AirPods" : "hecker", 180, 176, paint);
        canvas.restore();
    }
    private void earbud(Canvas c, float x, float y, float angle, boolean left) {
        c.save(); c.translate(x, y); c.rotate(angle);
        fill(0x33000000); c.drawOval(-19, 13, 19, 27, paint);
        gradient(-20, 20, light ? 0xFFFFFFFF : 0xFF60788C, light ? 0xFFD3DFE7 : 0xFF152C40);
        c.drawOval(-17, -19, 17, 20, paint);
        if (light) round(c, left ? -4 : -10, 5, left ? 10 : 4, 37, 6);
        fill(light ? 0xFF233644 : 0xFF0B1B28); c.drawOval(left ? -16 : 4, -7, left ? -4 : 16, 8, paint);
        fill(light ? 0xFFC3D0D9 : 0xFF7896AC); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
        c.drawOval(-13, -15, 13, 14, paint); paint.setStyle(Paint.Style.FILL);
        fill(light ? 0xFF718490 : 0xFFC0D6E5); paint.setTextSize(7); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(left ? "L" : "R", 0, 3, paint); c.restore();
    }
    private void fill(int color) { paint.setShader(null); paint.setColor(color); }
    private void gradient(float y1, float y2, int first, int last) {
        paint.setColor(Color.WHITE); paint.setShader(new LinearGradient(0, y1, 0, y2, first, last, Shader.TileMode.CLAMP));
    }
    private void round(Canvas c, float x1, float y1, float x2, float y2, float radius) {
        rect.set(x1, y1, x2, y2); c.drawRoundRect(rect, radius, radius, paint);
    }
}

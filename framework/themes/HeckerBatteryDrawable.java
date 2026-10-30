/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.os.SystemClock;
import android.util.PathParser;

/** Renders the Lunaris battery path resources without assuming Android 16 target IDs. */
public final class HeckerBatteryDrawable extends Drawable implements Animatable, Runnable {
    private static final long FRAME_DELAY_MS = 33;
    private static final float WAVE_PERIOD_MS = 2400f;
    private final Resources res;
    private final String pkg;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shimmerMatrix = new Matrix();
    private final Path perimeter, mask, bolt, saver;
    private final Path fillPath = new Path();
    private final RectF fillBounds = new RectF();
    private final int width, height;
    private final boolean horizontal, oneUi, livingFlow;
    private final boolean levelColors;
    private final boolean chargingAnimation, externalBolt;
    private final Path chargingBolt;
    private int level = 100, tint = Color.WHITE;
    private boolean charging, powerSave, percent;
    private boolean running;
    private float wavePhase;
    private long lastFrameTime;

    private HeckerBatteryDrawable(Context context, Resources res, String pkg) {
        oneUi = "com.android.systemui.battery.oneui7".equals(pkg);
        livingFlow = "org.hecker.theme.battery.livingflow".equals(pkg);
        levelColors = android.provider.Settings.Secure.getIntForUser(context.getContentResolver(), "hecker_battery_colors", 1, android.os.UserHandle.USER_CURRENT) != 0;
        chargingAnimation = setting(context, "hecker_battery_charging_animation", 1) != 0;
        externalBolt = oneUi && setting(context, "hecker_battery_bolt", 1) != 0;
        int boltStyle = Math.max(0, Math.min(2, setting(context, "hecker_battery_bolt_style", 0)));
        chargingBolt = PathParser.createPathFromPathData(new String[]{
                "M6,0 L0,11 L4,11 L2,20 L10,7 L6,7 Z",
                "M6,0 L0,9 L4,9 L5,6 L10,6 Z M5,10 L0,14 L4,14 L2,20 L10,10 Z",
                "M7,0 L1,10 L5,10 L3,20 L10,7 L6,7 Z M1,1 L0,4 L2,3 Z M9,16 L8,19 L10,18 Z"
        }[boltStyle]);
        this.res = res; this.pkg = pkg;
        width = integer("config_batterymeterWidth", 12); height = integer("config_batterymeterHeight", 20);
        if (width < 1 || width > 128 || height < 1 || height > 128) throw new IllegalArgumentException();
        perimeter = path("config_batterymeterPerimeterPath"); mask = path("config_batterymeterFillMask");
        bolt = path("config_batterymeterBoltPath"); saver = path("config_batterymeterPowersavePath");
        // Preserve each theme's original silhouette, including One UI's paths.
        horizontal = bool("config_batterymeterHorizontalFill", width > height);
        shimmerPaint.setShader(new LinearGradient(-width, 0, 0, 0,
                new int[]{0x00ffffff, 0x60ffffff, 0x00ffffff}, null, Shader.TileMode.CLAMP));
    }
    private static int setting(Context context, String key, int fallback) {
        return android.provider.Settings.Secure.getIntForUser(context.getContentResolver(), key,
                fallback, android.os.UserHandle.USER_CURRENT);
    }
    public static HeckerBatteryDrawable create(Context context) {
        try {
            String pkg = HeckerThemeAssets.packageName(context, "battery_style");
            Resources res = HeckerThemeAssets.resources(context, "battery_style");
            return pkg == null || res == null ? null : new HeckerBatteryDrawable(context, res, pkg);
        } catch (Exception malformed) { return null; }
    }
    private int id(String name, String type) { return res.getIdentifier(name, type, pkg); }
    private int integer(String name, int fallback) { int id = id(name, "integer"); return id == 0 ? fallback : res.getInteger(id); }
    private boolean bool(String name, boolean fallback) { int id = id(name, "bool"); return id == 0 ? fallback : res.getBoolean(id); }
    private int color(String name, int fallback) { int id = id(name, "color"); return id == 0 ? fallback : res.getColor(id, null); }
    private Path path(String name) { int id = id(name, "string"); return id == 0 ? new Path() : PathParser.createPathFromPathData(res.getString(id)); }
    public void update(int level, boolean charging, boolean powerSave, boolean percent, int tint) {
        this.level = Math.max(0, Math.min(100, level)); this.charging = charging; this.powerSave = powerSave;
        this.percent = percent; this.tint = tint; invalidateSelf();
        if (!canAnimate()) stop();
    }
    private boolean canAnimate() {
        return (oneUi || livingFlow) && chargingAnimation && charging && !powerSave && level > 0
                && ValueAnimator.areAnimatorsEnabled();
    }
    @Override public void start() {
        if (!isVisible() || getCallback() == null || !canAnimate()) { stop(); return; }
        if (running) return;
        running = true;
        lastFrameTime = SystemClock.uptimeMillis();
        invalidateSelf();
        scheduleSelf(this, lastFrameTime + FRAME_DELAY_MS);
    }
    @Override public void stop() {
        unscheduleSelf(this);
        if (!running) return;
        running = false;
        wavePhase = 0f;
        invalidateSelf();
    }
    @Override public boolean isRunning() { return running; }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) stop();
        return changed;
    }
    @Override public void run() {
        if (!running) return;
        float durationScale = ValueAnimator.getDurationScale();
        if (!isVisible() || getCallback() == null || !canAnimate() || durationScale <= 0f) {
            stop();
            return;
        }
        long now = SystemClock.uptimeMillis();
        wavePhase = (wavePhase + Math.max(0L, now - lastFrameTime)
                / (WAVE_PERIOD_MS * durationScale)) % 1f;
        lastFrameTime = now;
        invalidateSelf();
        scheduleSelf(this, now + FRAME_DELAY_MS);
    }
    private void buildFillPath() {
        mask.computeBounds(fillBounds, true);
        fillPath.rewind();
        float axis = horizontal ? fillBounds.width() : fillBounds.height();
        float filled = axis * level / 100f;
        if (!running) {
            if (horizontal) fillPath.addRect(fillBounds.left, fillBounds.top,
                    fillBounds.left + filled, fillBounds.bottom, Path.Direction.CW);
            else fillPath.addRect(fillBounds.left, fillBounds.bottom - filled,
                    fillBounds.right, fillBounds.bottom, Path.Direction.CW);
            return;
        }
        // A full sine period has zero mean, so the moving edge remains centered
        // on the real level. Taper it near empty/full and reuse all drawing objects.
        float amplitude = Math.min(axis * .045f, Math.min(filled, axis - filled) * .35f);
        if (horizontal) fillPath.moveTo(fillBounds.left, fillBounds.top);
        else fillPath.moveTo(fillBounds.left, fillBounds.bottom);
        for (int step = 0; step <= 24; step++) {
            float fraction = step / 24f;
            float ripple = amplitude * (float) Math.sin((fraction - wavePhase) * Math.PI * 2);
            if (horizontal) fillPath.lineTo(fillBounds.left + filled + ripple,
                    fillBounds.top + fillBounds.height() * fraction);
            else fillPath.lineTo(fillBounds.left + fillBounds.width() * fraction,
                    fillBounds.bottom - filled + ripple);
        }
        if (horizontal) fillPath.lineTo(fillBounds.left, fillBounds.bottom);
        else fillPath.lineTo(fillBounds.right, fillBounds.bottom);
        fillPath.close();
    }
    public static float scale(Context context) {
        return Math.max(80, Math.min(140, android.provider.Settings.Secure.getIntForUser(
                context.getContentResolver(), "hecker_battery_scale", 100, android.os.UserHandle.USER_CURRENT))) / 100f;
    }
    @Override public int getIntrinsicWidth() { return width + (externalBolt ? Math.max(6, Math.round(height * .65f)) + 2 : 0); }
    @Override public int getIntrinsicHeight() { return height; }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / (float) getIntrinsicWidth(), getBounds().height() / (float) height);
        paint.setStyle(Paint.Style.FILL); paint.setColor(tint); paint.setAlpha(livingFlow ? 230 : 65); canvas.drawPath(perimeter, paint);
        final int fillColor = charging ? ((oneUi || livingFlow) && levelColors ? 0xff64e4bd : color("config_batterymeterChargingFillColor", tint))
                : powerSave ? 0xffffcc66 : level <= 15 ? 0xffff6175
                : (oneUi || livingFlow) && levelColors ? (level <= 35 ? 0xffffc66b : level <= 70 ? 0xff76cfff : 0xff91e3c1) : tint;
        buildFillPath();
        int fill = canvas.save(); canvas.clipPath(mask); canvas.clipPath(fillPath);
        paint.setAlpha(255);
        paint.setColor(fillColor);
        canvas.drawPath(mask, paint);
        if (running) {
            shimmerMatrix.setTranslate(width * 2f * wavePhase, 0);
            shimmerPaint.getShader().setLocalMatrix(shimmerMatrix);
            canvas.drawPath(mask, shimmerPaint);
        }
        canvas.restoreToCount(fill);
        paint.setColor(tint); paint.setAlpha(255);
        if (!oneUi && (charging || bool("config_batterymeterGlyphFollowFrame", false)) && !(percent && bool("config_themedBatteryPercentWhileCharging", false))) canvas.drawPath(bolt, paint);
        else if (powerSave) canvas.drawPath(saver, paint);
        if (percent && (!charging || oneUi || bool("config_themedBatteryPercentWhileCharging", false))) {
            paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true); paint.setTextSize(height * .62f);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f;
            String label = Integer.toString(level);
            // Draw the unfilled part using the status-bar foreground, then the
            // filled part with contrast computed from the actual fill color.
            paint.setColor(tint);
            canvas.drawText(label, width / 2f, baseline, paint);
            int textFill = canvas.save(); canvas.clipPath(mask); canvas.clipPath(fillPath);
            paint.setColor(Color.luminance(fillColor) > .4 ? Color.BLACK : Color.WHITE);
            canvas.drawText(label, width / 2f, baseline, paint);
            canvas.restoreToCount(textFill);
            paint.setFakeBoldText(false);
        }
        if (externalBolt && charging) {
            int glyph = canvas.save();
            float glyphHeight = height * .9f;
            canvas.translate(width + 2, (height - glyphHeight) / 2f);
            canvas.scale((getIntrinsicWidth() - width - 2) / 10f, glyphHeight / 20f);
            paint.setColor(tint); paint.setAlpha(255);
            canvas.drawPath(chargingBolt, paint);
            canvas.restoreToCount(glyph);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

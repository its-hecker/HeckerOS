/* Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0 */
package com.android.systemui.hecker;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.PathParser;

/** Renders the Lunaris battery path resources without assuming Android 16 target IDs. */
public final class HeckerBatteryDrawable extends Drawable {
    private final Resources res;
    private final String pkg;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path perimeter, mask, bolt, saver;
    private final int width, height;
    private final boolean horizontal, oneUi;
    private final boolean levelColors;
    private int level = 100, tint = Color.WHITE;
    private boolean charging, powerSave, percent;

    private HeckerBatteryDrawable(Context context, Resources res, String pkg) {
        oneUi = "com.android.systemui.battery.oneui7".equals(pkg);
        levelColors = android.provider.Settings.Secure.getIntForUser(context.getContentResolver(), "hecker_battery_colors", 1, android.os.UserHandle.USER_CURRENT) != 0;
        this.res = res; this.pkg = pkg;
        width = integer("config_batterymeterWidth", 12); height = integer("config_batterymeterHeight", 20);
        if (width < 1 || width > 128 || height < 1 || height > 128) throw new IllegalArgumentException();
        perimeter = path("config_batterymeterPerimeterPath"); mask = path("config_batterymeterFillMask");
        bolt = path("config_batterymeterBoltPath"); saver = path("config_batterymeterPowersavePath");
        // Preserve each theme's original silhouette, including One UI's paths.
        horizontal = bool("config_batterymeterHorizontalFill", width > height);
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
    }
    public static float scale(Context context) {
        return Math.max(80, Math.min(140, android.provider.Settings.Secure.getIntForUser(
                context.getContentResolver(), "hecker_battery_scale", 100, android.os.UserHandle.USER_CURRENT))) / 100f;
    }
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / (float) width, getBounds().height() / (float) height);
        paint.setStyle(Paint.Style.FILL); paint.setColor(tint); paint.setAlpha(65); canvas.drawPath(perimeter, paint);
        final int fillColor = charging ? (oneUi && levelColors ? 0xff64e4bd : color("config_batterymeterChargingFillColor", tint))
                : powerSave ? 0xffffcc66 : level <= 15 ? 0xffff6175
                : oneUi && levelColors ? (level <= 35 ? 0xffffc66b : level <= 70 ? 0xff76cfff : 0xff91e3c1) : tint;
        int fill = canvas.save(); canvas.clipPath(mask);
        RectF bounds = new RectF(); mask.computeBounds(bounds, true);
        if (horizontal) canvas.clipRect(bounds.left, bounds.top, bounds.left + bounds.width() * level / 100f, bounds.bottom);
        else canvas.clipRect(bounds.left, bounds.bottom - bounds.height() * level / 100f, bounds.right, bounds.bottom);
        paint.setAlpha(255);
        paint.setColor(fillColor);
        canvas.drawPath(mask, paint); canvas.restoreToCount(fill);
        paint.setColor(tint); paint.setAlpha(255);
        if ((charging || bool("config_batterymeterGlyphFollowFrame", false)) && !(percent && bool("config_themedBatteryPercentWhileCharging", false))) canvas.drawPath(bolt, paint);
        else if (powerSave) canvas.drawPath(saver, paint);
        if (percent && (!charging || bool("config_themedBatteryPercentWhileCharging", false))) {
            paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true); paint.setTextSize(height * .62f);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f;
            String label = Integer.toString(level);
            // Draw the unfilled part using the status-bar foreground, then the
            // filled part with contrast computed from the actual fill color.
            paint.setColor(tint);
            canvas.drawText(label, width / 2f, baseline, paint);
            int textFill = canvas.save(); canvas.clipPath(mask);
            if (horizontal) canvas.clipRect(bounds.left, bounds.top, bounds.left + bounds.width() * level / 100f, bounds.bottom);
            else canvas.clipRect(bounds.left, bounds.bottom - bounds.height() * level / 100f, bounds.right, bounds.bottom);
            paint.setColor(Color.luminance(fillColor) > .4 ? Color.BLACK : Color.WHITE);
            canvas.drawText(label, width / 2f, baseline, paint);
            canvas.restoreToCount(textFill);
            paint.setFakeBoldText(false);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

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
    private final boolean horizontal;
    private int level = 100, tint = Color.WHITE;
    private boolean charging, powerSave, percent;

    private HeckerBatteryDrawable(Resources res, String pkg) {
        this.res = res; this.pkg = pkg;
        width = integer("config_batterymeterWidth", 12); height = integer("config_batterymeterHeight", 20);
        if (width < 1 || width > 128 || height < 1 || height > 128) throw new IllegalArgumentException();
        perimeter = path("config_batterymeterPerimeterPath"); mask = path("config_batterymeterFillMask");
        bolt = path("config_batterymeterBoltPath"); saver = path("config_batterymeterPowersavePath");
        horizontal = bool("config_batterymeterHorizontalFill", width > height);
    }
    public static HeckerBatteryDrawable create(Context context) {
        try {
            String pkg = HeckerThemeAssets.packageName(context, "battery_style");
            Resources res = HeckerThemeAssets.resources(context, "battery_style");
            return pkg == null || res == null ? null : new HeckerBatteryDrawable(res, pkg);
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
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }
    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / (float) width, getBounds().height() / (float) height);
        paint.setStyle(Paint.Style.FILL); paint.setColor(tint); paint.setAlpha(65); canvas.drawPath(perimeter, paint);
        int fill = canvas.save(); canvas.clipPath(mask);
        RectF bounds = new RectF(); mask.computeBounds(bounds, true);
        if (horizontal) canvas.clipRect(bounds.left, bounds.top, bounds.left + bounds.width() * level / 100f, bounds.bottom);
        else canvas.clipRect(bounds.left, bounds.bottom - bounds.height() * level / 100f, bounds.right, bounds.bottom);
        paint.setAlpha(255);
        paint.setColor(charging ? color("config_batterymeterChargingFillColor", tint) : powerSave ? color("config_batterymeterPowerSaveFillColor", 0xffffcc0a)
                : level <= integer("config_batterymeterCriticalLevel", 15) ? 0xffff453a : tint);
        canvas.drawPath(mask, paint); canvas.restoreToCount(fill);
        paint.setColor(tint); paint.setAlpha(255);
        if ((charging || bool("config_batterymeterGlyphFollowFrame", false)) && !(percent && bool("config_themedBatteryPercentWhileCharging", false))) canvas.drawPath(bolt, paint);
        else if (powerSave) canvas.drawPath(saver, paint);
        if (percent && (!charging || bool("config_themedBatteryPercentWhileCharging", false))) {
            paint.setTextAlign(Paint.Align.CENTER); paint.setFakeBoldText(true); paint.setTextSize(height * .62f);
            paint.setColor(level > 55 ? (Color.luminance(tint) > .5 ? Color.BLACK : Color.WHITE) : tint);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            canvas.drawText(Integer.toString(level), width / 2f, height / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
            paint.setFakeBoldText(false);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

// Copyright (C) 2026 HeckerOS. SPDX-License-Identifier: Apache-2.0
package org.hecker.motion;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.text.NumberFormat;
import java.util.Locale;

/** The same live glyph geometry on AOD, keyguard and Home; no image assets or touch surface. */
public final class FlowClockRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private static final int[] COLORS = {Color.WHITE, 0xffe6d5ff, 0xffffdebc, 0xffc3edeb, 0xffd2e5ff};
    private Locale lastLocale;
    private NumberFormat numbers;
    private int lastHour = -1, lastMinute = -1;
    private boolean lastTwentyFour;
    private String hourText, minuteText;
    public static float unit(float value) { return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 1; }
    private static float mix(float a, float b, float p) { return a + (b - a) * unit(p); }
    private static int mixColor(int a, int b, float p) {
        int r = Math.round(mix((a >> 16) & 255, (b >> 16) & 255, p));
        int g = Math.round(mix((a >> 8) & 255, (b >> 8) & 255, p));
        int blue = Math.round(mix(a & 255, b & 255, p));
        return 0xff000000 | (r << 16) | (g << 8) | blue;
    }
    /** Monotonic, critically damped settling; endpoints are exact and reversal is continuous. */
    public static float settle(float fraction) {
        float t = unit(fraction);
        return (float) ((1 - (1 + 7 * t) * Math.exp(-7 * t)) / (1 - 8 * Math.exp(-7)));
    }
    public static boolean supportsDepth(int style) { return style >= 2 && style <= 5; }
    public static boolean living(int style) { return style >= 3 && style <= 5; }
    public static int color(int palette) {
        return COLORS[Math.max(0, Math.min(COLORS.length - 1, palette))];
    }
    private String digits(int value, int minimum, Locale locale) {
        numbers.setMinimumIntegerDigits(minimum);
        return numbers.format(value);
    }
    private void glyph(Canvas canvas, String text, float center, float top, float height,
            float maxWidth, float stretch, int color, float opacity, Typeface face) {
        paint.reset(); paint.setAntiAlias(true); paint.setColor(color);
        paint.setAlpha(Math.round(255 * unit(opacity))); paint.setTypeface(face);
        paint.setTextSize(100); paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        paint.setTextSize(Math.max(1, 100 * height / Math.max(1, fm.descent - fm.ascent)));
        paint.setTextScaleX(Math.min(stretch, maxWidth / Math.max(1, paint.measureText(text))));
        fm = paint.getFontMetrics(); canvas.drawText(text, center, top - fm.ascent, paint);
    }
    public void draw(Canvas canvas, int hour, int minute, boolean twentyFour, String date,
            Locale locale, float width, float height, int style, float awake, boolean compact,
            boolean home, float opacity, int palette, int scalePercent, int positionX, int positionY) {
        if (width <= 0 || height <= 0) return;
        float p = unit(awake);
        if (!locale.equals(lastLocale)) {
            numbers = NumberFormat.getIntegerInstance(locale); numbers.setGroupingUsed(false);
            lastLocale = locale; lastHour = -1;
        }
        if (hour != lastHour || minute != lastMinute || twentyFour != lastTwentyFour) {
            hourText = digits(twentyFour ? hour : (hour + 11) % 12 + 1, twentyFour ? 2 : 1, locale);
            minuteText = digits(minute, 2, locale); lastHour = hour; lastMinute = minute; lastTwentyFour = twentyFour;
        }
        String h = hourText, m = minuteText;
        boolean stacked = !home && !compact && style != 4;
        float padding = Math.min(24, width * .06f);
        // All edits stay inside the native clock allocation, away from notifications/unlock targets.
        float scale = Math.max(.7f, Math.min(1.1f, scalePercent / 100f));
        float maxHeight = Math.max(1, height - 32);
        float targetHeight = Math.min(maxHeight / (stacked ? 2.05f : 1.35f), width * (stacked ? .62f : .29f)) * scale;
        targetHeight = Math.min(targetHeight, maxHeight / (stacked ? 2.05f : 1.35f));
        float ambientHeight = Math.min(42, maxHeight / 2);
        float glyphHeight = mix(ambientHeight, targetHeight, p);
        float ambientTop = (height - ambientHeight * 1.8f) / 2;
        float freeY = Math.max(0, height - targetHeight * (stacked ? 2.05f : 1.35f) - 24);
        float y = freeY * (.5f + Math.max(-100, Math.min(100, positionY)) / 200f);
        float targetTop = Math.max(0, Math.min(height - targetHeight * (stacked ? 2.05f : 1.35f), y));
        float center = width / 2;
        float targetCenter = stacked ? width * .27f : center;
        float half = stacked ? Math.min(width * .25f, width / 2 - padding) : width / 2 - padding;
        targetCenter = Math.max(padding + half, Math.min(width - padding - half,
                targetCenter + Math.max(-100, Math.min(100, positionX)) * width * .001f));
        float top = mix(ambientTop, targetTop, p);
        int tint = home ? color(palette) : mixColor(Color.WHITE, color(palette), p);
        float alpha = mix(.72f, unit(opacity), p);
        Typeface face = style == 5 ? bold : style == 4 ? regular : thin;
        if (stacked) {
            float x = mix(center, targetCenter, p);
            // Each group travels from its horizontal AOD position into its stacked destination.
            glyph(canvas, h, mix(center - 36, x, p), top, glyphHeight,
                    mix(54, half * 2, p), mix(1, .60f, p), tint, alpha, face);
            glyph(canvas, m, mix(center + 36, x, p), top + p * targetHeight * .98f,
                    glyphHeight, mix(54, half * 2, p), mix(1, .60f, p), tint, alpha, face);
            glyph(canvas, ":", center, top, ambientHeight, 12, 1, Color.WHITE,
                    (1 - p) * alpha, face);
        } else {
            glyph(canvas, h + ":" + m, center, top, glyphHeight, width - padding * 2,
                    style == 3 ? .64f : 1, tint, alpha, face);
        }
        // Locale-sensitive date stays live and never overlaps the large clock's second line.
        float dateTop = stacked ? Math.max(4, top + 6) : top + glyphHeight + 5;
        glyph(canvas, date, stacked ? mix(center, width * .77f, p) : center,
                mix(ambientTop + ambientHeight + 5, dateTop, p), 16,
                stacked ? mix(width - padding * 2, width * .37f, p) : width - padding * 2, 1, tint, alpha, regular);
    }
}

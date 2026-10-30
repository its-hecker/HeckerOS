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
    private Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private static final int[] COLORS = {Color.WHITE, 0xffe6d5ff, 0xffffdebc, 0xffc3edeb, 0xffd2e5ff};
    private boolean referenceMetrics;
    public void setReferenceFaces(Typeface regular, Typeface bold) {
        if (regular != null && bold != null) {
            this.thin=regular; this.regular=regular; this.bold=bold; referenceMetrics=true;
        }
    }
    /** One consistent native linear doze fraction, with zero endpoint velocity. */
    public static float wake(float linearDoze) {
        float t=1-unit(linearDoze); return t*t*(3-2*t);
    }
    public static float depthMask(float awake) {
        float t=unit(awake); return t*t*(3-2*t);
    }
    public static float homeFade(float progress) { return unit((progress - .55f) / .45f); }
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
        if (referenceMetrics) {
            // The approved host preview used these font-space proportions. Bundle that
            // open font instead of stretching different Android OEM font metrics.
            paint.setTextSize(Math.max(1,height));
            paint.setTextScaleX(Math.min(stretch,maxWidth/Math.max(1,paint.measureText(text))));
            canvas.drawText(text,center,top+height*.8f,paint); return;
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        paint.setTextSize(Math.max(1, 100 * height / Math.max(1, fm.descent - fm.ascent)));
        paint.setTextScaleX(Math.min(stretch, maxWidth / Math.max(1, paint.measureText(text))));
        fm = paint.getFontMetrics(); canvas.drawText(text, center, top - fm.ascent, paint);
    }
    public void draw(Canvas canvas, int hour, int minute, boolean twentyFour, String date,
            Locale locale, float width, float height, int style, float awake, boolean compact,
            boolean home, float opacity, int palette, int scalePercent, int positionX, int positionY) {
        draw(canvas, hour, minute, twentyFour, date, locale, width, height, style, awake,
                compact, home, opacity, palette, scalePercent, positionX, positionY, 0);
    }
    public void draw(Canvas canvas, int hour, int minute, boolean twentyFour, String date,
            Locale locale, float width, float height, int style, float awake, boolean compact,
            boolean home, float opacity, int palette, int scalePercent, int positionX, int positionY,
            float homeHandoff) {
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
            // Reuse each live digit group instead of dissolving a tall clock into an AOD-sized Home clock.
            float handoff = unit(homeHandoff / .68f);
            handoff = handoff * handoff * (3 - 2 * handoff);
            float homeHeight = Math.min(height, 160);
            float homeGlyph = Math.min((homeHeight - 32) / 1.35f, width * .29f) * scale;
            homeGlyph = Math.min(homeGlyph, (homeHeight - 32) / 1.35f);
            float homeTop = Math.max(0, homeHeight - homeGlyph * 1.35f - 24) / 2;
            float stretch = style == 3 ? .64f : 1;
            paint.reset(); paint.setTypeface(face); paint.setTextSize(homeGlyph);
            float fit = Math.min(stretch, (width - padding * 2) / Math.max(1, paint.measureText(h + ":" + m)));
            float hw = paint.measureText(h) * fit, mw = paint.measureText(m) * fit;
            float cw = paint.measureText(":") * fit, total = hw + cw + mw;
            float hx = center - total / 2 + hw / 2, mx = center + total / 2 - mw / 2;
            float movingHeight = mix(glyphHeight, homeGlyph, handoff);
            // Each group travels from its horizontal AOD position into its stacked destination.
            glyph(canvas, h, mix(mix(center - 36, x, p), hx, handoff), mix(top, homeTop, handoff), movingHeight,
                    mix(mix(54, half * 2, p), hw + 1, handoff), mix(mix(1, .60f, p), fit, handoff), tint, alpha, face);
            glyph(canvas, m, mix(mix(center + 36, x, p), mx, handoff), mix(top + p * targetHeight * .98f, homeTop, handoff),
                    movingHeight, mix(mix(54, half * 2, p), mw + 1, handoff), mix(mix(1, .60f, p), fit, handoff), tint, alpha, face);
            glyph(canvas, ":", mix(center, center - total / 2 + hw + cw / 2, handoff), mix(top, homeTop, handoff),
                    mix(ambientHeight, homeGlyph, handoff), mix(12, cw + 1, handoff), mix(1, fit, handoff), mixColor(Color.WHITE, tint, handoff),
                    Math.max(1 - p, handoff) * alpha, face);
            float dateTop = Math.max(4, top + 6);
            float dateVisibility = 1 - depthMask(handoff / .18f) + depthMask((handoff - .78f) / .22f);
            glyph(canvas, date, mix(mix(center, width * .77f, p), center, handoff),
                    mix(mix(ambientTop + ambientHeight + 5, dateTop, p), homeTop + homeGlyph + 5, handoff), 16,
                    mix(mix(width - padding * 2, width * .37f, p), width - padding * 2, handoff), 1, tint, alpha * dateVisibility, regular);
            return;
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
